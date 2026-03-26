package com.mariozechner.pi.codingagent.mode;

import com.mariozechner.pi.agent.Agent;
import com.mariozechner.pi.agent.event.*;
import com.mariozechner.pi.agent.state.AgentState;
import com.mariozechner.pi.ai.stream.AssistantMessageEvent;
import com.mariozechner.pi.ai.types.*;
import com.mariozechner.pi.codingagent.session.AgentSession;
import com.mariozechner.pi.tui.terminal.TestTerminal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class InteractiveModeTest {

    @Mock AgentSession session;
    @Mock Agent agent;

    TestTerminal terminal;
    AgentState state;
    InteractiveMode mode;

    @BeforeEach
    void setUp() {
        terminal = new TestTerminal(80, 24);
        state = new AgentState();
        mode = new InteractiveMode();

        when(session.getAgent()).thenReturn(agent);
        when(agent.getState()).thenReturn(state);
    }

    // -------------------------------------------------------------------
    // Input reading
    // -------------------------------------------------------------------

    @Nested
    class ReadInput {

        @Test
        void readsInputUntilEnter() {
            // Schedule input after the listener is registered
            var thread = new Thread(() -> {
                sleep(50);
                terminal.simulateInput("hello\n");
            });
            thread.start();

            String result = mode.readInput(terminal);

            assertEquals("hello", result);
        }

        @Test
        void handlesBackspace() {
            var thread = new Thread(() -> {
                sleep(50);
                terminal.simulateInput("helloo");
                sleep(20);
                terminal.simulateInput("\u007f"); // backspace
                sleep(20);
                terminal.simulateInput("\n");
            });
            thread.start();

            String result = mode.readInput(terminal);

            assertEquals("hello", result);
        }

        @Test
        void ctrlCReturnsEmpty() {
            var thread = new Thread(() -> {
                sleep(50);
                terminal.simulateInput("partial");
                sleep(20);
                terminal.simulateInput("\u0003"); // Ctrl+C
            });
            thread.start();

            String result = mode.readInput(terminal);

            assertEquals("", result);
        }

        @Test
        void ctrlDReturnsNull() {
            var thread = new Thread(() -> {
                sleep(50);
                terminal.simulateInput("\u0004"); // Ctrl+D
            });
            thread.start();

            String result = mode.readInput(terminal);

            assertNull(result);
        }

        @Test
        void showsPromptPrefix() {
            var thread = new Thread(() -> {
                sleep(50);
                terminal.simulateInput("\n");
            });
            thread.start();

            mode.readInput(terminal);

            assertTrue(terminal.getFullOutput().contains("> "));
        }
    }

    // -------------------------------------------------------------------
    // REPL lifecycle
    // -------------------------------------------------------------------

    @Nested
    class ReplLifecycle {

        @Test
        void exitCommandStopsLoop() {
            var thread = new Thread(() -> {
                sleep(50);
                terminal.simulateInput("/exit\n");
            });
            thread.start();

            mode.run(session, terminal);

            assertTrue(terminal.getFullOutput().contains("Goodbye"));
        }

        @Test
        void ctrlDStopsLoop() {
            var thread = new Thread(() -> {
                sleep(50);
                terminal.simulateInput("\u0004"); // Ctrl+D = EOF
            });
            thread.start();

            mode.run(session, terminal);

            // Should exit cleanly without error
            assertFalse(terminal.getFullOutput().contains("Error"));
        }

        @Test
        void showsWelcomeMessage() {
            var thread = new Thread(() -> {
                sleep(50);
                terminal.simulateInput("/exit\n");
            });
            thread.start();

            mode.run(session, terminal);

            assertTrue(terminal.getFullOutput().contains("Pi Coding Agent"));
        }

        @Test
        void entersAndExitsRawMode() {
            var thread = new Thread(() -> {
                sleep(50);
                terminal.simulateInput("/exit\n");
            });
            thread.start();

            mode.run(session, terminal);

            // After run completes, raw mode should be exited
            assertFalse(terminal.isRawMode());
        }

        @Test
        void emptyInputIsIgnored() {
            when(session.prompt(anyString())).thenReturn(CompletableFuture.completedFuture(null));
            when(session.getHistory()).thenReturn(List.of());

            var thread = new Thread(() -> {
                sleep(50);
                terminal.simulateInput("\n"); // empty input
                sleep(30);
                terminal.simulateInput("/exit\n");
            });
            thread.start();

            mode.run(session, terminal);

            verify(session, never()).prompt(anyString());
        }
    }

    // -------------------------------------------------------------------
    // Prompt execution
    // -------------------------------------------------------------------

    @Nested
    class PromptExecution {

        @Test
        void sendsInputToSession() {
            when(session.prompt("build the project"))
                    .thenReturn(CompletableFuture.completedFuture(null));

            var thread = new Thread(() -> {
                sleep(50);
                terminal.simulateInput("build the project\n");
                sleep(100);
                terminal.simulateInput("/exit\n");
            });
            thread.start();

            mode.run(session, terminal);

            verify(session).prompt("build the project");
        }

        @Test
        void displaysErrorOnFailure() {
            CompletableFuture<Void> failed = new CompletableFuture<>();
            failed.completeExceptionally(new RuntimeException("connection failed"));
            when(session.prompt("test")).thenReturn(failed);

            var thread = new Thread(() -> {
                sleep(50);
                terminal.simulateInput("test\n");
                sleep(100);
                terminal.simulateInput("/exit\n");
            });
            thread.start();

            mode.run(session, terminal);

            assertTrue(terminal.getFullOutput().contains("Error"));
        }
    }

    // -------------------------------------------------------------------
    // Event handling
    // -------------------------------------------------------------------

    @Nested
    class EventHandling {

        @Test
        void textDeltaIsWrittenToTerminal() {
            var partial = new AssistantMessage(
                    List.of(new TextContent("Hello", null)),
                    "messages", "anthropic", "model",
                    null, Usage.empty(), null, null, 1L
            );
            var delta = new AssistantMessageEvent.TextDeltaEvent(0, "Hello", partial);
            var event = new MessageUpdateEvent(partial, delta);

            mode.handleEvent(event, terminal);

            assertTrue(terminal.getFullOutput().contains("Hello"));
        }

        @Test
        void toolStartShowsToolName() {
            var event = new ToolExecutionStartEvent("tc-1", "bash", Map.of("command", "ls"));

            mode.handleEvent(event, terminal);

            String output = terminal.getFullOutput();
            assertTrue(output.contains("[bash]"));
            assertTrue(output.contains("running"));
        }

        @Test
        void toolEndShowsDone() {
            var event = new ToolExecutionEndEvent("tc-1", "bash", null, false);

            mode.handleEvent(event, terminal);

            assertTrue(terminal.getFullOutput().contains("done"));
        }

        @Test
        void toolEndShowsFailedOnError() {
            var event = new ToolExecutionEndEvent("tc-1", "bash", null, true);

            mode.handleEvent(event, terminal);

            assertTrue(terminal.getFullOutput().contains("failed"));
        }

        @Test
        void unrecognizedEventIsIgnored() {
            var event = new TurnStartEvent();

            assertDoesNotThrow(() -> mode.handleEvent(event, terminal));
        }
    }

    // -------------------------------------------------------------------
    // Ctrl+C abort
    // -------------------------------------------------------------------

    @Nested
    class AbortHandling {

        @Test
        void ctrlCDuringExecutionCallsAbort() {
            // Capture the event listener
            var listenerRef = new AtomicReference<AgentEventListener>();
            when(agent.subscribe(any())).thenAnswer(invocation -> {
                listenerRef.set(invocation.getArgument(0));
                return (Runnable) () -> {};
            });

            // Create a slow-completing future
            CompletableFuture<Void> slowFuture = new CompletableFuture<>();
            when(session.prompt("long task")).thenReturn(slowFuture);

            var thread = new Thread(() -> {
                sleep(50);
                terminal.simulateInput("long task\n");
                sleep(100);
                // Send Ctrl+C during execution
                terminal.simulateInput("\u0003");
                sleep(50);
                // Complete the future after abort
                slowFuture.completeExceptionally(new RuntimeException("aborted"));
            });
            thread.start();

            // Run in a separate thread so we don't block
            var modeThread = new Thread(() -> {
                // Run just the prompt execution, not the full REPL
                mode.executePrompt(session, terminal, "long task");
            });
            modeThread.start();
            try {
                modeThread.join(3000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }

            verify(session).abort();
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
                    () -> mode.run(null, terminal));
        }

        @Test
        void throwsOnNullTerminal() {
            assertThrows(NullPointerException.class,
                    () -> mode.run(session, null));
        }
    }

    // -------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------

    private static void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
