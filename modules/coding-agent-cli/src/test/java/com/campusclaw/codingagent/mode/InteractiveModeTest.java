package com.campusclaw.codingagent.mode;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.List;
import java.util.concurrent.CompletableFuture;

import com.campusclaw.agent.Agent;
import com.campusclaw.agent.event.*;
import com.campusclaw.agent.state.AgentState;
import com.campusclaw.ai.stream.AssistantMessageEvent;
import com.campusclaw.ai.types.*;
import com.campusclaw.codingagent.command.SlashCommandRegistry;
import com.campusclaw.codingagent.command.builtin.HelpCommand;
import com.campusclaw.codingagent.command.builtin.QuitCommand;
import com.campusclaw.codingagent.session.AgentSession;
import com.campusclaw.codingagent.skill.SkillRegistry;
import com.campusclaw.codingagent.tool.bash.BashExecutor;
import com.campusclaw.tui.terminal.TestTerminal;

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
class InteractiveModeTest {

    @Mock AgentSession session;
    @Mock Agent agent;
    @Mock BashExecutor bashExecutor;

    TestTerminal terminal;
    AgentState state;
    SlashCommandRegistry registry;
    InteractiveMode mode;

    @BeforeEach
    void setUp() {
        terminal = new TestTerminal(80, 24);
        state = new AgentState();
        registry = new SlashCommandRegistry();
        registry.register(new HelpCommand(registry));
        registry.register(new QuitCommand());
        mode = new InteractiveMode(registry, bashExecutor, null, null, null, null, null);

        when(session.getAgent()).thenReturn(agent);
        when(agent.getState()).thenReturn(state);
        when(session.getSkillRegistry()).thenReturn(new SkillRegistry());
        when(session.getPromptTemplates()).thenReturn(List.of());
    }

    // -------------------------------------------------------------------
    // Event handling (unit tests — no TUI needed)
    // -------------------------------------------------------------------

    @Nested
    class EventHandling {

        @Test
        void textDeltaUpdatesAssistantComponent() {
            var partial = new AssistantMessage(
                    List.of(new TextContent("Hello", null)),
                    "messages", "anthropic", "model",
                    null, Usage.empty(), null, null, 1L
            );
            var delta = new AssistantMessageEvent.TextDeltaEvent(0, "Hello", partial);
            var event = new MessageUpdateEvent(partial, delta);

            var component = new com.campusclaw.codingagent.mode.tui.AssistantMessageComponent();
            component.appendText("Hello");
            assertTrue(component.hasContent());

            var lines = component.render(80);
            assertTrue(lines.stream().anyMatch(l -> l.contains("Hello")));
        }

        @Test
        void thinkingDeltaRendersInItalic() {
            var component = new com.campusclaw.codingagent.mode.tui.AssistantMessageComponent();
            component.appendThinking("Let me think...");

            var lines = component.render(80);
            String output = String.join("\n", lines);
            assertTrue(output.contains("Let me think"));
            assertTrue(output.contains("\033[3m")); // italic
        }

        @Test
        void toolStatusShowsRunningThenDone() {
            var tool = new com.campusclaw.codingagent.mode.tui.ToolStatusComponent("bash");
            var running = tool.render(80);
            String runningOutput = String.join("", running);
            // Tool shows bold name on pending bg
            assertTrue(runningOutput.contains("bash"));

            tool.setComplete(false);
            var done = tool.render(80);
            String doneOutput = String.join("", done);
            // Tool shows bold name on success bg
            assertTrue(doneOutput.contains("bash"));
        }

        @Test
        void toolStatusShowsFailed() {
            var tool = new com.campusclaw.codingagent.mode.tui.ToolStatusComponent("bash");
            tool.setComplete(true);
            var lines = tool.render(80);
            String output = String.join("", lines);
            // Tool shows bold name on error bg
            assertTrue(output.contains("bash"));
            assertTrue(output.contains("\033[48;2;60;40;40m")); // error bg
        }
    }

    // -------------------------------------------------------------------
    // Footer component
    // -------------------------------------------------------------------

    @Nested
    class FooterTests {

        @Test
        void rendersModelInfo() {
            var footer = new com.campusclaw.codingagent.mode.tui.FooterComponent();
            footer.setModel("zai", "glm-5", 200000, false);
            var lines = footer.render(80);

            String output = String.join("\n", lines);
            assertTrue(output.contains("glm-5"));
            assertTrue(output.contains("zai"));
        }

        @Test
        void rendersTokenStats() {
            var footer = new com.campusclaw.codingagent.mode.tui.FooterComponent();
            footer.setModel("zai", "glm-5", 200000, false);
            footer.updateUsage(1500, 200, 0, 0, 0.001);
            var lines = footer.render(80);

            String output = String.join("\n", lines);
            assertTrue(output.contains("1.5k")); // input tokens
            assertTrue(output.contains("200")); // output tokens
        }

        @Test
        void rendersPwdAndStatsLines() {
            var footer = new com.campusclaw.codingagent.mode.tui.FooterComponent();
            footer.setModel("zai", "glm-5", 200000, false);
            footer.setCwd("/Users/z/project");
            var lines = footer.render(80);
            assertEquals(2, lines.size()); // pwd + stats
            assertTrue(lines.get(0).contains("~")); // pwd with ~ substitution
        }

        @Test
        void contextPercentageColorCoding() {
            var footer = new com.campusclaw.codingagent.mode.tui.FooterComponent();
            footer.setModel("zai", "glm-5", 1000, false);
            // 95% usage — should be red
            footer.updateUsage(950, 0, 0, 0, 0);
            var lines = footer.render(120);
            String output = String.join("\n", lines);
            assertTrue(output.contains("\033[31m")); // red
        }

        @Test
        void tokenFormattingMillions() {
            assertEquals("1.5M", com.campusclaw.codingagent.mode.tui.FooterComponent.formatTokens(1500000));
            assertEquals("15M", com.campusclaw.codingagent.mode.tui.FooterComponent.formatTokens(15000000));
            assertEquals("200k", com.campusclaw.codingagent.mode.tui.FooterComponent.formatTokens(200000));
            assertEquals("1.5k", com.campusclaw.codingagent.mode.tui.FooterComponent.formatTokens(1500));
            assertEquals("500", com.campusclaw.codingagent.mode.tui.FooterComponent.formatTokens(500));
        }
    }

    // -------------------------------------------------------------------
    // Bash execution component
    // -------------------------------------------------------------------

    @Nested
    class BashExecutionTests {

        @Test
        void rendersCommandAndOutput() {
            var comp = new com.campusclaw.codingagent.mode.tui.BashExecutionComponent("ls -la", false);
            comp.setResult("file1.txt\nfile2.txt", 0);
            var lines = comp.render(80);
            String output = String.join("\n", lines);
            String stripped = output.replaceAll("\033\\[[;\\d]*[a-zA-Z]", "");
            assertTrue(stripped.contains("$ ls -la"));
            assertTrue(stripped.contains("file1.txt"));
        }

        @Test
        void excludedCommandShowsDollarDollar() {
            var comp = new com.campusclaw.codingagent.mode.tui.BashExecutionComponent("pwd", true);
            comp.setResult("/home/user", 0);
            var lines = comp.render(80);
            String output = String.join("\n", lines);
            String stripped = output.replaceAll("\033\\[[;\\d]*[a-zA-Z]", "");
            assertTrue(stripped.contains("$$ pwd"));
            assertTrue(stripped.contains("no context"));
        }

        @Test
        void showsExitCodeOnError() {
            var comp = new com.campusclaw.codingagent.mode.tui.BashExecutionComponent("bad-cmd", false);
            comp.setResult("command not found", 127);
            var lines = comp.render(80);
            String output = String.join("\n", lines);
            assertTrue(output.contains("(exit 127)"));
        }

        @Test
        void showsRunningWhenIncomplete() {
            var comp = new com.campusclaw.codingagent.mode.tui.BashExecutionComponent("sleep 10", false);
            var lines = comp.render(80);
            String output = String.join("\n", lines);
            // BashExecutionComponent shows "running..." in gray when incomplete
            String stripped = output.replaceAll("\033\\[[;\\d]*[a-zA-Z]", "");
            assertTrue(stripped.contains("running..."));
        }
    }

    // -------------------------------------------------------------------
    // REPL integration (full run with TUI)
    // -------------------------------------------------------------------

    @Nested
    class ReplIntegration {

        @Test
        void ctrlDExitsCleanly() {
            var thread = new Thread(() -> {
                sleep(200);
                terminal.simulateInput("\u0004");
            });
            thread.start();

            mode.run(session, terminal);
            assertFalse(terminal.getFullOutput().contains("Error"));
        }

        @Test
        void showsWelcomeMessage() {
            var thread = new Thread(() -> {
                sleep(200);
                terminal.simulateInput("\u0004");
            });
            thread.start();

            mode.run(session, terminal);
            // Welcome text may scroll off in small terminal; check for content that's visible
            String output = terminal.getFullOutput();
            assertTrue(output.contains("CampusClaw can explain") || output.contains("v0.1.0"));
        }

        @Test
        void welcomeMessageShowsBashHints() {
            var thread = new Thread(() -> {
                sleep(200);
                terminal.simulateInput("\u0004");
            });
            thread.start();

            mode.run(session, terminal);
            String output = terminal.getFullOutput();
            assertTrue(output.contains("run bash"));
        }

        @Test
        void slashHelpShowsCommands() {
            var thread = new Thread(() -> {
                sleep(200);
                typeChars("/help");
                terminal.simulateInput("\r");
                sleep(200);
                terminal.simulateInput("\u0004");
            });
            thread.start();

            mode.run(session, terminal);
            assertTrue(terminal.getFullOutput().contains("Available commands"));
        }

        @Test
        void promptIsSentToSession() {
            when(session.prompt("hello"))
                    .thenReturn(CompletableFuture.completedFuture(null));

            var thread = new Thread(() -> {
                sleep(200);
                typeChars("hello");
                terminal.simulateInput("\r");
                sleep(500);
                terminal.simulateInput("\u0004");
            });
            thread.start();

            mode.run(session, terminal);
            verify(session).prompt("hello");
        }
    }

    // -------------------------------------------------------------------
    // Input validation
    // -------------------------------------------------------------------

    @Nested
    class InputValidation {

        @Test
        void throwsOnNullSession() {
            assertThrows(NullPointerException.class, () -> mode.run(null, terminal));
        }

        @Test
        void throwsOnNullTerminal() {
            assertThrows(NullPointerException.class, () -> mode.run(session, null));
        }

        @Test
        void throwsOnNullRegistry() {
            assertThrows(NullPointerException.class, () -> new InteractiveMode(null, null, null, null, null, null, null));
        }
    }

    // -------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------

    private void typeChars(String text) {
        for (char c : text.toCharArray()) {
            terminal.simulateInput(String.valueOf(c));
            sleep(5);
        }
    }

    private static void sleep(long ms) {
        try { Thread.sleep(ms); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
    }
}
