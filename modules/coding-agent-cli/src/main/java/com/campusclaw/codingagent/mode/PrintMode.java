/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.campusclaw.codingagent.mode;

import com.campusclaw.agent.event.MessageEndEvent;
import com.campusclaw.ai.types.AssistantMessage;
import com.campusclaw.ai.types.TextContent;
import com.campusclaw.codingagent.session.AgentSession;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Print mode: executes a single prompt and writes the result to stdout.
 * Supports "text" (final text only) and "json" (all events as JSONL) output formats.
 *
 * @version [br_eCampusCore 25.1.0_Next, 2026/05/06]
 * @since [br_eCampusCore 25.1.0_Next]
 */
public class PrintMode {
    private static final Logger log = LoggerFactory.getLogger(PrintMode.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    @SuppressWarnings("checkstyle:top_class_comment")
    public enum OutputFormat {
        TEXT,
        JSON
    }

    private final AgentSession session;
    private final OutputFormat format;

    public PrintMode(AgentSession session, OutputFormat format) {
        this.session = session;
        this.format = format;
    }

    public int run(String prompt) {
        var result = new StringBuilder();
        var exitCode = new int[] {0};

        session.subscribe(event -> {
            if (format == OutputFormat.JSON) {
                writeJsonEvent(event);
            }

            // Capture text for TEXT mode
            if (event instanceof MessageEndEvent me) {
                var msg = me.message();
                if (msg instanceof AssistantMessage am) {
                    for (var cb : am.content()) {
                        if (cb instanceof TextContent tc) {
                            result.append(tc.text());
                        }
                    }
                }
            }
        });

        try {
            session.prompt(prompt).join();

            if (format == OutputFormat.TEXT) {
                writeStdout(result.toString());
            }
        } catch (Exception e) {
            log.error("Print mode error", e);
            exitCode[0] = 1;
        }

        return exitCode[0];
    }

    /*
     * PrintMode's contract is "write program output to stdout" — text for TEXT mode,
     * JSONL events for JSON mode. This is the protocol byte stream callers pipe and
     * parse, not a log: it must go through System.out, never a logger.
     */
    @SuppressWarnings("checkstyle:no_system_out_err")
    private void writeStdout(String text) {
        System.out.print(text);
    }

    @SuppressWarnings("checkstyle:no_system_out_err")
    private void writeJsonEvent(Object event) {
        try {
            System.out.println(MAPPER.writeValueAsString(event));
            System.out.flush();
        } catch (Exception e) {
            log.warn("Failed to serialize event", e);
        }
    }
}
