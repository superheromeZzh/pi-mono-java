/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.hicampus.mate.matecampusclaw.agent.subagent.acp.backend;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import com.fasterxml.jackson.databind.ObjectMapper;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for the thin {@link ProcessAcpBackend} subclass presets:
 * {@link ClaudeCodeAcpBackend} (default {@code claude --acp}) and
 * {@link CodexAcpBackend} (default {@code codex-acp}). They both expose two constructors —
 * one defaulted, one with command/args overrides — and inherit everything else from
 * {@link ProcessAcpBackend}. The presets are tested by inspecting the {@code id()} they
 * surface (which must equal the class's {@code ID} constant) and constructing them with
 * overrides to confirm the second ctor compiles and wires through.
 */
class AcpBackendPresetsTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Nested
    class ClaudeCode {

        @Test
        void testDefaultConstructorWiresClaudeAcpId() {
            var backend = new ClaudeCodeAcpBackend(MAPPER, null, null, null);
            assertThat(backend.id()).isEqualTo(ClaudeCodeAcpBackend.ID).isEqualTo("claude-code");
        }

        @Test
        void testOverrideConstructorAcceptsCustomCommandAndArgs() {
            var backend = new ClaudeCodeAcpBackend(
                    MAPPER, null, null, null, "/usr/local/bin/claude-acp", List.of("--debug", "--port", "0"));
            assertThat(backend.id()).isEqualTo("claude-code");
        }
    }

    @Nested
    class Codex {

        @Test
        void testDefaultConstructorWiresCodexId() {
            var backend = new CodexAcpBackend(MAPPER, null, null, null);
            assertThat(backend.id()).isEqualTo(CodexAcpBackend.ID).isEqualTo("codex");
        }

        @Test
        void testOverrideConstructorAcceptsCustomCommandAndArgs() {
            var backend = new CodexAcpBackend(
                    MAPPER, null, null, null, "/opt/codex/bin/codex-acp", List.of("--profile", "dev"));
            assertThat(backend.id()).isEqualTo("codex");
        }
    }
}
