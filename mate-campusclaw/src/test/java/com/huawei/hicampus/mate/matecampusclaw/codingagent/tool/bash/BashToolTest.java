package com.huawei.hicampus.mate.matecampusclaw.codingagent.tool.bash;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;

import com.huawei.hicampus.mate.matecampusclaw.agent.tool.AgentToolResult;
import com.huawei.hicampus.mate.matecampusclaw.agent.tool.CancellationToken;
import com.huawei.hicampus.mate.matecampusclaw.ai.types.TextContent;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class BashToolTest {

    @Mock
    BashExecutor bashExecutor;

    @TempDir
    Path tempDir;

    BashTool bashTool;

    @BeforeEach
    void setUp() {
        bashTool = new BashTool(bashExecutor, tempDir);
    }

    private String extractText(AgentToolResult result) {
        return ((TextContent) result.content().get(0)).text();
    }

    // -------------------------------------------------------------------
    // Tool metadata
    // -------------------------------------------------------------------

    @Nested
    class Metadata {

        @Test
        void name() {
            assertEquals("bash", bashTool.name());
        }

        @Test
        void label() {
            assertEquals("Bash", bashTool.label());
        }

        @Test
        void parametersSchema() {
            var params = bashTool.parameters();
            assertEquals("object", params.get("type").asText());
            assertTrue(params.get("properties").has("command"));
            assertTrue(params.get("properties").has("timeout"));
            assertEquals("command", params.get("required").get(0).asText());
        }
    }

    // -------------------------------------------------------------------
    // Normal execution
    // -------------------------------------------------------------------

    @Nested
    class NormalExecution {

        @Test
        void returnsStdoutAsText() throws Exception {
            when(bashExecutor.execute(eq("echo hello"), eq(tempDir), any()))
                    .thenReturn(new BashExecutionResult(0, "hello\n", ""));

            var result = bashTool.execute("call-1", Map.of("command", "echo hello"), null, null);

            assertEquals("hello\n", extractText(result));
            assertNull(((BashToolDetails) result.details()).truncation());
        }

        @Test
        void combinesStdoutAndStderr() throws Exception {
            when(bashExecutor.execute(any(), any(), any()))
                    .thenReturn(new BashExecutionResult(0, "out\n", "err\n"));

            var result = bashTool.execute("call-2", Map.of("command", "cmd"), null, null);

            String text = extractText(result);
            assertTrue(text.contains("out"));
            assertTrue(text.contains("err"));
        }

        @Test
        void delegatesToBashExecutorWithCorrectCwd() throws Exception {
            when(bashExecutor.execute(any(), any(), any()))
                    .thenReturn(new BashExecutionResult(0, "", ""));

            bashTool.execute("call-3", Map.of("command", "test"), null, null);

            verify(bashExecutor).execute(eq("test"), eq(tempDir), any());
        }

        @Test
        void passesDefaultTimeout() throws Exception {
            when(bashExecutor.execute(any(), any(), any()))
                    .thenReturn(new BashExecutionResult(0, "", ""));

            bashTool.execute("call-4", Map.of("command", "test"), null, null);

            var captor = ArgumentCaptor.forClass(BashExecutorOptions.class);
            verify(bashExecutor).execute(any(), any(), captor.capture());
            assertNull(captor.getValue().timeout(), "Default should be no timeout");
        }

        @Test
        void passesCustomTimeout() throws Exception {
            when(bashExecutor.execute(any(), any(), any()))
                    .thenReturn(new BashExecutionResult(0, "", ""));

            bashTool.execute("call-5", Map.of("command", "test", "timeout", 30), null, null);

            var captor = ArgumentCaptor.forClass(BashExecutorOptions.class);
            verify(bashExecutor).execute(any(), any(), captor.capture());
            assertEquals(Duration.ofSeconds(30), captor.getValue().timeout());
        }

        @Test
        void passesCancellationToken() throws Exception {
            var token = new CancellationToken();
            when(bashExecutor.execute(any(), any(), any()))
                    .thenReturn(new BashExecutionResult(0, "", ""));

            bashTool.execute("call-6", Map.of("command", "test"), token, null);

            var captor = ArgumentCaptor.forClass(BashExecutorOptions.class);
            verify(bashExecutor).execute(any(), any(), captor.capture());
            assertSame(token, captor.getValue().signal());
        }
    }

    // -------------------------------------------------------------------
    // Non-zero exit code
    // -------------------------------------------------------------------

    @Nested
    class NonZeroExitCode {

        @Test
        void includesExitCodeInOutput() throws Exception {
            when(bashExecutor.execute(any(), any(), any()))
                    .thenReturn(new BashExecutionResult(1, "output\n", ""));

            var result = bashTool.execute("call-7", Map.of("command", "false"), null, null);

            String text = extractText(result);
            assertTrue(text.contains("output"));
            assertTrue(text.contains("[exit code: 1]"));
        }

        @Test
        void includesStderrAndExitCode() throws Exception {
            when(bashExecutor.execute(any(), any(), any()))
                    .thenReturn(new BashExecutionResult(2, "", "error msg\n"));

            var result = bashTool.execute("call-8", Map.of("command", "bad"), null, null);

            String text = extractText(result);
            assertTrue(text.contains("error msg"));
            assertTrue(text.contains("[exit code: 2]"));
        }
    }

    // -------------------------------------------------------------------
    // Timeout / cancellation
    // -------------------------------------------------------------------

    @Nested
    class TimeoutAndCancellation {

        @Test
        void timeoutShowsMessage() throws Exception {
            when(bashExecutor.execute(any(), any(), any()))
                    .thenReturn(new BashExecutionResult(null, "partial\n", ""));

            var result = bashTool.execute("call-9", Map.of("command", "sleep 999"), null, null);

            String text = extractText(result);
            assertTrue(text.contains("partial"));
            assertTrue(text.contains("[process timed out or was cancelled]"));
        }
    }

    // -------------------------------------------------------------------
    // Output truncation
    // -------------------------------------------------------------------

    @Nested
    class Truncation {

        @Test
        void largeOutputIsTruncated() throws Exception {
            // Generate output exceeding MAX_OUTPUT_LINES
            var sb = new StringBuilder();
            for (int i = 0; i < BashTool.MAX_OUTPUT_LINES + 500; i++) {
                sb.append("line ").append(i).append('\n');
            }
            when(bashExecutor.execute(any(), any(), any()))
                    .thenReturn(new BashExecutionResult(0, sb.toString(), ""));

            var result = bashTool.execute("call-10", Map.of("command", "gen"), null, null);

            var details = (BashToolDetails) result.details();
            assertNotNull(details.truncation());
            assertTrue(details.truncation().truncated());
            assertNotNull(details.fullOutputPath());

            // The displayed text should have fewer lines than the original
            String text = extractText(result);
            long displayedLines = text.chars().filter(c -> c == '\n').count();
            assertTrue(displayedLines <= BashTool.MAX_OUTPUT_LINES);
        }

        @Test
        void smallOutputIsNotTruncated() throws Exception {
            when(bashExecutor.execute(any(), any(), any()))
                    .thenReturn(new BashExecutionResult(0, "small\n", ""));

            var result = bashTool.execute("call-11", Map.of("command", "echo small"), null, null);

            var details = (BashToolDetails) result.details();
            assertNull(details.truncation());
            assertNull(details.fullOutputPath());
        }
    }

    // -------------------------------------------------------------------
    // Error handling
    // -------------------------------------------------------------------

    @Nested
    class ErrorHandling {

        @Test
        void missingCommandReturnsError() throws Exception {
            var result = bashTool.execute("call-12", Map.of(), null, null);
            assertTrue(extractText(result).contains("Error"));
        }

        @Test
        void blankCommandReturnsError() throws Exception {
            var result = bashTool.execute("call-13", Map.of("command", "  "), null, null);
            assertTrue(extractText(result).contains("Error"));
        }

        @Test
        void ioExceptionReturnsError() throws Exception {
            when(bashExecutor.execute(any(), any(), any()))
                    .thenThrow(new IOException("process failed"));

            var result = bashTool.execute("call-14", Map.of("command", "bad"), null, null);
            assertTrue(extractText(result).contains("Error executing command"));
            assertTrue(extractText(result).contains("process failed"));
        }
    }
}
