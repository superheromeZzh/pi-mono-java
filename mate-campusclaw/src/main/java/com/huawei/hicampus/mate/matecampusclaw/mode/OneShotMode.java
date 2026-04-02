package com.huawei.hicampus.mate.matecampusclaw.codingagent.mode;

import java.io.PrintStream;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;

import com.huawei.hicampus.mate.matecampusclaw.agent.event.*;
import com.huawei.hicampus.mate.matecampusclaw.ai.stream.AssistantMessageEvent;
import com.huawei.hicampus.mate.matecampusclaw.ai.types.AssistantMessage;
import com.huawei.hicampus.mate.matecampusclaw.ai.types.ContentBlock;
import com.huawei.hicampus.mate.matecampusclaw.ai.types.StopReason;
import com.huawei.hicampus.mate.matecampusclaw.ai.types.TextContent;
import com.huawei.hicampus.mate.matecampusclaw.codingagent.session.AgentSession;

/**
 * Executes a single prompt against the agent session with streaming output.
 * Text deltas are printed to stdout as they arrive for responsive UX.
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
     * Sends the prompt to the session, streams text output to stdout as it
     * arrives, and returns an exit code.
     *
     * @param session an initialized {@link AgentSession}
     * @param prompt  the user prompt to execute
     * @return 0 on success, 1 on error
     */
    public int run(AgentSession session, String prompt) {
        Objects.requireNonNull(session, "session");
        Objects.requireNonNull(prompt, "prompt");

        // Subscribe for streaming output
        var hasOutput = new boolean[]{false};
        Runnable unsub = session.subscribe(event -> {
            if (event instanceof MessageUpdateEvent e) {
                if (e.assistantMessageEvent() instanceof AssistantMessageEvent.TextDeltaEvent delta) {
                    out.print(delta.delta());
                    out.flush();
                    hasOutput[0] = true;
                }
            }
        });

        CompletableFuture<Void> future = session.prompt(prompt);

        try {
            future.join();
        } catch (Exception e) {
            String error = session.getAgent().getState().getError();
            err.println("\nError: " + (error != null ? error : e.getMessage()));
            return 1;
        } finally {
            unsub.run();
        }

        // Check for error state
        String error = session.getAgent().getState().getError();
        if (error != null) {
            err.println("\nError: " + error);
            return 1;
        }

        // Check stop reason and print fallback if streaming didn't capture output
        var history = session.getHistory();
        for (int i = history.size() - 1; i >= 0; i--) {
            if (history.get(i) instanceof AssistantMessage am) {
                if (am.stopReason() == StopReason.ERROR) {
                    String errorMsg = am.errorMessage();
                    err.println("\nError: " + (errorMsg != null ? errorMsg : "agent stopped with error"));
                    return 1;
                }
                // Fallback: if streaming didn't produce output, print text from final message
                if (!hasOutput[0]) {
                    for (ContentBlock block : am.content()) {
                        if (block instanceof TextContent text) {
                            out.print(text.text());
                            hasOutput[0] = true;
                        }
                    }
                }
                break;
            }
        }

        if (hasOutput[0]) {
            out.println();
        }
        return 0;
    }
}
