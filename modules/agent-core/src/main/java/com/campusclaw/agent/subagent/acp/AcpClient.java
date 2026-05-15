/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.campusclaw.agent.subagent.acp;

import java.io.InputStream;
import java.io.OutputStream;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BiFunction;

import com.campusclaw.agent.subagent.SubAgentEvent;
import com.campusclaw.agent.subagent.SubAgentException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;
import reactor.core.publisher.Sinks.EmitFailureHandler;

/**
 * JSON-RPC client driving an ACP server over an {@link AcpTransport}. Performs the
 * {@code initialize} → {@code session/new} → {@code session/prompt} handshake and translates
 * inbound {@code session/update} notifications into a {@link Flux} of {@link SubAgentEvent}.
 *
 * <p>One client manages one ACP process and one session.
 *
 * @version [br_eCampusCore 25.1.0_Next, 2026/05/12]
 * @since [br_eCampusCore 25.1.0_Next]
 */
public class AcpClient implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(AcpClient.class);

    /**
     * Busy-loop retry handler for concurrent emits to {@link #events}. Reader thread emits all
     * events on this sink (including the synthesized {@code Done} produced from a
     * {@code session/prompt} response — see {@link #handleResponse}); downstream subscribers that
     * call {@code emitNext} on a derived sink (e.g. backend bridge) may briefly collide with the
     * reader, so we retry rather than dropping.
     */
    private static final EmitFailureHandler RETRY_NON_SERIALIZED =
            EmitFailureHandler.busyLooping(java.time.Duration.ofMillis(50L));

    /**
     * Tracks an in-flight JSON-RPC request so {@link #handleResponse} can dispatch by method.
     *
     * @param method JSON-RPC method name (e.g. {@code session/prompt})
     * @param future future completed when the matching response envelope arrives
     */
    private record Pending(String method, CompletableFuture<AcpProtocol.Envelope> future) {}

    private final ObjectMapper mapper;
    private final AcpTransport transport;
    private final AtomicLong nextRequestId = new AtomicLong(1L);
    private final Map<Long, Pending> pending = new ConcurrentHashMap<>();
    private final Sinks.Many<SubAgentEvent> events = Sinks.many().multicast().onBackpressureBuffer(1024, false);

    private volatile String sessionId;
    private volatile PermissionHandler permissionHandler =
            (request, ctx) -> AcpProtocol.RequestPermissionResponse.Outcome.cancelled();

    /**
     * Resolves a permission request from the ACP server into an outcome to return on the wire.
     */
    @FunctionalInterface
    public interface PermissionHandler {

        AcpProtocol.RequestPermissionResponse.Outcome resolve(
                AcpProtocol.RequestPermissionRequest request, PermissionContext ctx);
    }

    /**
     * Context made available to {@link PermissionHandler} implementations.
     */
    public record PermissionContext(String sessionId) {}

    public AcpClient(ObjectMapper mapper, InputStream input, OutputStream output) {
        this.mapper = mapper;
        this.transport = new AcpTransport(mapper, input, output, this::onEnvelope, this::onTransportError);
    }

    public void start(String name) {
        transport.start(name);
    }

    public void setPermissionHandler(PermissionHandler handler) {
        if (handler == null) {
            throw new IllegalArgumentException("handler must not be null");
        }
        this.permissionHandler = handler;
    }

    public Flux<SubAgentEvent> events() {
        return events.asFlux();
    }

    public AcpProtocol.InitializeResponse initialize(String clientName, String clientVersion, Duration timeout) {
        var request = new AcpProtocol.InitializeRequest(
                AcpProtocol.PROTOCOL_VERSION,
                AcpProtocol.ClientCapabilities.none(),
                new AcpProtocol.ClientInfo(clientName, clientVersion));
        AcpProtocol.Envelope reply = call(AcpProtocol.METHOD_INITIALIZE, request, timeout);
        return mapper.convertValue(reply.result(), AcpProtocol.InitializeResponse.class);
    }

    public String newSession(String cwd, Duration timeout) {
        var request = new AcpProtocol.NewSessionRequest(cwd, List.of());
        AcpProtocol.Envelope reply = call(AcpProtocol.METHOD_NEW_SESSION, request, timeout);
        var response = mapper.convertValue(reply.result(), AcpProtocol.NewSessionResponse.class);
        this.sessionId = response.sessionId();
        return response.sessionId();
    }

    public AcpStopReason prompt(String text, Duration timeout) {
        if (sessionId == null) {
            throw new SubAgentException("ACP_NO_SESSION", "session/new must be called before prompt");
        }
        var request = new AcpProtocol.PromptRequest(sessionId, List.of(AcpProtocol.ContentBlock.text(text)));
        AcpProtocol.Envelope reply = call(AcpProtocol.METHOD_PROMPT, request, timeout);
        var response = mapper.convertValue(reply.result(), AcpProtocol.PromptResponse.class);
        return AcpStopReason.fromWire(response.stopReason());
    }

    public void cancel() {
        if (sessionId == null) {
            return;
        }
        try {
            JsonNode params = mapper.valueToTree(new AcpProtocol.CancelRequest(sessionId));
            transport.send(AcpProtocol.Envelope.notification(AcpProtocol.METHOD_CANCEL, params));
        } catch (RuntimeException ex) {
            log.debug("cancel notification failed: {}", ex.toString());
        }
    }

    @Override
    public void close() {
        AcpTransport.note("AcpClient.close enter pendingCount=" + pending.size());
        events.emitComplete(RETRY_NON_SERIALIZED);
        AcpTransport.note("AcpClient.close events.emitComplete done");
        transport.close();
        AcpTransport.note("AcpClient.close transport.close done");
        pending.values().forEach(p -> p.future()
                .completeExceptionally(new SubAgentException("ACP_CLOSED", "transport closed")));
        pending.clear();
        AcpTransport.note("AcpClient.close exit");
    }

    private AcpProtocol.Envelope call(String method, Object params, Duration timeout) {
        long id = nextRequestId.getAndIncrement();
        var future = new CompletableFuture<AcpProtocol.Envelope>();
        pending.put(id, new Pending(method, future));
        try {
            JsonNode tree = mapper.valueToTree(params);
            transport.send(AcpProtocol.Envelope.request(id, method, tree));
        } catch (RuntimeException ex) {
            pending.remove(id);
            throw ex;
        }
        return awaitResponse(id, method, future, timeout);
    }

    private AcpProtocol.Envelope awaitResponse(
            long id, String method, CompletableFuture<AcpProtocol.Envelope> future, Duration timeout) {
        try {
            AcpProtocol.Envelope envelope =
                    timeout == null ? future.get() : future.get(timeout.toMillis(), TimeUnit.MILLISECONDS);
            if (envelope.error() != null) {
                throw new SubAgentException(
                        "ACP_ERROR_" + envelope.error().code(),
                        method + " failed: " + envelope.error().message());
            }
            return envelope;
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new SubAgentException("ACP_INTERRUPTED", method + " interrupted", ex);
        } catch (java.util.concurrent.TimeoutException ex) {
            pending.remove(id);
            throw new SubAgentException("ACP_TIMEOUT", method + " timed out", ex);
        } catch (java.util.concurrent.ExecutionException ex) {
            Throwable cause = ex.getCause() == null ? ex : ex.getCause();
            if (cause instanceof SubAgentException sae) {
                throw sae;
            }
            throw new SubAgentException("ACP_FAILED", method + " failed: " + cause.getMessage(), cause);
        }
    }

    private void onEnvelope(AcpProtocol.Envelope envelope) {
        if (envelope.isResponse()) {
            handleResponse(envelope);
        } else if (envelope.isNotification()) {
            handleNotification(envelope);
        } else if (envelope.isRequest()) {
            handleInboundRequest(envelope);
        }
    }

    private void handleResponse(AcpProtocol.Envelope envelope) {
        Object rawId = envelope.id();
        if (!(rawId instanceof Number n)) {
            return;
        }
        long id = n.longValue();
        Pending p = pending.remove(id);
        if (p == null) {
            return;
        }

        // For session/prompt responses, synthesize and emit Done from the reader thread *before*
        // completing the future. The reader thread processes session/update notifications and the
        // response in wire order; emitting Done here guarantees all preceding TextDelta events have
        // already been pushed to the sink, so downstream operators that use takeUntil(Done) never
        // truncate the final agent_message_chunk. Previously Done was emitted by the prompt thread
        // after future.complete unparked it, which on Windows could outrace a final TextDelta that
        // the reader thread had not yet processed.
        if (AcpProtocol.METHOD_PROMPT.equals(p.method()) && envelope.error() == null && envelope.result() != null) {
            try {
                var response = mapper.convertValue(envelope.result(), AcpProtocol.PromptResponse.class);
                AcpStopReason stop = AcpStopReason.fromWire(response.stopReason());
                AcpTransport.note("AcpClient.emit Done(stopReason=" + stop + ") [reader-thread]");
                events.emitNext(new SubAgentEvent.Done(stop.toSubAgent()), RETRY_NON_SERIALIZED);
                AcpTransport.note("AcpClient.emit-done Done(stopReason=" + stop + ") [reader-thread]");
            } catch (RuntimeException ex) {
                AcpTransport.note("AcpClient.handleResponse Done-emit threw: " + ex);
                log.warn("failed to synthesize Done from prompt response: {}", ex.toString());
            }
        }
        p.future().complete(envelope);
    }

    private void handleNotification(AcpProtocol.Envelope envelope) {
        if (!AcpProtocol.METHOD_UPDATE.equals(envelope.method())) {
            return;
        }
        try {
            var update = mapper.convertValue(envelope.params(), AcpProtocol.UpdateNotification.class);
            JsonNode raw = update.update();
            String tag = raw != null && raw.has("sessionUpdate")
                    ? raw.get("sessionUpdate").asText("")
                    : "<missing>";
            if (log.isDebugEnabled()) {
                log.debug("session/update tag={} payload={}", tag, raw);
            }
            SubAgentEvent event = AcpEventMapper.toSubAgentEvent(update.update(), mapper);
            if (event == null) {
                AcpTransport.note("AcpClient.mapper returned null for tag=" + tag);
                return;
            }
            String summary = event.getClass().getSimpleName();
            if (event instanceof SubAgentEvent.TextDelta td) {
                summary += "(stream=" + td.stream() + ",len=" + td.text().length() + ")";
            }
            AcpTransport.note("AcpClient.emit " + summary);
            events.emitNext(event, RETRY_NON_SERIALIZED);
            AcpTransport.note("AcpClient.emit-done " + summary);
        } catch (RuntimeException ex) {
            AcpTransport.note("AcpClient.handleNotification threw: " + ex);
            log.warn("failed to decode session/update: {}", ex.toString());
        }
    }

    private void handleInboundRequest(AcpProtocol.Envelope envelope) {
        if (AcpProtocol.METHOD_REQUEST_PERMISSION.equals(envelope.method())) {
            answerPermission(envelope);
            return;
        }
        sendError(envelope.id(), AcpProtocol.Error.METHOD_NOT_FOUND, "method not supported: " + envelope.method());
    }

    private void answerPermission(AcpProtocol.Envelope envelope) {
        try {
            var request = mapper.convertValue(envelope.params(), AcpProtocol.RequestPermissionRequest.class);
            BiFunction<
                            AcpProtocol.RequestPermissionRequest,
                            PermissionContext,
                            AcpProtocol.RequestPermissionResponse.Outcome>
                    handler = permissionHandler::resolve;
            AcpProtocol.RequestPermissionResponse.Outcome outcome =
                    handler.apply(request, new PermissionContext(sessionId));
            var response = new AcpProtocol.RequestPermissionResponse(outcome);
            transport.send(AcpProtocol.Envelope.ok(envelope.id(), mapper.valueToTree(response)));
        } catch (RuntimeException ex) {
            sendError(envelope.id(), AcpProtocol.Error.INTERNAL_ERROR, ex.getMessage());
        }
    }

    private void sendError(Object id, int code, String message) {
        transport.send(AcpProtocol.Envelope.fail(id, new AcpProtocol.Error(code, message, null)));
    }

    private void onTransportError(Throwable cause) {
        events.emitNext(
                new SubAgentEvent.Error("ACP_TRANSPORT", String.valueOf(cause.getMessage()), false),
                RETRY_NON_SERIALIZED);
        events.emitComplete(RETRY_NON_SERIALIZED);
        pending.values().forEach(p -> p.future().completeExceptionally(cause));
        pending.clear();
    }
}
