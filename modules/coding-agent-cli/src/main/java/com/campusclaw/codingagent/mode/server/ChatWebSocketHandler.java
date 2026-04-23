package com.campusclaw.codingagent.mode.server;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.campusclaw.agent.Agent;
import com.campusclaw.agent.event.AgentEndEvent;
import com.campusclaw.agent.event.AgentEvent;
import com.campusclaw.agent.event.AgentStartEvent;
import com.campusclaw.agent.event.MessageEndEvent;
import com.campusclaw.agent.event.MessageStartEvent;
import com.campusclaw.agent.event.MessageUpdateEvent;
import com.campusclaw.agent.event.ToolExecutionEndEvent;
import com.campusclaw.agent.event.ToolExecutionStartEvent;
import com.campusclaw.agent.event.ToolExecutionUpdateEvent;
import com.campusclaw.ai.types.AssistantMessage;
import com.campusclaw.ai.types.ContentBlock;
import com.campusclaw.ai.types.Message;
import com.campusclaw.ai.types.StopReason;
import com.campusclaw.ai.types.TextContent;
import com.campusclaw.ai.types.ThinkingLevel;
import com.campusclaw.codingagent.session.AgentSession;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;

import org.reactivestreams.Publisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;
import reactor.netty.http.websocket.WebsocketInbound;
import reactor.netty.http.websocket.WebsocketOutbound;

/**
 * WebSocket handler for {@code /api/ws/chat}. Exposes the full {@link AgentSession}
 * command surface on a single long-lived connection, with agent events streamed
 * back as JSON frames.
 *
 * <p>Protocol contract: {@code docs/asyncapi/chat-ws.yaml}.
 *
 * <p>Shares {@link SessionPool} with the SSE endpoint {@code /api/chat}, so
 * reconnecting with the same {@code conversation_id} resumes the same session.
 */
public class ChatWebSocketHandler {

    private static final Logger log = LoggerFactory.getLogger(ChatWebSocketHandler.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    /**
     * Explicitly-typed writer for {@link Message}. Required because {@link Message}
     * uses {@link com.fasterxml.jackson.annotation.JsonTypeInfo} on the sealed
     * interface to emit {@code "role":"..."}. When we put a Message into a
     * {@code Map<String, Object>} and serialize the map, Jackson sees the element's
     * declared type as {@code Object} and skips the discriminator. Pre-serializing
     * the Message with this writer and embedding the resulting JsonNode preserves
     * {@code role}, which frontends depend on to render assistant bubbles.
     */
    private static final ObjectWriter MESSAGE_WRITER = MAPPER.writerFor(Message.class);

    /**
     * Busy-loop retry on emit contention for up to 50ms. Three concurrent producers
     * write to the outbound sink: (1) the agent event subscribe callback, (2) the
     * inbound command dispatcher, (3) the heartbeat interval Flux.
     */
    private static final Sinks.EmitFailureHandler BUSY_LOOP =
            Sinks.EmitFailureHandler.busyLooping(Duration.ofMillis(50));

    private static final String PONG_FRAME = "{\"type\":\"pong\"}";
    private static final Duration HEARTBEAT_INTERVAL = Duration.ofSeconds(20);

    private final SessionPool pool;

    public ChatWebSocketHandler(SessionPool pool) {
        this.pool = pool;
    }

    /**
     * Handle one WebSocket connection.
     *
     * @param in                   inbound frame stream
     * @param out                  outbound frame sink
     * @param conversationIdHint   conversation to resume, or {@code null} to create a new one
     */
    public Publisher<Void> handle(WebsocketInbound in, WebsocketOutbound out, String conversationIdHint) {
        SessionPool.SessionRef ref = pool.getOrCreate(conversationIdHint);
        AgentSession session = ref.session();
        String conversationId = ref.conversationId();
        log.info("WebSocket connected: conversation={}", conversationId);

        Sinks.Many<String> outbound = Sinks.many().multicast().onBackpressureBuffer();

        Runnable unsubscribe = session.subscribe(event -> {
            String json = serializeEvent(event, conversationId);
            if (json != null) {
                outbound.emitNext(json, BUSY_LOOP);
            }
            // Model-level error (assistant completed with stopReason=ERROR) is
            // not the same as a runtime exception on session.prompt(). Surface it
            // with an additional `error` frame so frontends don't have to dig
            // into message.stopReason. See docs/plans/ws-chat-plan.md.
            if (event instanceof MessageEndEvent me
                    && me.message() instanceof AssistantMessage am
                    && am.stopReason() == StopReason.ERROR) {
                String errorText = am.errorMessage() != null ? am.errorMessage() : "model returned stopReason=error";
                String errorJson = serializeErrorFrame(conversationId, errorText);
                if (errorJson != null) {
                    outbound.emitNext(errorJson, BUSY_LOOP);
                }
            }
        });

        Mono<Void> inboundPipeline = in.receive().asString()
                .doOnNext(raw -> handleCommand(raw, session, conversationId, outbound))
                .then();

        Flux<String> heartbeat = Flux.interval(HEARTBEAT_INTERVAL).map(i -> PONG_FRAME);
        Flux<String> toSend = Flux.merge(outbound.asFlux(), heartbeat);
        Mono<Void> sendPipeline = out.sendString(toSend).then();

        Mono<Void> cleanup = in.receiveCloseStatus()
                .doFinally(sig -> {
                    log.info("WebSocket closed: conversation={} signal={}", conversationId, sig);
                    unsubscribe.run();
                    if (session.isInitialized() && session.isStreaming()) {
                        try {
                            session.abort();
                        } catch (Exception e) {
                            log.debug("Abort on disconnect failed for {}", conversationId, e);
                        }
                    }
                    outbound.emitComplete(BUSY_LOOP);
                })
                .then();

        return Mono.when(inboundPipeline, sendPipeline, cleanup);
    }

    // =========================================================================
    // Command dispatch
    // =========================================================================

    private void handleCommand(String raw, AgentSession session, String conversationId, Sinks.Many<String> out) {
        String id = null;
        String type = "unknown";
        try {
            JsonNode cmd = MAPPER.readTree(raw);
            type = cmd.path("type").asText("unknown");
            id = cmd.hasNonNull("id") ? cmd.get("id").asText() : null;

            switch (type) {
                case "prompt" -> handlePrompt(cmd, id, session, conversationId, out);
                case "steer" -> handleSteer(cmd, id, session, out);
                case "abort" -> {
                    session.abort();
                    emitResponse(out, id, true, null);
                }
                case "new_session" -> {
                    session.newSession();
                    emitResponse(out, id, true, null);
                }
                case "set_model" -> handleSetModel(cmd, id, session, out);
                case "set_thinking_level" -> handleSetThinkingLevel(cmd, id, session, out);
                case "get_state" -> handleGetState(id, session, conversationId, out);
                case "get_history" -> emitResponse(out, id, true, Map.of("messages", messagesToNode(session.getHistory())));
                case "get_prompt_templates" -> handleGetPromptTemplates(id, session, out);
                case "list_skills" -> handleListSkills(id, session, out);
                case "ping" -> out.emitNext(PONG_FRAME, BUSY_LOOP);
                default -> emitResponse(out, id, false, "unknown command type: " + type);
            }
        } catch (Exception e) {
            log.warn("Failed to handle WS command type={} raw={}", type, raw, e);
            emitResponse(out, id, false, "bad frame: " + e.getMessage());
        }
    }

    private void handlePrompt(JsonNode cmd, String id, AgentSession session, String conversationId, Sinks.Many<String> out) {
        String message = cmd.path("message").asText();
        if (message.isEmpty()) {
            emitResponse(out, id, false, "message is required");
            return;
        }
        if (session.isStreaming()) {
            emitResponse(out, id, false, "conversation is already processing a prompt");
            return;
        }
        emitResponse(out, id, true, Map.of("conversation_id", conversationId));
        session.prompt(message).whenComplete((v, ex) -> {
            if (ex != null) {
                emitErrorFrame(out, conversationId, Agent.formatError(ex));
            }
        });
    }

    private void handleSteer(JsonNode cmd, String id, AgentSession session, Sinks.Many<String> out) {
        String message = cmd.path("message").asText();
        if (message.isEmpty()) {
            emitResponse(out, id, false, "message is required");
            return;
        }
        session.steer(message);
        emitResponse(out, id, true, null);
    }

    private void handleSetModel(JsonNode cmd, String id, AgentSession session, Sinks.Many<String> out) {
        String model = cmd.path("model").asText();
        if (model.isEmpty()) {
            emitResponse(out, id, false, "model is required");
            return;
        }
        try {
            session.setModel(model);
            emitResponse(out, id, true, Map.of("model", session.getModelId()));
        } catch (Exception e) {
            emitResponse(out, id, false, "Invalid model: " + e.getMessage());
        }
    }

    private void handleSetThinkingLevel(JsonNode cmd, String id, AgentSession session, Sinks.Many<String> out) {
        String level = cmd.path("level").asText();
        try {
            ThinkingLevel tl = ThinkingLevel.fromValue(level);
            session.getAgent().setThinkingLevel(tl);
            emitResponse(out, id, true, Map.of("level", tl.value()));
        } catch (Exception e) {
            emitResponse(out, id, false, "Invalid thinking level: " + level);
        }
    }

    private void handleGetState(String id, AgentSession session, String conversationId, Sinks.Many<String> out) {
        var state = new LinkedHashMap<String, Object>();
        state.put("conversation_id", conversationId);
        state.put("isStreaming", session.isStreaming());
        state.put("model", session.getModelId());
        state.put("thinkingLevel", session.getAgent().getState().getThinkingLevel().value());
        state.put("messageCount", session.getHistory().size());
        emitResponse(out, id, true, state);
    }

    private void handleGetPromptTemplates(String id, AgentSession session, Sinks.Many<String> out) {
        List<Map<String, Object>> list = session.getPromptTemplates().stream()
                .map(t -> {
                    var m = new LinkedHashMap<String, Object>();
                    m.put("name", t.name());
                    m.put("description", t.description() != null ? t.description() : "");
                    m.put("source", t.source());
                    return (Map<String, Object>) m;
                })
                .toList();
        emitResponse(out, id, true, Map.of("templates", list));
    }

    private void handleListSkills(String id, AgentSession session, Sinks.Many<String> out) {
        List<Map<String, Object>> list = session.getSkillRegistry().getAll().stream()
                .map(s -> {
                    var m = new LinkedHashMap<String, Object>();
                    m.put("name", s.name());
                    m.put("description", s.description() != null ? s.description() : "");
                    m.put("source", s.source());
                    return (Map<String, Object>) m;
                })
                .toList();
        emitResponse(out, id, true, Map.of("skills", list));
    }

    // =========================================================================
    // Event → JSON mapping
    //
    // Intentionally independent from ChatHandler's SSE mapping — the WS wire
    // exposes a richer event set (agent_start, tool_update, full Message bodies)
    // while SSE stays minimal. Keep them as two copies to let each evolve on its
    // own cadence.
    // =========================================================================

    private String serializeEvent(AgentEvent event, String conversationId) {
        try {
            if (event instanceof AgentStartEvent) {
                return MAPPER.writeValueAsString(Map.of(
                        "type", "agent_start",
                        "conversation_id", conversationId));
            } else if (event instanceof MessageStartEvent ms) {
                var m = new LinkedHashMap<String, Object>();
                m.put("type", "message_start");
                m.put("conversation_id", conversationId);
                if (ms.message() != null) {
                    m.put("message", messageToNode(ms.message()));
                }
                return MAPPER.writeValueAsString(m);
            } else if (event instanceof MessageUpdateEvent mu) {
                if (mu.message() == null) {
                    return null;
                }
                var m = new LinkedHashMap<String, Object>();
                m.put("type", "message_update");
                m.put("message", messageToNode(mu.message()));
                return MAPPER.writeValueAsString(m);
            } else if (event instanceof MessageEndEvent me) {
                var m = new LinkedHashMap<String, Object>();
                m.put("type", "message_end");
                if (me.message() != null) {
                    m.put("message", messageToNode(me.message()));
                }
                return MAPPER.writeValueAsString(m);
            } else if (event instanceof ToolExecutionStartEvent te) {
                var m = new LinkedHashMap<String, Object>();
                m.put("type", "tool_start");
                m.put("toolCallId", te.toolCallId());
                m.put("toolName", te.toolName());
                if (te.args() != null) {
                    m.put("args", te.args());
                }
                return MAPPER.writeValueAsString(m);
            } else if (event instanceof ToolExecutionUpdateEvent tu) {
                var m = new LinkedHashMap<String, Object>();
                m.put("type", "tool_update");
                m.put("toolCallId", tu.toolCallId());
                m.put("toolName", tu.toolName());
                if (tu.args() != null) {
                    m.put("args", tu.args());
                }
                if (tu.partialResult() != null) {
                    m.put("partialResult", tu.partialResult());
                }
                return MAPPER.writeValueAsString(m);
            } else if (event instanceof ToolExecutionEndEvent te) {
                var m = new LinkedHashMap<String, Object>();
                m.put("type", "tool_end");
                m.put("toolCallId", te.toolCallId());
                m.put("toolName", te.toolName());
                m.put("isError", te.isError());
                if (te.result() != null) {
                    m.put("result", te.result());
                }
                return MAPPER.writeValueAsString(m);
            } else if (event instanceof AgentEndEvent ae) {
                var m = new LinkedHashMap<String, Object>();
                m.put("type", "done");
                m.put("conversation_id", conversationId);
                // Pull the last AssistantMessage out of the turn's message history
                // and surface its final text + usage + stopReason, so frontends don't
                // have to track assistant bubbles themselves. In multi-turn tool
                // loops (A → tool → A → tool → A), this is always the last A.
                AssistantMessage last = findLastAssistantMessage(ae.messages());
                if (last != null) {
                    String finalText = extractText(last);
                    if (!finalText.isEmpty()) {
                        m.put("finalText", finalText);
                    }
                    if (last.usage() != null) {
                        m.put("usage", last.usage());
                    }
                    if (last.stopReason() != null) {
                        m.put("stopReason", last.stopReason().value());
                    }
                }
                return MAPPER.writeValueAsString(m);
            }
            return null;
        } catch (Exception e) {
            log.warn("Failed to serialize WS event: {}", event.getClass().getSimpleName(), e);
            return null;
        }
    }

    // =========================================================================
    // Emit helpers
    // =========================================================================

    private void emitResponse(Sinks.Many<String> out, String id, boolean success, Object data) {
        try {
            var frame = new LinkedHashMap<String, Object>();
            frame.put("type", "response");
            if (id != null) {
                frame.put("id", id);
            }
            frame.put("success", success);
            if (success) {
                if (data != null) {
                    frame.put("data", data);
                }
            } else {
                frame.put("error", data instanceof String s ? s : String.valueOf(data));
            }
            out.emitNext(MAPPER.writeValueAsString(frame), BUSY_LOOP);
        } catch (Exception e) {
            log.warn("Failed to serialize WS response frame", e);
        }
    }

    private void emitErrorFrame(Sinks.Many<String> out, String conversationId, String error) {
        String frame = serializeErrorFrame(conversationId, error);
        if (frame != null) {
            out.emitNext(frame, BUSY_LOOP);
        }
    }

    private String serializeErrorFrame(String conversationId, String error) {
        try {
            var frame = new LinkedHashMap<String, Object>();
            frame.put("type", "error");
            frame.put("error", error);
            frame.put("conversation_id", conversationId);
            return MAPPER.writeValueAsString(frame);
        } catch (Exception e) {
            log.warn("Failed to serialize WS error frame", e);
            return null;
        }
    }

    // =========================================================================
    // Done-frame enrichment helpers
    // =========================================================================

    /**
     * Returns the last {@link AssistantMessage} in the given message list, or
     * {@code null} if there is none. In a multi-turn tool loop the agent's
     * message history alternates assistant → toolResult → assistant → …, and
     * the frontend's "final answer" is always the last assistant entry.
     */
    private static AssistantMessage findLastAssistantMessage(List<Message> messages) {
        if (messages == null) {
            return null;
        }
        AssistantMessage last = null;
        for (Message m : messages) {
            if (m instanceof AssistantMessage am) {
                last = am;
            }
        }
        return last;
    }

    /**
     * Concatenates all {@link TextContent} blocks of an assistant message into
     * a single string (thinking / tool-call blocks are skipped).
     */
    private static String extractText(AssistantMessage msg) {
        if (msg.content() == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (ContentBlock block : msg.content()) {
            if (block instanceof TextContent tc && tc.text() != null) {
                sb.append(tc.text());
            }
        }
        return sb.toString();
    }

    /**
     * Serializes a {@link Message} to a {@link JsonNode} using the typed writer
     * so that the {@code "role":"user|assistant|toolResult"} discriminator is
     * preserved. Returns {@code null} on failure (caller should skip emission).
     */
    private static JsonNode messageToNode(Message msg) {
        if (msg == null) {
            return null;
        }
        try {
            return MAPPER.readTree(MESSAGE_WRITER.writeValueAsString(msg));
        } catch (Exception e) {
            log.warn("Failed to serialize Message to JsonNode", e);
            return null;
        }
    }

    /** Maps a list of messages through {@link #messageToNode} into a JSON array. */
    private static JsonNode messagesToNode(List<Message> msgs) {
        var arr = MAPPER.createArrayNode();
        if (msgs == null) {
            return arr;
        }
        for (Message m : msgs) {
            JsonNode node = messageToNode(m);
            if (node != null) {
                arr.add(node);
            }
        }
        return arr;
    }
}
