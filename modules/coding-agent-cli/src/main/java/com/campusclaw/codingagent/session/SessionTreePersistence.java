/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.campusclaw.codingagent.session;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

import com.fasterxml.jackson.databind.ObjectMapper;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Persists session trees as JSONL files (one entry per line).
 *
 * @version [br_eCampusCore 25.1.0_Next, 2026/05/06]
 * @since [br_eCampusCore 25.1.0_Next]
 */
public class SessionTreePersistence {
    private static final Logger log = LoggerFactory.getLogger(SessionTreePersistence.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final Path sessionDir;

    public SessionTreePersistence(Path sessionDir) {
        this.sessionDir = sessionDir;
    }

    public SessionTreePersistence() {
        this(com.campusclaw.codingagent.config.AppPaths.SESSIONS_DIR);
    }

    /**
     * Save a session tree to a JSONL file.
     *
     * @param sessionName the sessionName
     * @param tree the tree
     */
    public void save(String sessionName, SessionTree tree) {
        Path file = sessionDir.resolve(sessionName + ".jsonl");
        try {
            Files.createDirectories(sessionDir);
            try (var writer =
                    Files.newBufferedWriter(file, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)) {
                for (SessionEntry entry : tree.getAllEntries()) {
                    writer.write(MAPPER.writeValueAsString(entry));
                    writer.newLine();
                }
            }
            log.debug("Saved session '{}' with {} entries", sessionName, tree.size());
        } catch (IOException e) {
            log.error("Failed to save session '{}'", sessionName, e);
        }
    }

    /**
     * Append a single entry to an existing session file.
     *
     * @param sessionName the sessionName
     * @param entry the entry
     */
    public void appendEntry(String sessionName, SessionEntry entry) {
        Path file = sessionDir.resolve(sessionName + ".jsonl");
        try {
            Files.createDirectories(sessionDir);
            try (var writer = Files.newBufferedWriter(file, StandardOpenOption.CREATE, StandardOpenOption.APPEND)) {
                writer.write(MAPPER.writeValueAsString(entry));
                writer.newLine();
            }
        } catch (IOException e) {
            log.error("Failed to append entry to session '{}'", sessionName, e);
        }
    }

    /**
     * Load a session tree from a JSONL file.
     *
     * @param sessionName the sessionName
     * @return the result
     */
    public SessionTree load(String sessionName) {
        Path file = sessionDir.resolve(sessionName + ".jsonl");
        SessionTree tree = new SessionTree();
        if (!Files.exists(file)) {
            return tree;
        }

        try (var reader = Files.newBufferedReader(file)) {
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty()) {
                    continue;
                }
                try {
                    SessionEntry entry = MAPPER.readValue(line, SessionEntry.class);
                    tree.addEntry(entry);
                } catch (Exception e) {
                    log.warn("Skipping malformed session entry: {}", e.getMessage());
                }
            }
        } catch (IOException e) {
            log.error("Failed to load session '{}'", sessionName, e);
        }

        return tree;
    }

    /**
     * List all available session names.
     *
     * @return the result
     */
    public java.util.List<String> listSessions() {
        if (!Files.isDirectory(sessionDir)) {
            return java.util.List.of();
        }
        try (var stream = Files.list(sessionDir)) {
            return stream.filter(p -> p.toString().endsWith(".jsonl"))
                    .map(p -> p.getFileName().toString().replace(".jsonl", ""))
                    .sorted()
                    .toList();
        } catch (IOException e) {
            log.error("Failed to list sessions", e);
            return java.util.List.of();
        }
    }

    /**
     * Delete a session file.
     *
     * @param sessionName the sessionName
     * @return the result
     */
    public boolean delete(String sessionName) {
        try {
            return Files.deleteIfExists(sessionDir.resolve(sessionName + ".jsonl"));
        } catch (IOException e) {
            log.error("Failed to delete session '{}'", sessionName, e);
            return false;
        }
    }
}
