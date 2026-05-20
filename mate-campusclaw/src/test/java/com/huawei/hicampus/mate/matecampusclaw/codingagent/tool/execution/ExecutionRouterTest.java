/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.hicampus.mate.matecampusclaw.codingagent.tool.execution;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.huawei.hicampus.mate.matecampusclaw.agent.tool.AgentToolResult;
import com.huawei.hicampus.mate.matecampusclaw.ai.types.TextContent;
import com.huawei.hicampus.mate.matecampusclaw.codingagent.config.ToolExecutionProperties;
import com.huawei.hicampus.mate.matecampusclaw.codingagent.tool.bash.BashTool;
import com.huawei.hicampus.mate.matecampusclaw.codingagent.tool.edit.EditTool;
import com.huawei.hicampus.mate.matecampusclaw.codingagent.tool.glob.GlobTool;
import com.huawei.hicampus.mate.matecampusclaw.codingagent.tool.grep.GrepTool;
import com.huawei.hicampus.mate.matecampusclaw.codingagent.tool.read.ReadTool;
import com.huawei.hicampus.mate.matecampusclaw.codingagent.tool.sandbox.DockerSandboxClient;
import com.huawei.hicampus.mate.matecampusclaw.codingagent.tool.sandbox.ResourceLimits;
import com.huawei.hicampus.mate.matecampusclaw.codingagent.tool.sandbox.SandboxResult;
import com.huawei.hicampus.mate.matecampusclaw.codingagent.tool.sandbox.SandboxSecurityPolicy;
import com.huawei.hicampus.mate.matecampusclaw.codingagent.tool.write.WriteTool;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

/**
 * Unit tests for {@link ExecutionRouter}. Verifies routing decisions across
 * explicit mode overrides, auto mode risk assessment, sandbox vs local
 * fallback, and parameter-level mode overrides — covers the dispatch logic
 * that decides whether a tool call runs in-process or in a Docker sandbox.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ExecutionRouterTest {

    @Mock
    DockerSandboxClient sandboxClient;

    @Mock
    ReadTool readTool;

    @Mock
    WriteTool writeTool;

    @Mock
    EditTool editTool;

    @Mock
    BashTool bashTool;

    @Mock
    GlobTool globTool;

    @Mock
    GrepTool grepTool;

    private ToolExecutionProperties properties;
    private SandboxSecurityPolicy securityPolicy;
    private ExecutionRouter router;

    @BeforeEach
    void setUp() {
        properties = new ToolExecutionProperties();
        properties.setLocalExecutionEnabled(true);
        properties.setSandboxExecutionEnabled(false);
        securityPolicy = new SandboxSecurityPolicy();
        router = new ExecutionRouter(
                properties,
                securityPolicy,
                sandboxClient,
                Optional.of(readTool),
                Optional.of(writeTool),
                Optional.of(editTool),
                Optional.of(bashTool),
                Optional.of(globTool),
                Optional.of(grepTool));
    }

    private static AgentToolResult result(String text) {
        return new AgentToolResult(List.of(new TextContent(text)), null);
    }

    private static SandboxResult sbSuccess(String stdout) {
        return SandboxResult.builder().stdout(stdout).stderr("").exitCode(0).build();
    }

    @Nested
    class ExplicitLocalMode {

        @Test
        void readToolDispatchedLocally() throws Exception {
            when(readTool.execute(any(), any(), any(), any())).thenReturn(result("read-result"));
            AgentToolResult r = router.route("read", Map.of("path", "/tmp/x"), ExecutionMode.LOCAL, null, null);
            assertThat(((TextContent) r.content().get(0)).text()).isEqualTo("read-result");
            verify(readTool).execute(any(), any(), any(), any());
        }

        @Test
        void writeToolDispatchedLocally() throws Exception {
            when(writeTool.execute(any(), any(), any(), any())).thenReturn(result("write-result"));
            router.route("write", Map.of("path", "/tmp/x", "content", "y"), ExecutionMode.LOCAL, null, null);
            verify(writeTool).execute(any(), any(), any(), any());
        }

        @Test
        void editBashGlobGrepEachDispatched() throws Exception {
            when(editTool.execute(any(), any(), any(), any())).thenReturn(result("edit"));
            when(bashTool.execute(any(), any(), any(), any())).thenReturn(result("bash"));
            when(globTool.execute(any(), any(), any(), any())).thenReturn(result("glob"));
            when(grepTool.execute(any(), any(), any(), any())).thenReturn(result("grep"));
            router.route("edit", Map.of("path", "/tmp"), ExecutionMode.LOCAL, null, null);
            router.route("bash", Map.of("command", "ls"), ExecutionMode.LOCAL, null, null);
            router.route("glob", Map.of("pattern", "*"), ExecutionMode.LOCAL, null, null);
            router.route("grep", Map.of("pattern", "x"), ExecutionMode.LOCAL, null, null);
            verify(editTool).execute(any(), any(), any(), any());
            verify(bashTool).execute(any(), any(), any(), any());
            verify(globTool).execute(any(), any(), any(), any());
            verify(grepTool).execute(any(), any(), any(), any());
        }

        @Test
        void localDisabledThrows() {
            properties.setLocalExecutionEnabled(false);
            assertThatThrownBy(() -> router.route("read", Map.of("path", "/tmp/x"), ExecutionMode.LOCAL, null, null))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("Local execution is disabled");
        }

        @Test
        void unknownToolWithSandboxDisabledThrows() {
            assertThatThrownBy(() -> router.route("mystery", Map.of(), ExecutionMode.LOCAL, null, null))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("sandbox is disabled");
        }

        @Test
        void missingLocalToolFallsBackToSandbox() throws Exception {
            ExecutionRouter routerNoLocalRead = new ExecutionRouter(
                    properties,
                    securityPolicy,
                    sandboxClient,
                    Optional.empty(), // no read tool
                    Optional.of(writeTool),
                    Optional.of(editTool),
                    Optional.of(bashTool),
                    Optional.of(globTool),
                    Optional.of(grepTool));
            properties.setSandboxExecutionEnabled(true);
            when(sandboxClient.isAvailable()).thenReturn(true);
            when(sandboxClient.execute(anyList(), any(ResourceLimits.class))).thenReturn(sbSuccess("sandbox-output"));
            AgentToolResult r =
                    routerNoLocalRead.route("read", Map.of("path", "/tmp/x"), ExecutionMode.LOCAL, null, null);
            assertThat(((TextContent) r.content().get(0)).text()).contains("sandbox-output");
        }
    }

    @Nested
    class ExplicitSandboxMode {

        @Test
        void sandboxDisabledThrows() {
            assertThatThrownBy(() -> router.route("read", Map.of("path", "/tmp"), ExecutionMode.SANDBOX, null, null))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("Sandbox execution is disabled");
        }

        @Test
        void sandboxUnavailableFallsBackToLocal() throws Exception {
            properties.setSandboxExecutionEnabled(true);
            when(sandboxClient.isAvailable()).thenReturn(false);
            when(readTool.execute(any(), any(), any(), any())).thenReturn(result("local-fallback"));
            AgentToolResult r = router.route("read", Map.of("path", "/tmp/x"), ExecutionMode.SANDBOX, null, null);
            assertThat(((TextContent) r.content().get(0)).text()).isEqualTo("local-fallback");
        }

        @Test
        void successfulSandboxExecution() throws Exception {
            properties.setSandboxExecutionEnabled(true);
            when(sandboxClient.isAvailable()).thenReturn(true);
            when(sandboxClient.execute(anyList(), any(ResourceLimits.class))).thenReturn(sbSuccess("sandbox out"));
            AgentToolResult r = router.route("read", Map.of("path", "/tmp/x"), ExecutionMode.SANDBOX, null, null);
            assertThat(((TextContent) r.content().get(0)).text()).contains("sandbox out");
        }

        @Test
        void sandboxResultWithStderrAndExitCode() throws Exception {
            properties.setSandboxExecutionEnabled(true);
            when(sandboxClient.isAvailable()).thenReturn(true);
            SandboxResult sandboxResult = SandboxResult.builder()
                    .stdout("out")
                    .stderr("warning")
                    .exitCode(2)
                    .build();
            when(sandboxClient.execute(anyList(), any(ResourceLimits.class))).thenReturn(sandboxResult);
            AgentToolResult r = router.route("read", Map.of("path", "/tmp/x"), ExecutionMode.SANDBOX, null, null);
            String out = ((TextContent) r.content().get(0)).text();
            assertThat(out)
                    .contains("out")
                    .contains("[stderr]")
                    .contains("warning")
                    .contains("[exit code: 2]");
        }

        @Test
        void sandboxResultWithError() throws Exception {
            properties.setSandboxExecutionEnabled(true);
            when(sandboxClient.isAvailable()).thenReturn(true);
            SandboxResult sandboxResult = SandboxResult.error("docker not running", "stderr-line");
            when(sandboxClient.execute(anyList(), any(ResourceLimits.class))).thenReturn(sandboxResult);
            AgentToolResult r = router.route("bash", Map.of("command", "ls"), ExecutionMode.SANDBOX, null, null);
            String out = ((TextContent) r.content().get(0)).text();
            assertThat(out).contains("Error: docker not running");
        }

        @Test
        void unsupportedToolInSandboxThrows() {
            properties.setSandboxExecutionEnabled(true);
            when(sandboxClient.isAvailable()).thenReturn(true);
            assertThatThrownBy(() -> router.route("custom", Map.of(), ExecutionMode.SANDBOX, null, null))
                    .isInstanceOf(UnsupportedOperationException.class);
        }
    }

    @Nested
    class AutoModeRiskAssessment {

        @org.junit.jupiter.api.BeforeEach
        void enableAutoAsDefault() {
            // ExecutionRouter.determineMode treats explicit AUTO as "fall through to default",
            // so we make AUTO the default to exercise the risk-assessment branch.
            properties.setDefaultMode(ExecutionMode.AUTO);
        }

        @Test
        void lowRiskUsesLocal() throws Exception {
            properties.setSandboxExecutionEnabled(true);
            when(sandboxClient.isAvailable()).thenReturn(true);
            when(readTool.execute(any(), any(), any(), any())).thenReturn(result("ok"));

            // read is LOW risk → local even when sandbox available
            router.route("read", Map.of("path", "/tmp/x"), ExecutionMode.AUTO, null, null);
            verify(readTool).execute(any(), any(), any(), any());
        }

        @Test
        void mediumRiskUsesSandboxWhenAvailable() throws Exception {
            properties.setSandboxExecutionEnabled(true);
            when(sandboxClient.isAvailable()).thenReturn(true);
            when(sandboxClient.execute(anyList(), any(ResourceLimits.class))).thenReturn(sbSuccess("sb"));
            router.route("write", Map.of("path", "/tmp/x", "content", "y"), ExecutionMode.AUTO, null, null);
            verify(sandboxClient).execute(anyList(), any(ResourceLimits.class));
        }

        @Test
        void criticalCommandUsesSandbox() throws Exception {
            properties.setSandboxExecutionEnabled(true);
            when(sandboxClient.isAvailable()).thenReturn(true);
            when(sandboxClient.execute(anyList(), any(ResourceLimits.class))).thenReturn(sbSuccess("safe"));
            router.route("bash", Map.of("command", "rm -rf /"), ExecutionMode.AUTO, null, null);
            verify(sandboxClient).execute(anyList(), any(ResourceLimits.class));
        }

        @Test
        void protectedPathFlaggedAsHighRisk() throws Exception {
            properties.setSandboxExecutionEnabled(true);
            when(sandboxClient.isAvailable()).thenReturn(true);
            when(sandboxClient.execute(anyList(), any(ResourceLimits.class))).thenReturn(sbSuccess("sb"));
            router.route("write", Map.of("path", "/etc/passwd", "content", "x"), ExecutionMode.AUTO, null, null);
            verify(sandboxClient).execute(anyList(), any(ResourceLimits.class));
        }

        @Test
        void criticalWithoutSandboxFallsToLocalWithSafetyCheck() {
            // Sandbox disabled — dangerous command should fail safety check
            assertThatThrownBy(
                            () -> router.route("bash", Map.of("command", "rm -rf /"), ExecutionMode.AUTO, null, null))
                    .isInstanceOf(SecurityException.class);
        }

        @Test
        void protectedPathFallsToSafetyCheckWhenSandboxOff() {
            assertThatThrownBy(() -> router.route(
                            "write", Map.of("path", "/etc/passwd", "content", "x"), ExecutionMode.AUTO, null, null))
                    .isInstanceOf(SecurityException.class);
        }
    }

    @Nested
    class ParameterOverride {

        @Test
        void modeFromParamRespectedWhenExplicitNull() throws Exception {
            properties.setSandboxExecutionEnabled(true);
            when(sandboxClient.isAvailable()).thenReturn(true);
            when(sandboxClient.execute(anyList(), any(ResourceLimits.class))).thenReturn(sbSuccess("sb"));

            // Explicit null, but _executionMode in params says sandbox
            router.route("read", Map.of("path", "/tmp/x", "_executionMode", "sandbox"), null, null, null);
            verify(sandboxClient).execute(anyList(), any(ResourceLimits.class));
        }

        @Test
        void invalidModeInParamFallsBackToDefault() throws Exception {
            // properties.defaultMode = LOCAL
            when(readTool.execute(any(), any(), any(), any())).thenReturn(result("ok"));
            router.route("read", Map.of("path", "/tmp/x", "_executionMode", "bogus"), null, null, null);
            verify(readTool).execute(any(), any(), any(), any());
        }

        @Test
        void autoExplicitTriggersRiskAssessment() throws Exception {
            properties.setSandboxExecutionEnabled(true);
            when(sandboxClient.isAvailable()).thenReturn(true);
            when(readTool.execute(any(), any(), any(), any())).thenReturn(result("ok"));
            router.route("read", Map.of("path", "/tmp/x"), ExecutionMode.AUTO, null, null);

            // Read is LOW risk → local
            verify(readTool).execute(any(), any(), any(), any());
        }
    }
}
