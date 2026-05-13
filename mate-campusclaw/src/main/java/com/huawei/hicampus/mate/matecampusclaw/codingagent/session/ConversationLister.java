/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.hicampus.mate.matecampusclaw.codingagent.session;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

import com.huawei.hicampus.mate.matecampusclaw.codingagent.config.AppPaths;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Scans the per-cwd JSONL session directory and produces a browsable list of
 * conversations for the WS frontend's "open existing chat" picker.
 *
 * <p>Each entry is derived by walking one JSONL file: the first line gives the
 * session header (id, createdAt); subsequent {@code "type":"message"} entries
 * contribute to {@code messageCount}, and the first user message's first
 * {@code TextContent} block becomes the title. File mtime is used for
 * {@code updatedAt} since the JSONL itself doesn't track it.
 *
 * <p>Cheap on filesystems with dozens of conversations. If a deployment ever
 * accumulates thousands of files, replace the per-call scan with an index.
 *
 * @version [br_eCampusCore 25.1.0_Next, 2026/05/06]
 * @since [br_eCampusCore 25.1.0_Next]
 */
public class ConversationLister {

    private static final Logger log = LoggerFactory.getLogger(ConversationLister.class);
    private static final int TITLE_MAX_CHARS = 80;

    private final ObjectMapper mapper = new ObjectMapper();

    /** Listed conversation entry. Wire-format-friendly via Jackson. */
    public record Entry(String id, String title, int messageCount, String createdAt, String updatedAt) {}

    /**
     * Lists every {@code .jsonl} session file under the directory derived from
     * {@code cwd}, sorted by {@code updatedAt} descending (newest first).
     * Returns an empty list when the directory is missing.
     *
     * @param cwd the cwd
     * @return the result
     */
    public List<Entry> list(String cwd) {
        Path dir = sessionsDirFor(cwd);
        if (!Files.isDirectory(dir)) {
            return List.of();
        }

        List<Entry> entries = new ArrayList<>();
        try (var stream = Files.list(dir)) {
            stream.filter(p -> p.toString().endsWith(".jsonl")).forEach(p -> {
                Entry e = readEntry(p);
                if (e != null) {
                    entries.add(e);
                }
            });
        } catch (IOException e) {
            log.warn("Failed to list session directory: {}", dir, e);
            return List.of();
        }

        entries.sort(Comparator.comparing(Entry::updatedAt).reversed());
        return entries;
    }

    /**
     * Resolves the on-disk JSONL directory for the given working directory.
     *
     * @param cwd the cwd
     * @return the result
     */
    public Path sessionsDirFor(String cwd) {
        String safePath = "--" + cwd.replaceFirst("^[/\\\\]", "").replaceAll("[/\\\\:]", "-") + "--";
        return AppPaths.SESSIONS_DIR.resolve(safePath);
    }

    private Entry readEntry(Path file) {
        String idFromHeader = null;
        String createdAt = null;
        int messageCount = 0;
        String title = null;

        try (var reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isBlank()) {
                    continue;
                }
                JsonNode node;
                try {
                    node = mapper.readTree(line);
                } catch (JsonProcessingException malformed) {
                    continue;
                }
                String type = node.path("type").asText();
                if ("session".equals(type) && idFromHeader == null) {
                    idFromHeader = node.path("id").asText(null);
                    createdAt = node.path("timestamp").asText(null);
                } else if ("message".equals(type)) {
                    messageCount++;
                    if (title == null) {
                        title = extractUserTitle(node.path("message"));
                    }
                }
            }
        } catch (IOException e) {
            log.warn("Failed to read session file: {}", file, e);
            return null;
        }

        // Filename is authoritative for the id (it's what clients reconnect with);
        // header id is sanity-checked but not required to match.
        String fileId = stripJsonlSuffix(file.getFileName().toString());
        if (idFromHeader != null && !idFromHeader.equals(fileId)) {
            log.debug("session header id ({}) differs from filename id ({})", idFromHeader, fileId);
        }

        String updatedAt;
        try {
            updatedAt = Files.getLastModifiedTime(file).toInstant().toString();
        } catch (IOException e) {
            updatedAt = Instant.EPOCH.toString();
        }

        if (createdAt == null) {
            createdAt = updatedAt;
        }
        if (title == null || title.isBlank()) {
            title = fileId;
        }

        return new Entry(fileId, title, messageCount, createdAt, updatedAt);
    }

    /**
     * Returns the first user message's leading {@code TextContent} text, or
     * {@code null} if the entry is not a user message or has no text content.
     *
     * <p>For legacy JSONL files written before the role-discriminator fix,
     * the {@code role} field is absent. We fall back to the same shape-based
     * inference used by {@link SessionManager#inferRole(Map)} so legacy
     * conversations still get a meaningful title in the picker.
     *
     * @param messageNode the messageNode
     * @return the result
     */
    private static String extractUserTitle(JsonNode messageNode) {
        if (messageNode == null || messageNode.isMissingNode()) {
            return null;
        }
        String role = messageNode.path("role").asText("");
        if (role.isEmpty()) {
            role = inferRoleFromJsonNode(messageNode);
        }
        if (!"user".equals(role)) {
            return null;
        }
        JsonNode content = messageNode.path("content");
        if (!content.isArray()) {
            return null;
        }
        for (JsonNode block : content) {
            if ("text".equals(block.path("type").asText())) {
                String text = block.path("text").asText("");
                if (!text.isEmpty()) {
                    return truncate(text);
                }
            }
        }
        return null;
    }

    /**
     * Mirrors {@link SessionManager#inferRole(Map)} but reads from a JsonNode.
     *
     * @param message the message
     * @return the result
     */
    private static String inferRoleFromJsonNode(JsonNode message) {
        if (message.has("toolCallId") && message.has("toolName")) {
            return "toolResult";
        }
        if (message.has("api")
                || message.has("provider")
                || message.has("model")
                || message.has("responseId")
                || message.has("usage")
                || message.has("stopReason")
                || message.has("errorMessage")) {
            return "assistant";
        }
        return "user";
    }

    private static String truncate(String s) {
        String collapsed = s.replaceAll("\\s+", " ").trim();
        if (collapsed.length() <= TITLE_MAX_CHARS) {
            return collapsed;
        }
        return collapsed.substring(0, TITLE_MAX_CHARS - 1) + "…";
    }

    private static String stripJsonlSuffix(String name) {
        return name.endsWith(".jsonl") ? name.substring(0, name.length() - ".jsonl".length()) : name;
    }

    /**
     * Convenience: lists conversations for the JVM's working directory. The
     * server uses this since it has no per-request cwd notion.
     *
     * @return the result
     */
    public List<Entry> listForServer() {
        return list(System.getProperty("user.dir"));
    }

    /**
     * Convenience static for callers that don't want to keep an instance around.
     *
     * @param entries the entries
     * @return the result
     */
    public static List<Map<String, Object>> toWireFormat(List<Entry> entries) {
        List<Map<String, Object>> wire = new ArrayList<>(entries.size());
        for (Entry e : entries) {
            wire.add(Map.of(
                    "id", e.id(),
                    "title", e.title(),
                    "messageCount", e.messageCount(),
                    "createdAt", e.createdAt(),
                    "updatedAt", e.updatedAt()));
        }
        return wire;
    }
}
