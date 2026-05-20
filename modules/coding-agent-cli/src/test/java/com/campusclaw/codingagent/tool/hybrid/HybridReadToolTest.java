/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.campusclaw.codingagent.tool.hybrid;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;

import com.campusclaw.agent.tool.AgentToolResult;
import com.campusclaw.ai.types.TextContent;
import com.campusclaw.codingagent.tool.execution.ExecutionMode;
import com.campusclaw.codingagent.tool.execution.ExecutionRouter;

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
class HybridReadToolTest {

    @Mock
    ExecutionRouter router;

    @InjectMocks
    HybridReadTool tool;

    private static AgentToolResult anyResult() {
        return new AgentToolResult(List.of(new TextContent("ok")), null);
    }

    @Nested
    class Metadata {

        @Test
        void identity() {
            assertThat(tool.name()).isEqualTo("read");
            assertThat(tool.label()).isEqualTo("Read");
            assertThat(tool.description()).isNotBlank();
        }

        @Test
        void parametersExposed() {
            var schema = tool.parameters();
            assertThat(schema.get("required").get(0).asText()).isEqualTo("path");
            assertThat(schema.get("properties").has("path")).isTrue();
            assertThat(schema.get("properties").has("offset")).isTrue();
            assertThat(schema.get("properties").has("limit")).isTrue();
            assertThat(schema.get("properties").has("_executionMode")).isTrue();
        }
    }

    @Nested
    class Routing {

        @Test
        void noExecutionModeRoutesWithNull() throws Exception {
            when(router.route(eq("read"), anyMap(), isNull(), any(), any())).thenReturn(anyResult());
            tool.execute("id", Map.of("path", "x"), null, null);
            verify(router).route(eq("read"), anyMap(), isNull(), any(), any());
        }

        @Test
        void explicitLocalModeForwarded() throws Exception {
            when(router.route(anyString(), anyMap(), any(), any(), any())).thenReturn(anyResult());
            tool.execute("id", Map.of("path", "x", "_executionMode", "local"), null, null);
            verify(router).route(eq("read"), anyMap(), eq(ExecutionMode.LOCAL), any(), any());
        }

        @Test
        void explicitSandboxModeForwarded() throws Exception {
            when(router.route(anyString(), anyMap(), any(), any(), any())).thenReturn(anyResult());
            tool.execute("id", Map.of("path", "x", "_executionMode", "SANDBOX"), null, null);
            verify(router).route(eq("read"), anyMap(), eq(ExecutionMode.SANDBOX), any(), any());
        }

        @Test
        void invalidModeFallsBackToNull() throws Exception {
            when(router.route(eq("read"), anyMap(), isNull(), any(), any())).thenReturn(anyResult());
            tool.execute("id", Map.of("path", "x", "_executionMode", "garbage"), null, null);
            verify(router).route(eq("read"), anyMap(), isNull(), any(), any());
        }
    }
}
