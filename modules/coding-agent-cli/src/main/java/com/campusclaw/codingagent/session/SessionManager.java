/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.campusclaw.codingagent.session;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import com.campusclaw.ai.types.Message;
import com.campusclaw.codingagent.config.AppPaths;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Manages JSONL-based session persistence, aligned with campusclaw TS SessionManager.
 *
 * <p>Session files are append-only JSONL. The first line is a session header,
 * followed by entries (messages, model changes, thinking level changes, etc.).
 * Each entry has an id and parentId forming a tree structure.
 *
 * @version [br_eCampusCore 25.1.0_Next, 2026/05/06]
 * @since [br_eCampusCore 25.1.0_Next]
 */
public class SessionManager {

    private static final Logger log = LoggerFactory.getLogger(SessionManager.class);

    private final ObjectMapper mapper;

    /**
     * Typed writer for {@link Message}. {@link Message} carries
     * {@code @JsonTypeInfo(property = "role")}, but when a Message is embedded
     * inside a {@code Map<String, Object>} Jackson sees the value's static
     * type as {@code Object} and silently drops the {@code "role"} field.
     * Pre-serializing the Message through this writer and embedding the
     * resulting {@link com.fasterxml.jackson.databind.JsonNode} preserves the
     * discriminator so {@link #loadSession(Path)} can reconstruct the message
     * polymorphically.
     */
    private final ObjectWriter messageWriter;

    private Path sessionFile;
    private String sessionId;
    private String lastEntryId;
    private BufferedWriter writer;

    public SessionManager() {
        this.mapper = new ObjectMapper();
        this.mapper.setSerializationInclusion(JsonInclude.Include.NON_NULL);
        this.messageWriter = this.mapper.writerFor(Message.class);
    }

    /**
     * Creates a new session file in the sessions directory for the given cwd,
     * with a generated 8-char session ID.
     *
     * @param cwd the cwd
     */
    public void createSession(String cwd) {
        createSession(cwd, UUID.randomUUID().toString().substring(0, 8));
    }

    /**
     * Creates a new session file in the sessions directory for the given cwd
     * using a caller-supplied session ID. The JSONL filename will be
     * {@code <sessionId>.jsonl}, allowing external IDs (e.g. WS conversation_id)
     * to map 1:1 to a session file.
     *
     * @param cwd the cwd
     * @param sessionId the sessionId
     */
    public void createSession(String cwd, String sessionId) {
        this.sessionId = sessionId;
        this.lastEntryId = null;

        // Encode cwd into safe directory name: ~/file/.campusclaw/agent/sessions/--path--/
        String safePath = "--" + cwd.replaceFirst("^[/\\\\]", "").replaceAll("[/\\\\:]", "-") + "--";
        Path sessionDir = AppPaths.SESSIONS_DIR.resolve(safePath);
        try {
            Files.createDirectories(sessionDir);
        } catch (IOException e) {
            log.warn("Failed to create session directory: {}", sessionDir, e);
            return;
        }

        this.sessionFile = sessionDir.resolve(sessionId + ".jsonl");

        // Write header
        var header = new LinkedHashMap<String, Object>();
        header.put("type", "session");
        header.put("version", 3);
        header.put("id", sessionId);
        header.put("timestamp", Instant.now().toString());
        header.put("cwd", cwd);
        appendRaw(header);
    }

    /**
     * Resumes the most recent session for the given cwd.
     *
     * @param cwd the working directory whose latest session should be resumed
     * @return list of messages from the session, or empty if no session found
     */
    public List<Message> resumeLatestSession(String cwd) {
        String safePath = "--" + cwd.replaceFirst("^[/\\\\]", "").replaceAll("[/\\\\:]", "-") + "--";
        Path sessionDir = AppPaths.SESSIONS_DIR.resolve(safePath);

        if (!Files.isDirectory(sessionDir)) {
            return List.of();
        }

        try (var stream = Files.list(sessionDir)) {
            Optional<Path> latest = stream.filter(p -> p.toString().endsWith(".jsonl"))
                    .sorted(Comparator.comparingLong((Path p) -> {
                                try {
                                    return Files.getLastModifiedTime(p).toMillis();
                                } catch (IOException e) {
                                    return 0;
                                }
                            })
                            .reversed())
                    .findFirst();

            if (latest.isEmpty()) {
                return List.of();
            }

            return loadSession(latest.get());
        } catch (IOException e) {
            log.warn("Failed to list session directory: {}", sessionDir, e);
            return List.of();
        }
    }

    /**
     * Loads a session from a JSONL file, returning the messages from the current branch.
     * Also restores sessionId, sessionFile, and lastEntryId.
     *
     * @param file the file
     * @return the result
     */
    public List<Message> loadSession(Path file) {
        if (!Files.exists(file)) {
            return List.of();
        }
        List<Map<String, Object>> entries = readJsonlEntries(file);
        if (entries.isEmpty()) {
            return List.of();
        }
        var header = entries.get(0);
        if (!"session".equals(header.get("type"))) {
            return List.of();
        }
        this.sessionFile = file;
        this.sessionId = (String) header.get("id");
        this.lastEntryId = null;
        return extractMessages(entries);
    }

    private List<Map<String, Object>> readJsonlEntries(Path file) {
        List<Map<String, Object>> entries = new ArrayList<>();
        try (var reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isBlank()) {
                    continue;
                }
                try {
                    @SuppressWarnings("unchecked")
                    var entry = mapper.readValue(line, LinkedHashMap.class);
                    entries.add(entry);
                } catch (JsonProcessingException e) {
                    // Skip malformed lines — session files can be partially written on crash.
                    log.debug("skipping malformed session line in {}", file, e);
                }
            }
        } catch (IOException e) {
            log.warn("Failed to read session file: {}", file, e);
            return List.of();
        }
        return entries;
    }

    // Walk entries past the header and collect messages from the linear path (last branch),
    // updating {@code lastEntryId} as we go for subsequent appendMessage chaining.
    private List<Message> extractMessages(List<Map<String, Object>> entries) {
        List<Message> messages = new ArrayList<>();
        for (int i = 1; i < entries.size(); i++) {
            var entry = entries.get(i);
            String entryId = (String) entry.get("id");
            if (entryId != null) {
                lastEntryId = entryId;
            }
            if (!"message".equals(entry.get("type")) || !entry.containsKey("message")) {
                continue;
            }
            Object raw = entry.get("message");
            if (raw instanceof Map<?, ?> m) {
                @SuppressWarnings("unchecked")
                Map<String, Object> mutable = (Map<String, Object>) m;
                backfillRoleIfMissing(mutable);
            }
            try {
                String msgJson = mapper.writeValueAsString(entry.get("message"));
                messages.add(mapper.readValue(msgJson, Message.class));
            } catch (JsonProcessingException e) {
                log.warn("Failed to parse message entry", e);
            }
        }
        return messages;
    }

    /**
     * Legacy session files written before the role-discriminator fix store
     * {@code message} objects without a {@code "role"} field, because the old
     * {@code appendMessage} put the {@link Message} into a {@code Map<String, Object>}
     * which suppresses Jackson's {@code @JsonTypeInfo}. Without {@code role} the
     * sealed-interface deserializer rejects the entry and the whole message
     * gets silently dropped on resume.
     *
     * <p>Inspect the surviving fields and inject the most-likely role so old
     * conversations can still be reopened. The heuristic is conservative:
     * {@code toolCallId+toolName} → toolResult, any assistant-only metadata
     * → assistant, otherwise → user.
     *
     * @param message the message
     */
    static void backfillRoleIfMissing(Map<String, Object> message) {
        if (message.containsKey("role")) {
            return;
        }
        message.put("role", inferRole(message));
    }

    /**
     * Inference rule shared with {@link ConversationLister}.
     *
     * @param message the message
     * @return the result
     */
    private static final List<String> ASSISTANT_ROLE_KEYS =
            List.of("api", "provider", "model", "responseId", "usage", "stopReason", "errorMessage");

    static String inferRole(Map<String, Object> message) {
        if (message.containsKey("toolCallId") && message.containsKey("toolName")) {
            return "toolResult";
        }
        if (ASSISTANT_ROLE_KEYS.stream().anyMatch(message::containsKey)) {
            return "assistant";
        }
        return "user";
    }

    /**
     * Appends a message entry to the session file.
     *
     * @param message the message
     */
    public void appendMessage(Message message) {
        if (sessionFile == null) {
            return;
        }

        String entryId = generateId();
        var entry = new LinkedHashMap<String, Object>();
        entry.put("type", "message");
        entry.put("id", entryId);
        entry.put("parentId", lastEntryId);
        entry.put("timestamp", Instant.now().toString());
        try {
            entry.put("message", mapper.readTree(messageWriter.writeValueAsString(message)));
        } catch (JsonProcessingException e) {
            log.warn("Failed to serialize message; skipping append", e);
            return;
        }
        appendRaw(entry);
        lastEntryId = entryId;
    }

    /**
     * Appends a model change entry to the session file.
     *
     * @param provider the provider
     * @param modelId the modelId
     */
    public void appendModelChange(String provider, String modelId) {
        if (sessionFile == null) {
            return;
        }

        String entryId = generateId();
        var entry = new LinkedHashMap<String, Object>();
        entry.put("type", "model_change");
        entry.put("id", entryId);
        entry.put("parentId", lastEntryId);
        entry.put("timestamp", Instant.now().toString());
        entry.put("provider", provider);
        entry.put("modelId", modelId);
        appendRaw(entry);
        lastEntryId = entryId;
    }

    /**
     * Appends a thinking level change entry to the session file.
     *
     * @param thinkingLevel the thinkingLevel
     */
    public void appendThinkingLevelChange(String thinkingLevel) {
        if (sessionFile == null) {
            return;
        }

        String entryId = generateId();
        var entry = new LinkedHashMap<String, Object>();
        entry.put("type", "thinking_level_change");
        entry.put("id", entryId);
        entry.put("parentId", lastEntryId);
        entry.put("timestamp", Instant.now().toString());
        entry.put("thinkingLevel", thinkingLevel);
        appendRaw(entry);
        lastEntryId = entryId;
    }

    /**
     * Appends a session name entry.
     *
     * @param name the name
     */
    public void appendSessionName(String name) {
        if (sessionFile == null) {
            return;
        }

        String entryId = generateId();
        var entry = new LinkedHashMap<String, Object>();
        entry.put("type", "session_info");
        entry.put("id", entryId);
        entry.put("parentId", lastEntryId);
        entry.put("timestamp", Instant.now().toString());
        entry.put("name", name);
        appendRaw(entry);
        lastEntryId = entryId;
    }

    public String getSessionId() {
        return sessionId;
    }

    public Path getSessionFile() {
        return sessionFile;
    }

    /**
     * Close the writer. Called on shutdown.
     */
    public void close() {
        if (writer != null) {
            try {
                writer.flush();
                writer.close();
            } catch (IOException e) {
                log.warn("Failed to close session writer", e);
            }
            writer = null;
        }
    }

    private void appendRaw(Map<String, Object> entry) {
        if (sessionFile == null) {
            return;
        }
        try {
            if (writer == null) {
                writer = new BufferedWriter(new FileWriter(sessionFile.toFile(), StandardCharsets.UTF_8, true));
            }
            writer.write(mapper.writeValueAsString(entry));
            writer.newLine();
            writer.flush();
        } catch (IOException e) {
            log.warn("Failed to append to session file: {}", sessionFile, e);
        }
    }

    private String generateId() {
        return UUID.randomUUID().toString().substring(0, 8);
    }
}
