/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.hicampus.mate.matecampusclaw.codingagent.command.builtin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;

import com.huawei.hicampus.mate.matecampusclaw.codingagent.command.SlashCommandContext;
import com.huawei.hicampus.mate.matecampusclaw.codingagent.loop.LoopManager;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class LoopCommandTest {

    @Mock
    LoopManager loopManager;

    private final List<String> out = new ArrayList<>();
    private final SlashCommandContext ctx = new SlashCommandContext(null, out::add);

    @Nested
    class Metadata {

        @Test
        void name() {
            assertThat(new LoopCommand(loopManager).name()).isEqualTo("loop");
        }

        @Test
        void description() {
            assertThat(new LoopCommand(loopManager).description()).isNotBlank();
        }
    }

    @Nested
    class Guards {

        @Test
        void notInitialisedRejected() {
            when(loopManager.isInitialized()).thenReturn(false);
            new LoopCommand(loopManager).execute(ctx, "anything");
            assertThat(out).first().asString().contains("only available in interactive");
        }

        @Test
        void emptyArgumentPrintsUsage() {
            when(loopManager.isInitialized()).thenReturn(true);
            new LoopCommand(loopManager).execute(ctx, "");
            assertThat(out).first().asString().contains("Usage");
            assertThat(out.get(0)).contains("Examples:");
        }

        @Test
        void nullArgumentPrintsUsage() {
            when(loopManager.isInitialized()).thenReturn(true);
            new LoopCommand(loopManager).execute(ctx, null);
            assertThat(out).first().asString().contains("Usage");
        }
    }

    @Nested
    class Stop {

        @Test
        void stopWithIdSuccess() {
            when(loopManager.isInitialized()).thenReturn(true);
            when(loopManager.stop("L1")).thenReturn(true);
            new LoopCommand(loopManager).execute(ctx, "stop L1");
            assertThat(out).first().asString().contains("Stopped loop");
        }

        @Test
        void stopWithIdNotFound() {
            when(loopManager.isInitialized()).thenReturn(true);
            when(loopManager.stop("L1")).thenReturn(false);
            new LoopCommand(loopManager).execute(ctx, "stop L1");
            assertThat(out).first().asString().contains("Loop not found");
        }

        @Test
        void stopWithoutIdStopsAll() {
            when(loopManager.isInitialized()).thenReturn(true);
            when(loopManager.stopAll()).thenReturn(2);
            new LoopCommand(loopManager).execute(ctx, "stop");
            assertThat(out).first().asString().contains("Stopped 2");
        }
    }

    @Nested
    class List_ {

        @Test
        void emptyMessage() {
            when(loopManager.isInitialized()).thenReturn(true);
            when(loopManager.list()).thenReturn(List.of());
            new LoopCommand(loopManager).execute(ctx, "list");
            assertThat(out).first().asString().contains("No active loops");
        }

        @Test
        void renderEntries() {
            when(loopManager.isInitialized()).thenReturn(true);
            LoopManager.LoopEntry a = new LoopManager.LoopEntry("L1", "do thing", 60_000, null);
            LoopManager.LoopEntry b = new LoopManager.LoopEntry(
                    "L2",
                    "a very long prompt that exceeds sixty characters total - definitely truncated please",
                    3_600_000,
                    null);
            when(loopManager.list()).thenReturn(List.of(a, b));
            new LoopCommand(loopManager).execute(ctx, "list");
            String result = out.get(0);
            assertThat(result)
                    .contains("L1")
                    .contains("do thing")
                    .contains("1m")
                    .contains("L2")
                    .contains("1h")
                    .contains("...");
        }
    }

    @Nested
    class Start {

        @Test
        void defaultIntervalWhenNoneSpecified() {
            when(loopManager.isInitialized()).thenReturn(true);
            when(loopManager.start(anyString(), anyLong())).thenReturn("L1");
            new LoopCommand(loopManager).execute(ctx, "do something");
            assertThat(out).first().asString().contains("Started loop").contains("10m");
        }

        @Test
        void parsesIntervalAndPrompt() {
            when(loopManager.isInitialized()).thenReturn(true);
            when(loopManager.start(anyString(), anyLong())).thenReturn("L2");
            new LoopCommand(loopManager).execute(ctx, "5m check deploy");
            assertThat(out).first().asString().contains("5m").contains("check deploy");
        }
    }

    @Nested
    class ParseInterval {

        @Test
        void variousUnits() {
            assertThat(LoopCommand.parseInterval("5s")).isEqualTo(5000);
            assertThat(LoopCommand.parseInterval("5sec")).isEqualTo(5000);
            assertThat(LoopCommand.parseInterval("5secs")).isEqualTo(5000);
            assertThat(LoopCommand.parseInterval("5seconds")).isEqualTo(5000);
            assertThat(LoopCommand.parseInterval("2m")).isEqualTo(120_000);
            assertThat(LoopCommand.parseInterval("2min")).isEqualTo(120_000);
            assertThat(LoopCommand.parseInterval("2minutes")).isEqualTo(120_000);
            assertThat(LoopCommand.parseInterval("3h")).isEqualTo(3 * 3_600_000);
            assertThat(LoopCommand.parseInterval("3hr")).isEqualTo(3 * 3_600_000);
            assertThat(LoopCommand.parseInterval("3hours")).isEqualTo(3 * 3_600_000);
        }

        @Test
        void invalidInputs() {
            assertThat(LoopCommand.parseInterval(null)).isEqualTo(-1);
            assertThat(LoopCommand.parseInterval("x")).isEqualTo(-1);
            assertThat(LoopCommand.parseInterval("abc")).isEqualTo(-1);
            assertThat(LoopCommand.parseInterval("5xyz")).isEqualTo(-1);
            assertThat(LoopCommand.parseInterval("0s")).isEqualTo(-1);
            assertThat(LoopCommand.parseInterval("123")).isEqualTo(-1); // no unit
            assertThat(LoopCommand.parseInterval("abcs")).isEqualTo(-1); // no digits
        }
    }

    @Nested
    class FormatInterval {

        @Test
        void hoursMinutesSecondsAndMs() {
            assertThat(LoopCommand.formatInterval(3_600_000)).isEqualTo("1h");
            assertThat(LoopCommand.formatInterval(2 * 60_000)).isEqualTo("2m");
            assertThat(LoopCommand.formatInterval(30 * 1000)).isEqualTo("30s");
            assertThat(LoopCommand.formatInterval(1500)).isEqualTo("1500ms");
        }
    }
}
