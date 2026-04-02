package com.huawei.hicampus.mate.matecampusclaw.codingagent.mode;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import com.huawei.hicampus.mate.matecampusclaw.agent.Agent;
import com.huawei.hicampus.mate.matecampusclaw.agent.state.AgentState;
import com.huawei.hicampus.mate.matecampusclaw.ai.types.*;
import com.huawei.hicampus.mate.matecampusclaw.codingagent.session.AgentSession;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class OneShotModeTest {

    @Mock AgentSession session;
    @Mock Agent agent;

    ByteArrayOutputStream outBytes;
    ByteArrayOutputStream errBytes;
    PrintStream out;
    PrintStream err;
    AgentState state;
    OneShotMode mode;

    @BeforeEach
    void setUp() {
        outBytes = new ByteArrayOutputStream();
        errBytes = new ByteArrayOutputStream();
        out = new PrintStream(outBytes, true, StandardCharsets.UTF_8);
        err = new PrintStream(errBytes, true, StandardCharsets.UTF_8);
        state = new AgentState();
        mode = new OneShotMode(out, err);

        when(session.getAgent()).thenReturn(agent);
        when(agent.getState()).thenReturn(state);
        when(session.subscribe(any())).thenReturn(() -> {});
    }

    private String stdout() {
        out.flush();
        return outBytes.toString(StandardCharsets.UTF_8);
    }

    private String stderr() {
        err.flush();
        return errBytes.toString(StandardCharsets.UTF_8);
    }

    private AssistantMessage assistantMsg(String text, StopReason reason) {
        return new AssistantMessage(
                List.of(new TextContent(text, null)),
                "messages", "anthropic", "model",
                null, Usage.empty(), reason, null, 1000L
        );
    }

    private AssistantMessage assistantErrorMsg(String errorMessage) {
        return new AssistantMessage(
                List.of(), "messages", "anthropic", "model",
                null, Usage.empty(), StopReason.ERROR, errorMessage, 1000L
        );
    }

    // -------------------------------------------------------------------
    // Successful execution
    // -------------------------------------------------------------------

    @Nested
    class SuccessfulExecution {

        @Test
        void returnsZeroOnSuccess() {
            when(session.prompt("hello")).thenReturn(CompletableFuture.completedFuture(null));
            when(session.getHistory()).thenReturn(List.of(
                    new UserMessage("hello", 1L),
                    assistantMsg("Hi there!", StopReason.STOP)
            ));

            int exitCode = mode.run(session, "hello");

            assertEquals(0, exitCode);
        }

        @Test
        void printsAssistantTextToStdout() {
            when(session.prompt("hello")).thenReturn(CompletableFuture.completedFuture(null));
            when(session.getHistory()).thenReturn(List.of(
                    new UserMessage("hello", 1L),
                    assistantMsg("Hello, world!", StopReason.STOP)
            ));

            mode.run(session, "hello");

            assertTrue(stdout().contains("Hello, world!"));
        }

        @Test
        void printsMultipleTextBlocks() {
            AssistantMessage msg = new AssistantMessage(
                    List.of(
                            new TextContent("Part one. ", null),
                            new TextContent("Part two.", null)
                    ),
                    "messages", "anthropic", "model",
                    null, Usage.empty(), StopReason.STOP, null, 1L
            );
            when(session.prompt("test")).thenReturn(CompletableFuture.completedFuture(null));
            when(session.getHistory()).thenReturn(List.of(
                    new UserMessage("test", 1L), msg
            ));

            mode.run(session, "test");

            assertTrue(stdout().contains("Part one. Part two."));
        }

        @Test
        void skipsNonTextContentBlocks() {
            AssistantMessage msg = new AssistantMessage(
                    List.of(
                            new TextContent("visible", null),
                            new ToolCall("tc-1", "bash", Map.of("command", "ls"), null)
                    ),
                    "messages", "anthropic", "model",
                    null, Usage.empty(), StopReason.TOOL_USE, null, 1L
            );
            when(session.prompt("test")).thenReturn(CompletableFuture.completedFuture(null));
            when(session.getHistory()).thenReturn(List.of(
                    new UserMessage("test", 1L), msg
            ));

            mode.run(session, "test");

            assertTrue(stdout().contains("visible"));
            assertFalse(stdout().contains("bash"));
        }

        @Test
        void usesLastAssistantMessage() {
            when(session.prompt("test")).thenReturn(CompletableFuture.completedFuture(null));
            when(session.getHistory()).thenReturn(List.of(
                    new UserMessage("test", 1L),
                    assistantMsg("first response", StopReason.TOOL_USE),
                    new ToolResultMessage("tc-1", "bash",
                            List.of(new TextContent("ok")), null, false, 2L),
                    assistantMsg("final response", StopReason.STOP)
            ));

            mode.run(session, "test");

            assertTrue(stdout().contains("final response"));
            assertFalse(stdout().contains("first response"));
        }

        @Test
        void returnsZeroWhenNoAssistantMessage() {
            when(session.prompt("test")).thenReturn(CompletableFuture.completedFuture(null));
            when(session.getHistory()).thenReturn(List.of(
                    new UserMessage("test", 1L)
            ));

            int exitCode = mode.run(session, "test");

            assertEquals(0, exitCode);
        }
    }

    // -------------------------------------------------------------------
    // Error handling
    // -------------------------------------------------------------------

    @Nested
    class ErrorHandling {

        @Test
        void returnsOneWhenFutureFails() {
            CompletableFuture<Void> failed = new CompletableFuture<>();
            failed.completeExceptionally(new RuntimeException("LLM unreachable"));
            when(session.prompt("test")).thenReturn(failed);
            state.setError("LLM unreachable");

            int exitCode = mode.run(session, "test");

            assertEquals(1, exitCode);
            assertTrue(stderr().contains("LLM unreachable"));
        }

        @Test
        void returnsOneWhenAgentStateHasError() {
            when(session.prompt("test")).thenReturn(CompletableFuture.completedFuture(null));
            state.setError("Something went wrong");

            int exitCode = mode.run(session, "test");

            assertEquals(1, exitCode);
            assertTrue(stderr().contains("Something went wrong"));
        }

        @Test
        void returnsOneWhenStopReasonIsError() {
            when(session.prompt("test")).thenReturn(CompletableFuture.completedFuture(null));
            when(session.getHistory()).thenReturn(List.of(
                    new UserMessage("test", 1L),
                    assistantErrorMsg("model error occurred")
            ));

            int exitCode = mode.run(session, "test");

            assertEquals(1, exitCode);
            assertTrue(stderr().contains("model error occurred"));
        }

        @Test
        void returnsOneWithDefaultMessageWhenErrorMessageNull() {
            when(session.prompt("test")).thenReturn(CompletableFuture.completedFuture(null));
            when(session.getHistory()).thenReturn(List.of(
                    new UserMessage("test", 1L),
                    assistantErrorMsg(null)
            ));

            int exitCode = mode.run(session, "test");

            assertEquals(1, exitCode);
            assertTrue(stderr().contains("agent stopped with error"));
        }

        @Test
        void usesExceptionMessageWhenStateErrorNull() {
            CompletableFuture<Void> failed = new CompletableFuture<>();
            failed.completeExceptionally(new RuntimeException("connection timeout"));
            when(session.prompt("test")).thenReturn(failed);

            int exitCode = mode.run(session, "test");

            assertEquals(1, exitCode);
            assertTrue(stderr().contains("connection timeout"));
        }

        @Test
        void nothingWrittenToStdoutOnError() {
            CompletableFuture<Void> failed = new CompletableFuture<>();
            failed.completeExceptionally(new RuntimeException("fail"));
            when(session.prompt("test")).thenReturn(failed);

            mode.run(session, "test");

            assertEquals("", stdout());
        }
    }

    // -------------------------------------------------------------------
    // Input validation
    // -------------------------------------------------------------------

    @Nested
    class InputValidation {

        @Test
        void throwsOnNullSession() {
            assertThrows(NullPointerException.class,
                    () -> mode.run(null, "prompt"));
        }

        @Test
        void throwsOnNullPrompt() {
            assertThrows(NullPointerException.class,
                    () -> mode.run(session, null));
        }
    }

    // -------------------------------------------------------------------
    // Prompt delegation
    // -------------------------------------------------------------------

    @Nested
    class PromptDelegation {

        @Test
        void passesPromptToSession() {
            when(session.prompt("my specific prompt"))
                    .thenReturn(CompletableFuture.completedFuture(null));
            when(session.getHistory()).thenReturn(List.of());

            mode.run(session, "my specific prompt");

            verify(session).prompt("my specific prompt");
        }
    }
}
