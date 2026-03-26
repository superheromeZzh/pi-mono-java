package com.mariozechner.pi.codingagent.mode;

import com.mariozechner.pi.agent.event.*;
import com.mariozechner.pi.ai.stream.AssistantMessageEvent;
import com.mariozechner.pi.ai.types.AssistantMessage;
import com.mariozechner.pi.ai.types.ContentBlock;
import com.mariozechner.pi.ai.types.TextContent;
import com.mariozechner.pi.codingagent.session.AgentSession;
import com.mariozechner.pi.tui.terminal.Terminal;

import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Runs an interactive REPL loop that reads user input from the terminal,
 * sends it to the agent, and streams the response back in real time.
 *
 * <p>Supports:
 * <ul>
 *   <li>Real-time streaming of LLM text output via {@code TextDeltaEvent}</li>
 *   <li>Tool call status display</li>
 *   <li>{@code /exit} command to quit</li>
 *   <li>Ctrl+C to abort current agent execution</li>
 *   <li>{@code /skill:name} command expansion (handled by AgentSession)</li>
 * </ul>
 */
public class InteractiveMode {

    static final String PROMPT_PREFIX = "> ";
    static final String EXIT_COMMAND = "/exit";
    static final String ANSI_RESET = "\033[0m";
    static final String ANSI_BOLD = "\033[1m";
    static final String ANSI_DIM = "\033[2m";
    static final String ANSI_CYAN = "\033[36m";
    static final String ANSI_YELLOW = "\033[33m";
    static final String ANSI_RED = "\033[31m";

    /**
     * Runs the interactive REPL. Blocks until the user types {@code /exit}
     * or the terminal is closed.
     *
     * @param session    an initialized {@link AgentSession}
     * @param terminal   the terminal for I/O
     */
    public void run(AgentSession session, Terminal terminal) {
        Objects.requireNonNull(session, "session");
        Objects.requireNonNull(terminal, "terminal");

        terminal.enterRawMode();
        try {
            writeWelcome(terminal);
            replLoop(session, terminal);
        } finally {
            terminal.exitRawMode();
        }
    }

    private void replLoop(AgentSession session, Terminal terminal) {
        while (true) {
            String input = readInput(terminal);
            if (input == null) {
                // Terminal closed / EOF
                break;
            }

            String trimmed = input.trim();
            if (trimmed.isEmpty()) {
                continue;
            }

            if (EXIT_COMMAND.equals(trimmed)) {
                writeLine(terminal, ANSI_DIM + "Goodbye." + ANSI_RESET);
                break;
            }

            executePrompt(session, terminal, trimmed);
        }
    }

    /**
     * Reads a line of input from the terminal by collecting characters until
     * Enter is pressed. Supports Ctrl+C to return empty, and basic backspace.
     */
    String readInput(Terminal terminal) {
        var inputBuffer = new StringBuilder();
        var latch = new CountDownLatch(1);
        var cancelled = new AtomicBoolean(false);
        var eof = new AtomicBoolean(false);

        terminal.write(PROMPT_PREFIX);

        terminal.onInput(data -> {
            if (latch.getCount() == 0) return;

            for (int i = 0; i < data.length(); i++) {
                char ch = data.charAt(i);
                if (ch == '\r' || ch == '\n') {
                    terminal.write("\r\n");
                    latch.countDown();
                    return;
                } else if (ch == 3) { // Ctrl+C
                    cancelled.set(true);
                    terminal.write("\r\n");
                    latch.countDown();
                    return;
                } else if (ch == 4) { // Ctrl+D (EOF)
                    eof.set(true);
                    latch.countDown();
                    return;
                } else if (ch == 127 || ch == 8) { // Backspace
                    if (!inputBuffer.isEmpty()) {
                        inputBuffer.deleteCharAt(inputBuffer.length() - 1);
                        terminal.write("\b \b");
                    }
                } else if (ch >= 32) { // Printable character
                    inputBuffer.append(ch);
                    terminal.write(String.valueOf(ch));
                }
            }
        });

        try {
            latch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return null;
        }

        if (eof.get()) {
            return null;
        }
        if (cancelled.get()) {
            return "";
        }
        return inputBuffer.toString();
    }

    void executePrompt(AgentSession session, Terminal terminal, String input) {
        var unsubscribe = new AtomicReference<Runnable>();
        var aborted = new AtomicBoolean(false);

        // Subscribe to agent events for streaming output
        Runnable unsub = session.getAgent().subscribe(event ->
                handleEvent(event, terminal));
        unsubscribe.set(unsub);

        // Set up Ctrl+C handler during execution
        terminal.onInput(data -> {
            for (int i = 0; i < data.length(); i++) {
                if (data.charAt(i) == 3) { // Ctrl+C
                    aborted.set(true);
                    session.abort();
                    return;
                }
            }
        });

        CompletableFuture<Void> future = session.prompt(input);

        try {
            future.join();
        } catch (Exception e) {
            String error = session.getAgent().getState().getError();
            if (aborted.get()) {
                writeLine(terminal, ANSI_DIM + "\nAborted." + ANSI_RESET);
            } else {
                writeLine(terminal,
                        ANSI_RED + "\nError: " + (error != null ? error : e.getMessage()) + ANSI_RESET);
            }
        }

        // Check for errors not from exceptions
        String error = session.getAgent().getState().getError();
        if (error != null && !aborted.get()) {
            writeLine(terminal, ANSI_RED + "\nError: " + error + ANSI_RESET);
        }

        // Unsubscribe from events
        var unsub2 = unsubscribe.get();
        if (unsub2 != null) {
            unsub2.run();
        }

        terminal.write("\n");
    }

    void handleEvent(AgentEvent event, Terminal terminal) {
        switch (event) {
            case MessageUpdateEvent e -> handleMessageUpdate(e, terminal);
            case MessageEndEvent e -> handleMessageEnd(terminal);
            case ToolExecutionStartEvent e -> handleToolStart(e, terminal);
            case ToolExecutionEndEvent e -> handleToolEnd(e, terminal);
            case AgentEndEvent ignored -> { }
            default -> { }
        }
    }

    private void handleMessageUpdate(MessageUpdateEvent event, Terminal terminal) {
        if (event.assistantMessageEvent() instanceof AssistantMessageEvent.TextDeltaEvent delta) {
            terminal.write(delta.delta());
        }
    }

    private void handleMessageEnd(Terminal terminal) {
        // Ensure we end on a new line after assistant text
    }

    private void handleToolStart(ToolExecutionStartEvent event, Terminal terminal) {
        terminal.write("\n" + ANSI_YELLOW + ANSI_BOLD + "  [" + event.toolName() + "]"
                + ANSI_RESET + ANSI_DIM + " running..." + ANSI_RESET);
    }

    private void handleToolEnd(ToolExecutionEndEvent event, Terminal terminal) {
        if (event.isError()) {
            terminal.write(" " + ANSI_RED + "failed" + ANSI_RESET + "\n");
        } else {
            terminal.write(" " + ANSI_CYAN + "done" + ANSI_RESET + "\n");
        }
    }

    private void writeWelcome(Terminal terminal) {
        writeLine(terminal, ANSI_BOLD + ANSI_CYAN + "Pi Coding Agent" + ANSI_RESET
                + ANSI_DIM + " — type /exit to quit" + ANSI_RESET);
        terminal.write("\n");
    }

    private void writeLine(Terminal terminal, String text) {
        terminal.write(text + "\r\n");
    }
}
