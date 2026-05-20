/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.campusclaw.codingagent.loop;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;

import com.campusclaw.agent.tool.AgentToolResult;
import com.campusclaw.ai.types.TextContent;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class LoopToolTest {

    @Mock
    LoopManager loopManager;

    @InjectMocks
    LoopTool tool;

    private static String text(AgentToolResult r) {
        return ((TextContent) r.content().get(0)).text();
    }

    @Nested
    class Metadata {

        @Test
        void identity() {
            assertThat(tool.name()).isEqualTo("loop");
            assertThat(tool.label()).isEqualTo("Loop");
            assertThat(tool.description()).isNotBlank();
        }

        @Test
        void parametersSchema() {
            var schema = tool.parameters();
            assertThat(schema.get("required").get(0).asText()).isEqualTo("action");
            assertThat(schema.get("properties").has("prompt")).isTrue();
            assertThat(schema.get("properties").has("interval_ms")).isTrue();
        }
    }

    @Nested
    class ExecuteGuards {

        @Test
        void missingActionRejected() {
            when(loopManager.isInitialized()).thenReturn(true);
            assertThat(text(tool.execute("id", Map.of(), null, null))).contains("action is required");
        }

        @Test
        void notInitializedRejected() {
            when(loopManager.isInitialized()).thenReturn(false);
            assertThat(text(tool.execute("id", Map.of("action", "list"), null, null)))
                    .contains("only available in interactive mode");
        }

        @Test
        void unknownActionRejected() {
            when(loopManager.isInitialized()).thenReturn(true);
            assertThat(text(tool.execute("id", Map.of("action", "bogus"), null, null)))
                    .contains("unknown action");
        }
    }

    @Nested
    class Start {

        @Test
        void requiresPrompt() {
            when(loopManager.isInitialized()).thenReturn(true);
            assertThat(text(tool.execute("id", Map.of("action", "start"), null, null)))
                    .contains("prompt is required");
        }

        @Test
        void rejectsTooShortInterval() {
            when(loopManager.isInitialized()).thenReturn(true);
            Map<String, Object> p = Map.of("action", "start", "prompt", "hi", "interval_ms", 500);
            assertThat(text(tool.execute("id", p, null, null))).contains("at least 1000ms");
        }

        @Test
        void defaultIntervalUsedWhenAbsent() {
            when(loopManager.isInitialized()).thenReturn(true);
            when(loopManager.start(anyString(), anyLong())).thenReturn("L1");
            Map<String, Object> p = Map.of("action", "start", "prompt", "hi");
            String out = text(tool.execute("id", p, null, null));
            assertThat(out).contains("Started loop").contains("L1").contains("10m");
        }

        @Test
        void formatsHoursAndSeconds() {
            when(loopManager.isInitialized()).thenReturn(true);
            when(loopManager.start(anyString(), anyLong())).thenReturn("LX");

            // 2 hours
            Map<String, Object> p = Map.of("action", "start", "prompt", "hi", "interval_ms", 7_200_000L);
            assertThat(text(tool.execute("id", p, null, null))).contains("every 2h");

            // 30 seconds
            Map<String, Object> p2 = Map.of("action", "start", "prompt", "hi", "interval_ms", 30_000L);
            assertThat(text(tool.execute("id", p2, null, null))).contains("every 30s");

            // 1500ms — falls through to raw ms
            Map<String, Object> p3 = Map.of("action", "start", "prompt", "hi", "interval_ms", 1500L);
            assertThat(text(tool.execute("id", p3, null, null))).contains("1500ms");
        }
    }

    @Nested
    class StopActions {

        @Test
        void stopRequiresId() {
            when(loopManager.isInitialized()).thenReturn(true);
            assertThat(text(tool.execute("id", Map.of("action", "stop"), null, null)))
                    .contains("id is required");
        }

        @Test
        void stopSuccessAndFailure() {
            when(loopManager.isInitialized()).thenReturn(true);
            when(loopManager.stop("L1")).thenReturn(true);
            assertThat(text(tool.execute("id", Map.of("action", "stop", "id", "L1"), null, null)))
                    .contains("Stopped loop");
            when(loopManager.stop("L2")).thenReturn(false);
            assertThat(text(tool.execute("id", Map.of("action", "stop", "id", "L2"), null, null)))
                    .contains("Loop not found");
        }

        @Test
        void stopAllReportsCount() {
            when(loopManager.isInitialized()).thenReturn(true);
            when(loopManager.stopAll()).thenReturn(3);
            assertThat(text(tool.execute("id", Map.of("action", "stop_all"), null, null)))
                    .contains("Stopped 3");
        }
    }

    @Nested
    class List_ {

        @Test
        void emptyListMessage() {
            when(loopManager.isInitialized()).thenReturn(true);
            when(loopManager.list()).thenReturn(List.of());
            assertThat(text(tool.execute("id", Map.of("action", "list"), null, null)))
                    .contains("No active loops");
        }

        @Test
        void rendersEntries() {
            when(loopManager.isInitialized()).thenReturn(true);
            LoopManager.LoopEntry a = new LoopManager.LoopEntry("L1", "do something", 60_000L, null);
            LoopManager.LoopEntry b = new LoopManager.LoopEntry("L2", "and then", 3_600_000L, null);
            when(loopManager.list()).thenReturn(List.of(a, b));
            String out = text(tool.execute("id", Map.of("action", "list"), null, null));
            assertThat(out)
                    .contains("Active loops")
                    .contains("L1")
                    .contains("do something")
                    .contains("1m")
                    .contains("L2")
                    .contains("and then")
                    .contains("1h");
        }
    }
}
