package com.huawei.hicampus.mate.matecampusclaw.codingagent.mode;

import com.huawei.hicampus.mate.matecampusclaw.agent.event.*;
import com.huawei.hicampus.mate.matecampusclaw.ai.types.AssistantMessage;
import com.huawei.hicampus.mate.matecampusclaw.ai.types.TextContent;
import com.huawei.hicampus.mate.matecampusclaw.codingagent.session.AgentSession;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Print mode: executes a single prompt and writes the result to stdout.
 * Supports "text" (final text only) and "json" (all events as JSONL) output formats.
 */
public class PrintMode {
    private static final Logger log = LoggerFactory.getLogger(PrintMode.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    public enum OutputFormat { TEXT, JSON }

    private final AgentSession session;
    private final OutputFormat format;

    public PrintMode(AgentSession session, OutputFormat format) {
        this.session = session;
        this.format = format;
    }

    public int run(String prompt) {
        var result = new StringBuilder();
        var exitCode = new int[]{0};

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
                System.out.print(result);
            }
        } catch (Exception e) {
            log.error("Print mode error", e);
            exitCode[0] = 1;
        }

        return exitCode[0];
    }

    private void writeJsonEvent(Object event) {
        try {
            System.out.println(MAPPER.writeValueAsString(event));
            System.out.flush();
        } catch (Exception e) {
            log.warn("Failed to serialize event", e);
        }
    }
}
