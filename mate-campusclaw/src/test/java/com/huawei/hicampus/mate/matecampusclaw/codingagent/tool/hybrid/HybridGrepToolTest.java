/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.hicampus.mate.matecampusclaw.codingagent.tool.hybrid;

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

import com.huawei.hicampus.mate.matecampusclaw.agent.tool.AgentToolResult;
import com.huawei.hicampus.mate.matecampusclaw.ai.types.TextContent;
import com.huawei.hicampus.mate.matecampusclaw.codingagent.tool.execution.ExecutionMode;
import com.huawei.hicampus.mate.matecampusclaw.codingagent.tool.execution.ExecutionRouter;

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
class HybridGrepToolTest {

    @Mock
    ExecutionRouter router;

    @InjectMocks
    HybridGrepTool tool;

    private static AgentToolResult anyResult() {
        return new AgentToolResult(List.of(new TextContent("ok")), null);
    }

    @Nested
    class Metadata {

        @Test
        void identity() {
            assertThat(tool.name()).isEqualTo("grep");
            assertThat(tool.label()).isEqualTo("Grep");
            assertThat(tool.description()).isNotBlank();
        }

        @Test
        void parametersExposed() {
            var schema = tool.parameters();
            assertThat(schema.get("required").get(0).asText()).isEqualTo("pattern");
            assertThat(schema.get("properties").has("path")).isTrue();
            assertThat(schema.get("properties").has("glob")).isTrue();
            assertThat(schema.get("properties").has("_executionMode")).isTrue();
        }
    }

    @Nested
    class Routing {

        @Test
        void modeForwardedWhenValid() throws Exception {
            when(router.route(anyString(), anyMap(), any(), any(), any())).thenReturn(anyResult());
            tool.execute("id", Map.of("pattern", "x", "_executionMode", "sandbox"), null, null);
            verify(router).route(eq("grep"), anyMap(), eq(ExecutionMode.SANDBOX), any(), any());
        }

        @Test
        void invalidModeFallsBackToNull() throws Exception {
            when(router.route(eq("grep"), anyMap(), isNull(), any(), any())).thenReturn(anyResult());
            tool.execute("id", Map.of("pattern", "x", "_executionMode", "garbage"), null, null);
            verify(router).route(eq("grep"), anyMap(), isNull(), any(), any());
        }

        @Test
        void noModeYieldsNull() throws Exception {
            when(router.route(eq("grep"), anyMap(), isNull(), any(), any())).thenReturn(anyResult());
            tool.execute("id", Map.of("pattern", "x"), null, null);
            verify(router).route(eq("grep"), anyMap(), isNull(), any(), any());
        }
    }
}
