/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.hicampus.mate.matecampusclaw.codingagent.subagent;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import com.huawei.hicampus.mate.matecampusclaw.agent.subagent.SubAgentSessionRecord;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class FileSubAgentSessionStoreTest {

    private final ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule());

    @Test
    void savePersistsRecordInBackendSubdir(@TempDir Path root) {
        var store = new FileSubAgentSessionStore(root, mapper);
        var record = new SubAgentSessionRecord(
                "agent:main:claude-code:abc-123",
                "claude-code",
                "remote-1",
                "build help",
                Instant.parse("2026-05-12T10:00:00Z"),
                null);

        store.save(record);

        Path file = root.resolve("claude-code").resolve("abc-123.json");
        assertThat(Files.exists(file)).isTrue();
    }

    @Test
    void loadReadsBackPersistedRecord(@TempDir Path root) throws Exception {
        var store = new FileSubAgentSessionStore(root, mapper);
        var record = new SubAgentSessionRecord(
                "agent:main:claude-code:abc-123",
                "claude-code",
                "remote-1",
                null,
                Instant.parse("2026-05-12T10:00:00Z"),
                null);
        store.save(record);

        Optional<SubAgentSessionRecord> loaded = store.load(record.sessionKey());

        assertThat(loaded).isPresent();
        assertThat(loaded.get().runtimeSessionId()).isEqualTo("remote-1");
    }

    @Test
    void listOpenReturnsOnlyOpenRecords(@TempDir Path root) {
        var store = new FileSubAgentSessionStore(root, mapper);
        var open = new SubAgentSessionRecord(
                "agent:main:claude-code:open-1",
                "claude-code",
                "r1",
                null,
                Instant.parse("2026-05-12T10:00:00Z"),
                null);
        var closed = new SubAgentSessionRecord(
                "agent:main:claude-code:closed-1",
                "claude-code",
                "r2",
                null,
                Instant.parse("2026-05-12T09:00:00Z"),
                Instant.parse("2026-05-12T09:30:00Z"));
        store.save(open);
        store.save(closed);

        List<SubAgentSessionRecord> openRecords = store.listOpen();

        assertThat(openRecords).extracting(SubAgentSessionRecord::sessionKey).containsExactly(open.sessionKey());
    }
}
