package com.campusclaw.codingagent.cli;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Path;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import picocli.CommandLine;

class CampusClawCommandTest {

    private CampusClawCommand parse(String... args) {
        CampusClawCommand cmd = new CampusClawCommand(null, null, null, null, null, null, null, null, null);
        new CommandLine(cmd).parseArgs(args);
        return cmd;
    }

    // -------------------------------------------------------------------
    // Model option
    // -------------------------------------------------------------------

    @Nested
    class ModelOption {

        @Test
        void shortFlag() {
            CampusClawCommand cmd = parse("-m", "claude-sonnet-4-20250514");
            assertEquals("claude-sonnet-4-20250514", cmd.getModel());
        }

        @Test
        void longFlag() {
            CampusClawCommand cmd = parse("--model", "claude-opus-4-20250514");
            assertEquals("claude-opus-4-20250514", cmd.getModel());
        }

        @Test
        void defaultIsNull() {
            CampusClawCommand cmd = parse();
            assertNull(cmd.getModel());
        }
    }

    // -------------------------------------------------------------------
    // Prompt option
    // -------------------------------------------------------------------

    @Nested
    class PromptOption {

        @Test
        void longFlag() {
            CampusClawCommand cmd = parse("--prompt", "add tests");
            assertEquals("add tests", cmd.getPrompt());
        }

        @Test
        void defaultIsNull() {
            CampusClawCommand cmd = parse();
            assertNull(cmd.getPrompt());
        }

        @Test
        void shortPIsPrintNotPrompt() {
            CampusClawCommand cmd = parse("-p");
            assertTrue(cmd.isPrintMode());
            assertNull(cmd.getPrompt());
        }
    }

    // -------------------------------------------------------------------
    // Mode option
    // -------------------------------------------------------------------

    @Nested
    class ModeOption {

        @Test
        void defaultIsInteractive() {
            CampusClawCommand cmd = parse();
            assertEquals("interactive", cmd.getMode());
        }

        @Test
        void oneShot() {
            CampusClawCommand cmd = parse("--mode", "one-shot");
            assertEquals("one-shot", cmd.getMode());
        }

        @Test
        void print() {
            CampusClawCommand cmd = parse("--mode", "print");
            assertEquals("print", cmd.getMode());
        }
    }

    // -------------------------------------------------------------------
    // CWD option
    // -------------------------------------------------------------------

    @Nested
    class CwdOption {

        @Test
        void parsesPath() {
            CampusClawCommand cmd = parse("--cwd", "/tmp/project");
            assertEquals(Path.of("/tmp/project"), cmd.getCwd());
        }

        @Test
        void defaultIsNull() {
            CampusClawCommand cmd = parse();
            assertNull(cmd.getCwd());
        }
    }

    // -------------------------------------------------------------------
    // System prompt option
    // -------------------------------------------------------------------

    @Nested
    class SystemPromptOption {

        @Test
        void parsesSystemPrompt() {
            CampusClawCommand cmd = parse("--system-prompt", "Be concise.");
            assertEquals("Be concise.", cmd.getSystemPrompt());
        }

        @Test
        void appendSystemPrompt() {
            CampusClawCommand cmd = parse("--append-system-prompt", "Extra instructions");
            assertEquals("Extra instructions", cmd.getAppendSystemPrompt());
        }
    }

    // -------------------------------------------------------------------
    // Thinking option
    // -------------------------------------------------------------------

    @Nested
    class ThinkingOption {

        @Test
        void parsesThinkingLevel() {
            CampusClawCommand cmd = parse("--thinking", "high");
            assertEquals("high", cmd.getThinking());
        }

        @Test
        void defaultIsNull() {
            CampusClawCommand cmd = parse();
            assertNull(cmd.getThinking());
        }
    }

    // -------------------------------------------------------------------
    // Tools filter option
    // -------------------------------------------------------------------

    @Nested
    class ToolsOption {

        @Test
        void parsesToolsList() {
            CampusClawCommand cmd = parse("--tools", "read,bash,edit");
            assertEquals("read,bash,edit", cmd.getToolsFilter());
        }

        @Test
        void defaultIsNull() {
            CampusClawCommand cmd = parse();
            assertNull(cmd.getToolsFilter());
        }
    }

    // -------------------------------------------------------------------
    // Verbose option
    // -------------------------------------------------------------------

    @Nested
    class VerboseOption {

        @Test
        void parsesVerboseFlag() {
            CampusClawCommand cmd = parse("--verbose");
            assertTrue(cmd.isVerbose());
        }

        @Test
        void defaultIsFalse() {
            CampusClawCommand cmd = parse();
            assertFalse(cmd.isVerbose());
        }
    }

    // -------------------------------------------------------------------
    // Positional arguments
    // -------------------------------------------------------------------

    @Nested
    class PositionalArgs {

        @Test
        void capturesPositionalArgs() {
            CampusClawCommand cmd = parse("fix", "the", "bug");
            assertEquals(3, cmd.getPromptArgs().size());
            assertEquals("fix", cmd.getPromptArgs().get(0));
            assertEquals("the", cmd.getPromptArgs().get(1));
            assertEquals("bug", cmd.getPromptArgs().get(2));
        }

        @Test
        void emptyWhenNoneGiven() {
            CampusClawCommand cmd = parse();
            assertNull(cmd.getPromptArgs());
        }
    }

    // -------------------------------------------------------------------
    // Prompt resolution
    // -------------------------------------------------------------------

    @Nested
    class PromptResolution {

        @Test
        void flagTakesPrecedenceOverPositional() {
            CampusClawCommand cmd = parse("--prompt", "from flag", "positional", "args");
            assertEquals("from flag", cmd.resolvePrompt());
        }

        @Test
        void positionalJoinedWithSpaces() {
            CampusClawCommand cmd = parse("add", "unit", "tests");
            assertEquals("add unit tests", cmd.resolvePrompt());
        }

        @Test
        void nullWhenNothingGiven() {
            CampusClawCommand cmd = parse();
            assertNull(cmd.resolvePrompt());
        }

        @Test
        void blankFlagFallsBackToPositional() {
            CampusClawCommand cmd = parse("--prompt", "  ", "fallback");
            assertEquals("fallback", cmd.resolvePrompt());
        }
    }

    // -------------------------------------------------------------------
    // Combined options
    // -------------------------------------------------------------------

    @Nested
    class CombinedOptions {

        @Test
        void allOptionsTogether() {
            CampusClawCommand cmd = parse(
                    "-m", "claude-sonnet-4-20250514",
                    "--prompt", "refactor auth",
                    "--mode", "one-shot",
                    "--cwd", "/tmp/work",
                    "--system-prompt", "Use Java 21",
                    "--thinking", "high",
                    "--tools", "read,bash"
            );

            assertEquals("claude-sonnet-4-20250514", cmd.getModel());
            assertEquals("refactor auth", cmd.getPrompt());
            assertEquals("one-shot", cmd.getMode());
            assertEquals(Path.of("/tmp/work"), cmd.getCwd());
            assertEquals("Use Java 21", cmd.getSystemPrompt());
            assertEquals("high", cmd.getThinking());
            assertEquals("read,bash", cmd.getToolsFilter());
        }

        @Test
        void optionsWithPositionalArgs() {
            CampusClawCommand cmd = parse("-m", "model-name", "fix", "bugs");
            assertEquals("model-name", cmd.getModel());
            assertEquals(2, cmd.getPromptArgs().size());
        }
    }

    // -------------------------------------------------------------------
    // Execution
    // -------------------------------------------------------------------

    @Nested
    class Execution {

        @Test
        void printModeReturnsZero() {
            CampusClawCommand cmd = parse("--mode", "print", "--prompt", "test");
            assertEquals(0, cmd.call());
        }

        @Test
        void oneShotWithoutPromptReturnsOne() {
            CampusClawCommand cmd = parse("--mode", "one-shot");
            assertEquals(1, cmd.call());
        }
    }

    // -------------------------------------------------------------------
    // Help / Version via CommandLine
    // -------------------------------------------------------------------

    @Nested
    class HelpAndVersion {

        @Test
        void helpExitsWithZero() {
            CampusClawCommand cmd = new CampusClawCommand(null, null, null, null, null, null, null, null, null);
            int exitCode = new CommandLine(cmd).execute("--help");
            assertEquals(0, exitCode);
        }

        @Test
        void versionExitsWithZero() {
            CampusClawCommand cmd = new CampusClawCommand(null, null, null, null, null, null, null, null, null);
            int exitCode = new CommandLine(cmd).execute("--version");
            assertEquals(0, exitCode);
        }
    }
}
