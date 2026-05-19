/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.campusclaw.codingagent.subagent;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.stream.Stream;

import com.campusclaw.agent.subagent.SubAgentSessionRecord;
import com.campusclaw.agent.subagent.SubAgentSessionStore;
import com.campusclaw.codingagent.config.AppPaths;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * File-backed {@link SubAgentSessionStore} that writes one JSON document per session under
 * {@code ~/.campusclaw/agent/subagents/<backend>/<uuid>.json}.
 *
 * <p>Failures are logged and swallowed: persistence is best-effort metadata, not a correctness
 * dependency for the prompt path.
 *
 * @version [br_eCampusCore 25.1.0_Next, 2026/05/12]
 * @since [br_eCampusCore 25.1.0_Next]
 */
@Component
public class FileSubAgentSessionStore implements SubAgentSessionStore {

    private static final Logger log = LoggerFactory.getLogger(FileSubAgentSessionStore.class);
    private static final Path DEFAULT_ROOT = AppPaths.USER_AGENT_DIR.resolve("subagents");

    private final Path root;
    private final ObjectMapper mapper;

    @Autowired
    public FileSubAgentSessionStore(ObjectMapper mapper) {
        this(DEFAULT_ROOT, mapper);
    }

    public FileSubAgentSessionStore(Path root, ObjectMapper mapper) {
        this.root = root;
        this.mapper = mapper.findAndRegisterModules();
    }

    @Override
    public void save(SubAgentSessionRecord record) {
        if (record == null) {
            return;
        }
        try {
            Path file = resolveFile(record);
            Files.createDirectories(file.getParent());
            Files.writeString(file, mapper.writeValueAsString(record));
        } catch (IOException ex) {
            log.warn("failed to persist sub-agent session {}: {}", record.sessionKey(), ex.toString());
        }
    }

    @Override
    public Optional<SubAgentSessionRecord> load(String sessionKey) {
        if (sessionKey == null) {
            return Optional.empty();
        }
        Path file = locate(sessionKey);
        if (file == null || !Files.exists(file)) {
            return Optional.empty();
        }
        try {
            byte[] bytes = Files.readAllBytes(file);
            return Optional.of(mapper.readValue(bytes, SubAgentSessionRecord.class));
        } catch (IOException ex) {
            log.warn("failed to read sub-agent session {}: {}", sessionKey, ex.toString());
            return Optional.empty();
        }
    }

    @Override
    public List<SubAgentSessionRecord> listOpen() {
        if (!Files.isDirectory(root)) {
            return List.of();
        }
        var open = new ArrayList<SubAgentSessionRecord>();
        try (Stream<Path> backends = Files.list(root)) {
            backends.filter(Files::isDirectory).forEach(backend -> collectFrom(backend, open));
        } catch (IOException ex) {
            log.warn("failed to list sub-agent sessions: {}", ex.toString());
        }
        return open;
    }

    private void collectFrom(Path backendDir, List<SubAgentSessionRecord> sink) {
        try (Stream<Path> files = Files.list(backendDir)) {
            files.filter(path -> path.toString().toLowerCase(Locale.ROOT).endsWith(".json"))
                    .forEach(path -> readIfOpen(path, sink));
        } catch (IOException ex) {
            log.warn("failed to list backend dir {}: {}", backendDir, ex.toString());
        }
    }

    private void readIfOpen(Path file, List<SubAgentSessionRecord> sink) {
        try {
            var record = mapper.readValue(Files.readAllBytes(file), SubAgentSessionRecord.class);
            if (record.closedAt() == null) {
                sink.add(record);
            }
        } catch (IOException ex) {
            log.debug("skipping malformed record {}: {}", file, ex.toString());
        }
    }

    private Path resolveFile(SubAgentSessionRecord record) {
        return root.resolve(safe(record.backendId())).resolve(safe(extractUuid(record.sessionKey())) + ".json");
    }

    private Path locate(String sessionKey) {
        String uuid = extractUuid(sessionKey);
        if (uuid == null) {
            return null;
        }
        if (!Files.isDirectory(root)) {
            return null;
        }
        try (Stream<Path> backends = Files.list(root)) {
            return backends.filter(Files::isDirectory)
                    .map(dir -> dir.resolve(safe(uuid) + ".json"))
                    .filter(Files::exists)
                    .findFirst()
                    .orElse(null);
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
    }

    private static String extractUuid(String sessionKey) {
        if (sessionKey == null) {
            return null;
        }
        int lastColon = sessionKey.lastIndexOf(':');
        return lastColon < 0 ? null : sessionKey.substring(lastColon + 1);
    }

    private static String safe(String value) {
        if (value == null || value.isBlank()) {
            return "unknown";
        }
        return value.replaceAll("[^A-Za-z0-9._-]", "_");
    }
}
