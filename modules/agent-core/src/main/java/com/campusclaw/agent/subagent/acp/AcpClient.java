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

    private final ObjectMapper mapper;
    private final AcpTransport transport;
    private final AtomicLong nextRequestId = new AtomicLong(1L);
    private final Map<Long, CompletableFuture<AcpProtocol.Envelope>> pending = new ConcurrentHashMap<>();
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

    /** Context made available to {@link PermissionHandler} implementations. */
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
        AcpStopReason stop = AcpStopReason.fromWire(response.stopReason());
        events.tryEmitNext(new SubAgentEvent.Done(stop.toSubAgent()));
        return stop;
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
        events.tryEmitComplete();
        transport.close();
        pending.values()
                .forEach(future ->
                        future.completeExceptionally(new SubAgentException("ACP_CLOSED", "transport closed")));
        pending.clear();
    }

    private AcpProtocol.Envelope call(String method, Object params, Duration timeout) {
        long id = nextRequestId.getAndIncrement();
        var future = new CompletableFuture<AcpProtocol.Envelope>();
        pending.put(id, future);
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
        CompletableFuture<AcpProtocol.Envelope> future = pending.remove(id);
        if (future != null) {
            future.complete(envelope);
        }
    }

    private void handleNotification(AcpProtocol.Envelope envelope) {
        if (!AcpProtocol.METHOD_UPDATE.equals(envelope.method())) {
            return;
        }
        try {
            var update = mapper.convertValue(envelope.params(), AcpProtocol.UpdateNotification.class);
            SubAgentEvent event = AcpEventMapper.toSubAgentEvent(update.update(), mapper);
            if (event != null) {
                events.tryEmitNext(event);
            }
        } catch (RuntimeException ex) {
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
        events.tryEmitNext(new SubAgentEvent.Error("ACP_TRANSPORT", String.valueOf(cause.getMessage()), false));
        events.tryEmitComplete();
        pending.values().forEach(future -> future.completeExceptionally(cause));
        pending.clear();
    }
}
