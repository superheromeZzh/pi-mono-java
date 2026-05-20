/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.hicampus.mate.matecampusclaw.codingagent.tool.hybrid;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
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
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class HybridToolsTest {

    @Mock
    ExecutionRouter router;

    private static AgentToolResult anyResult() {
        return new AgentToolResult(List.of(new TextContent("ok")), null);
    }

    @Nested
    class HybridEdit {

        @Test
        void identityAndRouting() throws Exception {
            HybridEditTool t = new HybridEditTool(router);
            assertThat(t.name()).isEqualTo("edit");
            assertThat(t.label()).isEqualTo("Edit");
            assertThat(t.parameters().get("properties").has("oldText")).isTrue();
            assertThat(t.parameters().get("properties").has("newText")).isTrue();

            when(router.route(any(), anyMap(), any(), any(), any())).thenReturn(anyResult());
            t.execute("id", Map.of("path", "x", "_executionMode", "local"), null, null);
            verify(router).route(eq("edit"), anyMap(), eq(ExecutionMode.LOCAL), any(), any());
        }

        @Test
        void invalidModeNull() throws Exception {
            HybridEditTool t = new HybridEditTool(router);
            when(router.route(eq("edit"), anyMap(), isNull(), any(), any())).thenReturn(anyResult());
            t.execute("id", Map.of("path", "x", "_executionMode", "zzz"), null, null);
            verify(router).route(eq("edit"), anyMap(), isNull(), any(), any());
        }
    }

    @Nested
    class HybridWrite {

        @Test
        void identityAndRouting() throws Exception {
            HybridWriteTool t = new HybridWriteTool(router);
            assertThat(t.name()).isEqualTo("write");
            assertThat(t.label()).isEqualTo("Write");
            when(router.route(any(), anyMap(), any(), any(), any())).thenReturn(anyResult());
            t.execute("id", Map.of("path", "x", "content", "y", "_executionMode", "sandbox"), null, null);
            verify(router).route(eq("write"), anyMap(), eq(ExecutionMode.SANDBOX), any(), any());
        }

        @Test
        void parametersExposed() {
            HybridWriteTool t = new HybridWriteTool(router);
            assertThat(t.parameters().get("properties").has("path")).isTrue();
            assertThat(t.parameters().get("properties").has("content")).isTrue();
        }
    }

    @Nested
    class HybridGlob {

        @Test
        void identityAndRouting() throws Exception {
            HybridGlobTool t = new HybridGlobTool(router);
            assertThat(t.name()).isEqualTo("glob");
            assertThat(t.label()).isEqualTo("Glob");
            when(router.route(any(), anyMap(), any(), any(), any())).thenReturn(anyResult());
            t.execute("id", Map.of("pattern", "*.java"), null, null);
            verify(router).route(eq("glob"), anyMap(), isNull(), any(), any());
        }

        @Test
        void parametersExposed() {
            HybridGlobTool t = new HybridGlobTool(router);
            assertThat(t.parameters().get("required").get(0).asText()).isEqualTo("pattern");
        }
    }

    @Nested
    class HybridBash {

        @Test
        void identityAndRouting() throws Exception {
            HybridBashTool t = new HybridBashTool(router);
            assertThat(t.name()).isEqualTo("bash");
            assertThat(t.label()).isEqualTo("Bash");
            when(router.route(any(), anyMap(), any(), any(), any())).thenReturn(anyResult());
            t.execute("id", Map.of("command", "ls"), null, null);
            verify(router).route(eq("bash"), anyMap(), isNull(), any(), any());
        }

        @Test
        void parametersExposed() {
            HybridBashTool t = new HybridBashTool(router);
            assertThat(t.parameters().get("required").get(0).asText()).isEqualTo("command");
        }
    }
}
