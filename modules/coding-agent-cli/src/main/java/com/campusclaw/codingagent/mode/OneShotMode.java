/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.campusclaw.codingagent.mode;

import java.io.PrintStream;
import java.util.Objects;

import com.campusclaw.agent.event.MessageUpdateEvent;
import com.campusclaw.ai.stream.AssistantMessageEvent;
import com.campusclaw.ai.types.AssistantMessage;
import com.campusclaw.ai.types.ContentBlock;
import com.campusclaw.ai.types.StopReason;
import com.campusclaw.ai.types.TextContent;
import com.campusclaw.codingagent.session.AgentSession;

/**
 * Executes a single prompt against the agent session with streaming output.
 * Text deltas are printed to stdout as they arrive for responsive UX.
 *
 * <p>Exit codes:
 * <ul>
 *   <li>0 — success</li>
 *   <li>1 — error (execution failed or assistant reported an error)</li>
 * </ul>
 *
 * @version [br_eCampusCore 25.1.0_Next, 2026/05/06]
 * @since [br_eCampusCore 25.1.0_Next]
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
        var hasOutput = new boolean[] {false};
        Runnable unsub = session.subscribe(event -> {
            if (event instanceof MessageUpdateEvent e
                    && e.assistantMessageEvent() instanceof AssistantMessageEvent.TextDeltaEvent delta) {
                out.print(delta.delta());
                out.flush();
                hasOutput[0] = true;
            }
        });
        try {
            session.prompt(prompt).join();
        } catch (Exception e) {
            String error = session.getAgent().getState().getError();
            err.println("\nError: " + (error != null ? error : e.getMessage()));
            return 1;
        } finally {
            unsub.run();
        }
        String error = session.getAgent().getState().getError();
        if (error != null) {
            err.println("\nError: " + error);
            return 1;
        }
        int rc = handleFinalMessage(session, hasOutput);
        if (rc != 0) {
            return rc;
        }
        if (hasOutput[0]) {
            out.println();
        }
        return 0;
    }

    // Look at the trailing assistant message: surface its error or, if streaming
    // didn't produce any output, print the message text as a fallback.
    private int handleFinalMessage(AgentSession session, boolean[] hasOutput) {
        var history = session.getHistory();
        for (int i = history.size() - 1; i >= 0; i--) {
            if (!(history.get(i) instanceof AssistantMessage am)) {
                continue;
            }
            if (am.stopReason() == StopReason.ERROR) {
                String errorMsg = am.errorMessage();
                err.println("\nError: " + (errorMsg != null ? errorMsg : "agent stopped with error"));
                return 1;
            }
            if (!hasOutput[0]) {
                for (ContentBlock block : am.content()) {
                    if (block instanceof TextContent text) {
                        out.print(text.text());
                        hasOutput[0] = true;
                    }
                }
            }
            return 0;
        }
        return 0;
    }
}
