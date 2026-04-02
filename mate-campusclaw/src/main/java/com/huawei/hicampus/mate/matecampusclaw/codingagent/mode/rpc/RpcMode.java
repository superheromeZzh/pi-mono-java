package com.huawei.hicampus.mate.matecampusclaw.codingagent.mode.rpc;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.Map;

import com.huawei.hicampus.mate.matecampusclaw.agent.event.*;
import com.huawei.hicampus.mate.matecampusclaw.codingagent.session.AgentSession;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * RPC mode: reads JSONL commands from stdin, writes JSONL events to stdout.
 * Designed for headless operation and external process integration.
 */
public class RpcMode {
    private static final Logger log = LoggerFactory.getLogger(RpcMode.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final AgentSession session;

    public RpcMode(AgentSession session) {
        this.session = session;
    }

    public void run() {
        // Subscribe to agent events and forward as RPC events
        session.subscribe(event -> {
            if (event instanceof MessageStartEvent) {
                emit(RpcEvent.of("message_start", null));
            } else if (event instanceof MessageUpdateEvent mu) {
                var msg = mu.message();
                if (msg != null) {
                    emit(RpcEvent.of("message_update", Map.of("message", msg)));
                }
            } else if (event instanceof MessageEndEvent me) {
                emit(RpcEvent.of("message_end", Map.of("message", me.message())));
            } else if (event instanceof ToolExecutionStartEvent te) {
                emit(RpcEvent.of("tool_start", Map.of("toolName", te.toolName(), "toolCallId", te.toolCallId())));
            } else if (event instanceof ToolExecutionEndEvent te) {
                emit(RpcEvent.of("tool_end", Map.of("toolCallId", te.toolCallId())));
            } else if (event instanceof AgentEndEvent) {
                emit(RpcEvent.of("done", null));
            }
        });

        // Read commands from stdin
        try (var reader = new BufferedReader(new InputStreamReader(System.in))) {
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty()) continue;
                try {
                    var cmd = MAPPER.readValue(line, RpcCommand.class);
                    handleCommand(cmd);
                } catch (Exception e) {
                    emit(RpcEvent.error(null, "Invalid command: " + e.getMessage()));
                }
            }
        } catch (Exception e) {
            log.error("RPC mode error", e);
        }
    }

    private void handleCommand(RpcCommand cmd) {
        try {
            switch (cmd.type()) {
                case "prompt" -> {
                    if (cmd.message() != null) {
                        session.prompt(cmd.message());
                        emit(RpcEvent.response(cmd.id(), "ack", null));
                    }
                }
                case "steer" -> {
                    if (cmd.message() != null) {
                        session.steer(cmd.message());
                        emit(RpcEvent.response(cmd.id(), "ack", null));
                    }
                }
                case "abort" -> {
                    session.abort();
                    emit(RpcEvent.response(cmd.id(), "ack", null));
                }
                case "get_state" -> {
                    emit(RpcEvent.response(cmd.id(), "state", Map.of(
                        "model", session.getModelId(),
                        "isStreaming", session.isStreaming()
                    )));
                }
                case "set_model" -> {
                    if (cmd.model() != null) {
                        session.setModel(cmd.model());
                        emit(RpcEvent.response(cmd.id(), "ack", null));
                    }
                }
                case "new_session" -> {
                    session.newSession();
                    emit(RpcEvent.response(cmd.id(), "ack", null));
                }
                default -> emit(RpcEvent.error(cmd.id(), "Unknown command: " + cmd.type()));
            }
        } catch (Exception e) {
            emit(RpcEvent.error(cmd.id(), e.getMessage()));
        }
    }

    private void emit(RpcEvent event) {
        try {
            System.out.println(MAPPER.writeValueAsString(event));
            System.out.flush();
        } catch (Exception e) {
            log.warn("Failed to emit RPC event", e);
        }
    }
}
