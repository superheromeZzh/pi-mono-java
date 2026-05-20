/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.hicampus.mate.matecampusclaw.codingagent.command.builtin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import com.huawei.hicampus.mate.matecampusclaw.codingagent.command.SlashCommandContext;
import com.huawei.hicampus.mate.matecampusclaw.codingagent.session.AgentSession;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class HotkeysAndImportCommandTest {

    @Mock
    AgentSession session;

    private final List<String> out = new ArrayList<>();

    @Nested
    class Hotkeys {

        @Test
        void metadata() {
            HotkeysCommand c = new HotkeysCommand();
            assertThat(c.name()).isEqualTo("hotkeys");
            assertThat(c.description()).isNotBlank();
        }

        @Test
        void printsAllShortcuts() {
            SlashCommandContext ctx = new SlashCommandContext(session, out::add);
            new HotkeysCommand().execute(ctx, "");
            String joined = String.join("\n", out);
            assertThat(joined)
                    .contains("Keyboard Shortcuts")
                    .contains("Ctrl+C")
                    .contains("Ctrl+D")
                    .contains("Alt+Enter")
                    .contains("/command")
                    .contains("!command");
        }
    }

    @Nested
    class Import_ {

        @Test
        void metadata() {
            ImportCommand c = new ImportCommand();
            assertThat(c.name()).isEqualTo("import");
            assertThat(c.description()).isNotBlank();
        }

        @Test
        void emptyArgsShowsUsage() {
            SlashCommandContext ctx = new SlashCommandContext(session, out::add);
            new ImportCommand().execute(ctx, "");
            assertThat(out).anyMatch(s -> s.contains("Usage:"));
        }

        @Test
        void nullArgsShowsUsage() {
            SlashCommandContext ctx = new SlashCommandContext(session, out::add);
            new ImportCommand().execute(ctx, null);
            assertThat(out).anyMatch(s -> s.contains("Usage:"));
        }

        @Test
        void missingFileReportsError(@TempDir Path tmp) {
            SlashCommandContext ctx = new SlashCommandContext(session, out::add);
            new ImportCommand().execute(ctx, tmp.resolve("nonexistent.jsonl").toString());
            assertThat(out).anyMatch(s -> s.contains("File not found"));
        }

        @Test
        void noSessionManagerReportsError(@TempDir Path tmp) throws Exception {
            Path file = tmp.resolve("s.jsonl");
            java.nio.file.Files.writeString(file, "");
            when(session.getSessionManager()).thenReturn(null);
            SlashCommandContext ctx = new SlashCommandContext(session, out::add);
            new ImportCommand().execute(ctx, file.toString());
            assertThat(out).anyMatch(s -> s.contains("persistence is disabled"));
        }
    }
}
