package com.mariozechner.pi.codingagent.mode;

import com.mariozechner.pi.ai.types.AssistantMessage;
import com.mariozechner.pi.ai.types.ContentBlock;
import com.mariozechner.pi.ai.types.Message;
import com.mariozechner.pi.ai.types.StopReason;
import com.mariozechner.pi.ai.types.TextContent;
import com.mariozechner.pi.codingagent.session.AgentSession;

import java.io.PrintStream;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;

/**
 * Executes a single prompt against the agent session, prints the assistant's
 * text output to stdout, and returns an exit code.
 *
 * <p>Exit codes:
 * <ul>
 *   <li>0 — success</li>
 *   <li>1 — error (execution failed or assistant reported an error)</li>
 * </ul>
 */
public class OneShotMode {

    private final PrintStream out;
    private final PrintStream err;

    public OneShotMode() {
        this(System.out, System.err);
    }

    OneShotMode(PrintStream out, PrintStream err) {
        this.out = Objects.requireNonNull(out, "out");
        this.err = Objects.requireNonNull(err, "err");
    }

    /**
     * Sends the prompt to the session, waits for completion, prints the
     * assistant's text response to stdout, and returns an exit code.
     *
     * @param session an initialized {@link AgentSession}
     * @param prompt  the user prompt to execute
     * @return 0 on success, 1 on error
     */
    public int run(AgentSession session, String prompt) {
        Objects.requireNonNull(session, "session");
        Objects.requireNonNull(prompt, "prompt");

        CompletableFuture<Void> future = session.prompt(prompt);

        try {
            future.join();
        } catch (Exception e) {
            String error = session.getAgent().getState().getError();
            err.println("Error: " + (error != null ? error : e.getMessage()));
            return 1;
        }

        // Check for error state even if the future completed normally
        String error = session.getAgent().getState().getError();
        if (error != null) {
            err.println("Error: " + error);
            return 1;
        }

        List<Message> history = session.getHistory();
        AssistantMessage lastAssistant = findLastAssistantMessage(history);

        if (lastAssistant == null) {
            return 0;
        }

        if (lastAssistant.stopReason() == StopReason.ERROR) {
            String errorMsg = lastAssistant.errorMessage();
            err.println("Error: " + (errorMsg != null ? errorMsg : "agent stopped with error"));
            return 1;
        }

        printAssistantText(lastAssistant);
        return 0;
    }

    private AssistantMessage findLastAssistantMessage(List<Message> history) {
        for (int i = history.size() - 1; i >= 0; i--) {
            if (history.get(i) instanceof AssistantMessage am) {
                return am;
            }
        }
        return null;
    }

    private void printAssistantText(AssistantMessage message) {
        for (ContentBlock block : message.content()) {
            if (block instanceof TextContent text) {
                out.print(text.text());
            }
        }
        out.println();
    }
}
