/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.hicampus.mate.matecampusclaw.codingagent.mode.server;

import java.util.Map;

import com.huawei.hicampus.mate.matecampusclaw.agent.Agent;
import com.huawei.hicampus.mate.matecampusclaw.agent.event.AgentEndEvent;
import com.huawei.hicampus.mate.matecampusclaw.agent.event.AgentEvent;
import com.huawei.hicampus.mate.matecampusclaw.agent.event.MessageEndEvent;
import com.huawei.hicampus.mate.matecampusclaw.agent.event.MessageStartEvent;
import com.huawei.hicampus.mate.matecampusclaw.agent.event.MessageUpdateEvent;
import com.huawei.hicampus.mate.matecampusclaw.agent.event.ToolExecutionEndEvent;
import com.huawei.hicampus.mate.matecampusclaw.agent.event.ToolExecutionStartEvent;
import com.huawei.hicampus.mate.matecampusclaw.ai.types.ThinkingLevel;
import com.huawei.hicampus.mate.matecampusclaw.codingagent.session.AgentSession;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.lang.Nullable;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Handles POST /api/chat — streams agent responses as Server-Sent Events.
 *
 * <p>Supports multiple independent conversations via {@code conversation_id}.
 * Each conversation has its own {@link AgentSession} managed by a {@link SessionPool}.
 *
 * @version [br_eCampusCore 25.1.0_Next, 2026/05/06]
 * @since [br_eCampusCore 25.1.0_Next]
 */
public class ChatHandler {

    private static final Logger log = LoggerFactory.getLogger(ChatHandler.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final SessionPool pool;

    public ChatHandler(SessionPool pool) {
        this.pool = pool;
    }

    record ChatRequest(
            @JsonProperty("message") String message,
            @JsonProperty("conversation_id") @Nullable String conversationId,
            @JsonProperty("model") @Nullable String model,
            @JsonProperty("thinking") @Nullable String thinking) {}

    public Mono<ServerResponse> chat(ServerRequest request) {
        return request.bodyToMono(ChatRequest.class)
                .flatMap(this::handleChat)
                .onErrorResume(Exception.class, e -> ServerResponse.status(500)
                        .bodyValue(Map.of("error", Agent.formatError(e))));
    }

    private Mono<ServerResponse> handleChat(ChatRequest req) {
        if (req.message() == null || req.message().isBlank()) {
            return ServerResponse.badRequest().bodyValue(Map.of("error", "message is required"));
        }
        SessionPool.SessionRef ref = pool.getOrCreate(req.conversationId());
        AgentSession session = ref.session();
        String conversationId = ref.conversationId();
        if (session.isStreaming()) {
            return ServerResponse.status(409)
                    .bodyValue(Map.of(
                            "error",
                            "This conversation is already processing a prompt",
                            "conversation_id",
                            conversationId));
        }
        Mono<ServerResponse> override = applyPerRequestOverrides(req, session);
        if (override != null) {
            return override;
        }
        Flux<ServerSentEvent<String>> events = Flux.create(sink -> wireSink(sink, session, req, conversationId));
        return ServerResponse.ok().contentType(MediaType.TEXT_EVENT_STREAM).body(events, ServerSentEvent.class);
    }

    // Returns a 400 response on invalid model/thinking override; null when overrides are accepted (or absent).
    private static Mono<ServerResponse> applyPerRequestOverrides(ChatRequest req, AgentSession session) {
        if (req.model() != null && !req.model().isBlank()) {
            try {
                session.setModel(req.model());
            } catch (Exception e) {
                return ServerResponse.badRequest().bodyValue(Map.of("error", "Invalid model: " + e.getMessage()));
            }
        }
        if (req.thinking() != null && !req.thinking().isBlank()) {
            try {
                session.getAgent().setThinkingLevel(ThinkingLevel.fromValue(req.thinking()));
            } catch (Exception e) {
                return ServerResponse.badRequest()
                        .bodyValue(Map.of("error", "Invalid thinking level: " + e.getMessage()));
            }
        }
        return null;
    }

    private void wireSink(
            reactor.core.publisher.FluxSink<ServerSentEvent<String>> sink,
            AgentSession session,
            ChatRequest req,
            String conversationId) {
        Runnable unsub = session.subscribe(event -> forwardEvent(sink, event, conversationId));
        sink.onDispose(unsub::run);
        sink.onCancel(() -> {
            if (session.isStreaming()) {
                session.abort();
                log.info("Aborted conversation {} due to client disconnect", conversationId);
            }
        });
        session.prompt(req.message()).whenComplete((v, ex) -> {
            if (ex == null) {
                return;
            }
            try {
                sink.next(sse(
                        "error",
                        MAPPER.writeValueAsString(
                                Map.of("error", Agent.formatError(ex), "conversation_id", conversationId))));
            } catch (Exception e) {
                // sink will be completed below regardless — log so diagnostics survive
                log.warn("failed to serialize SSE error event for conversation {}", conversationId, e);
            }
            sink.complete();
        });
    }

    private void forwardEvent(
            reactor.core.publisher.FluxSink<ServerSentEvent<String>> sink, AgentEvent event, String conversationId) {
        try {
            if (event instanceof MessageStartEvent) {
                sink.next(sse("message_start", MAPPER.writeValueAsString(Map.of("conversation_id", conversationId))));
            } else if (event instanceof MessageUpdateEvent mu) {
                var msg = mu.message();
                if (msg != null) {
                    sink.next(sse("message_update", MAPPER.writeValueAsString(Map.of("message", msg))));
                }
            } else if (event instanceof MessageEndEvent me) {
                var msg = me.message();
                sink.next(sse("message_end", MAPPER.writeValueAsString(Map.of("message", msg != null ? msg : ""))));
            } else if (event instanceof ToolExecutionStartEvent te) {
                sink.next(sse(
                        "tool_start",
                        MAPPER.writeValueAsString(Map.of("toolName", te.toolName(), "toolCallId", te.toolCallId()))));
            } else if (event instanceof ToolExecutionEndEvent te) {
                sink.next(sse("tool_end", MAPPER.writeValueAsString(Map.of("toolCallId", te.toolCallId()))));
            } else if (event instanceof AgentEndEvent) {
                sink.next(sse("done", MAPPER.writeValueAsString(Map.of("conversation_id", conversationId))));
                sink.complete();
            }
        } catch (Exception e) {
            log.warn("Failed to serialize SSE event", e);
        }
    }

    private static ServerSentEvent<String> sse(String eventType, String data) {
        return ServerSentEvent.<String>builder().event(eventType).data(data).build();
    }
}
