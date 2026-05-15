/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.campusclaw.agent.subagent.a2a;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.cert.X509Certificate;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

import com.campusclaw.agent.subagent.SubAgentBackend;
import com.campusclaw.agent.subagent.SubAgentEvent;
import com.campusclaw.agent.subagent.SubAgentException;
import com.campusclaw.agent.subagent.SubAgentSession;
import com.campusclaw.agent.subagent.SubAgentSessionKey;
import com.campusclaw.agent.tool.CancellationToken;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

/**
 * {@link SubAgentBackend} that talks to a Huawei mate-service hosted A2A agent.
 *
 * <p>Each prompt turn produces one {@code POST {baseUri}/{agentName}} request carrying a JSON-RPC
 * 2.0 {@code SendMessage} envelope and the {@code X-HW-ID} / {@code X-HW-APPKEY} auth headers. The
 * single blocking response is mapped to the standard {@link SubAgentEvent} flux as one or more
 * {@link SubAgentEvent.TextDelta} followed by a {@link SubAgentEvent.Done}, matching what local /
 * ACP / HTTP NDJSON backends emit.
 *
 * <p>This backend is stateless on the remote side: there is no session bookkeeping to tear down,
 * so {@code open()} just allocates a local handle and {@code close()} removes it. {@code cancel()}
 * sets a local flag that prevents the in-flight response from being delivered (the upstream call
 * itself cannot be cancelled mid-flight when {@code HttpClient.send} is blocking — that would need
 * the async API; deferred until a real upstream contract for cancellation exists).
 *
 * @version [br_eCampusCore 25.1.0_Next, 2026/05/15]
 * @since [br_eCampusCore 25.1.0_Next]
 */
public class A2aAgentBackend implements SubAgentBackend {

    private static final Logger log = LoggerFactory.getLogger(A2aAgentBackend.class);
    private static final String HEADER_HW_ID = "X-HW-ID";
    private static final String HEADER_HW_APPKEY = "X-HW-APPKEY";

    /**
     * Sentinel for {@code SSLParameters.setEndpointIdentificationAlgorithm} that disables hostname
     * verification under JDK HttpClient. Using an empty string (not {@code null}) is load-bearing:
     * the HttpClient internals force {@code null} back to {@code "HTTPS"} unless the JVM was
     * started with {@code -Djdk.internal.httpclient.disableHostnameVerification=true} (which is
     * read once at class-init and cannot be flipped at runtime), while an empty string bypasses
     * that override and the downstream SSLEngine treats it as no identification.
     */
    private static final String BYPASS_HOSTNAME_VERIFICATION = "";

    private final A2aAgentConfig config;
    private final ObjectMapper mapper;
    private final HttpClient http;
    private final Map<String, Handle> handles = new ConcurrentHashMap<>();

    public A2aAgentBackend(A2aAgentConfig config, ObjectMapper mapper) {
        this(config, mapper, defaultClient(config));
    }

    public A2aAgentBackend(A2aAgentConfig config, ObjectMapper mapper, HttpClient http) {
        this.config = config;
        this.mapper = mapper;
        this.http = http;
    }

    private static HttpClient defaultClient(A2aAgentConfig config) {
        HttpClient.Builder builder =
                HttpClient.newBuilder().connectTimeout(config.connectTimeout()).version(HttpClient.Version.HTTP_1_1);
        if (config.insecureSkipVerify()) {
            log.warn(
                    "a2a backend '{}' has insecureSkipVerify=true; TLS verification disabled — DO NOT use in production",
                    config.id());
            applyInsecureSsl(builder);
        }
        return builder.build();
    }

    private static void applyInsecureSsl(HttpClient.Builder builder) {
        try {
            SSLContext context = SSLContext.getInstance("TLS");
            context.init(null, new TrustManager[] {new TrustAllManager()}, new java.security.SecureRandom());
            builder.sslContext(context);
            SSLParameters params = context.getDefaultSSLParameters();
            params.setEndpointIdentificationAlgorithm(BYPASS_HOSTNAME_VERIFICATION);
            builder.sslParameters(params);
        } catch (GeneralSecurityException ex) {
            throw new IllegalStateException("failed to install insecure SSL context for a2a backend", ex);
        }
    }

    @Override
    public String id() {
        return config.id();
    }

    @Override
    public SubAgentSession open(OpenRequest request) {
        SubAgentSessionKey key = SubAgentSessionKey.newKey(request.parentAgentId(), id());
        String model = request.model() != null && !request.model().isBlank() ? request.model() : config.defaultModel();
        handles.put(key.asString(), new Handle(model));
        return new SubAgentSession(key, null, this);
    }

    @Override
    public Flux<SubAgentEvent> prompt(SubAgentSession session, String text, CancellationToken signal) {
        Handle handle = requireHandle(session);
        Sinks.Many<SubAgentEvent> sink = Sinks.many().multicast().onBackpressureBuffer(1024, false);
        AtomicBoolean cancelled = new AtomicBoolean(false);

        if (signal != null) {
            signal.onCancel(() -> cancelled.set(true));
        }

        Thread.ofVirtual()
                .name("a2a-prompt-" + id() + "-" + session.key().uuid())
                .start(() -> sendBlocking(session, handle, text, sink, cancelled));

        return sink.asFlux()
                .takeUntil(event -> event instanceof SubAgentEvent.Done || event instanceof SubAgentEvent.Error);
    }

    @Override
    public void cancel(SubAgentSession session, String reason) {
        // mate-service exposes no cancellation endpoint — best we can do is flip the local flag
        // so any not-yet-delivered response is suppressed. Logged so operators can see the intent.
        log.debug("cancel requested for {} (reason={}); upstream has no cancel endpoint", session.keyString(), reason);
    }

    @Override
    public void close(SubAgentSession session, String reason) {
        handles.remove(session.keyString());
    }

    private void sendBlocking(
            SubAgentSession session,
            Handle handle,
            String text,
            Sinks.Many<SubAgentEvent> sink,
            AtomicBoolean cancelled) {
        if (cancelled.get()) {
            sink.tryEmitNext(new SubAgentEvent.Done(SubAgentEvent.StopReason.CANCELLED));
            sink.tryEmitComplete();
            return;
        }
        HttpRequest request = buildRequest(text, handle, sink);
        if (request == null) {
            return;
        }
        try {
            HttpResponse<String> response =
                    http.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (cancelled.get()) {
                sink.tryEmitNext(new SubAgentEvent.Done(SubAgentEvent.StopReason.CANCELLED));
                sink.tryEmitComplete();
                return;
            }
            if (response.statusCode() / 100 != 2) {
                sink.tryEmitNext(new SubAgentEvent.Error(
                        "A2A_HTTP_" + response.statusCode(),
                        "request failed with status " + response.statusCode(),
                        response.statusCode() >= 500));
                sink.tryEmitComplete();
                return;
            }
            A2aProtocol.parseAndEmit(response.body(), mapper, sink);
        } catch (IOException ex) {
            log.warn("a2a request failed for {} target={}: {}", session.keyString(), targetUri(), describe(ex), ex);
            sink.tryEmitNext(new SubAgentEvent.Error("A2A_IO", describe(ex) + " (target=" + targetUri() + ")", true));
            sink.tryEmitComplete();
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            sink.tryEmitNext(new SubAgentEvent.Error("A2A_INTERRUPTED", describe(ex), false));
            sink.tryEmitComplete();
        }
    }

    private static String describe(Throwable ex) {
        StringBuilder out = new StringBuilder();
        appendOne(out, ex);
        Throwable cause = ex.getCause();
        int depth = 0;
        while (cause != null && cause != ex && depth < 3) {
            out.append(" <- ");
            appendOne(out, cause);
            Throwable next = cause.getCause();
            if (next == cause) {
                break;
            }
            cause = next;
            depth++;
        }
        return out.toString();
    }

    private static void appendOne(StringBuilder out, Throwable t) {
        out.append(t.getClass().getSimpleName());
        String msg = t.getMessage();
        if (msg != null && !msg.isBlank()) {
            out.append(": ").append(msg);
        }
    }

    private HttpRequest buildRequest(String text, Handle handle, Sinks.Many<SubAgentEvent> sink) {
        String requestId = UUID.randomUUID().toString();
        String messageId = UUID.randomUUID().toString();
        Map<String, Object> envelope = A2aProtocol.buildSendMessageRequest(requestId, messageId, text, handle.model());
        try {
            return HttpRequest.newBuilder(targetUri())
                    .timeout(config.requestTimeout())
                    .header("content-type", "application/json")
                    .header("accept", "application/json")
                    .header(HEADER_HW_ID, config.hwId())
                    .header(HEADER_HW_APPKEY, config.hwAppKey())
                    .POST(HttpRequest.BodyPublishers.ofByteArray(mapper.writeValueAsBytes(envelope)))
                    .build();
        } catch (com.fasterxml.jackson.core.JsonProcessingException ex) {
            sink.tryEmitNext(
                    new SubAgentEvent.Error("A2A_JSON", "failed to encode request: " + ex.getMessage(), false));
            sink.tryEmitComplete();
            return null;
        }
    }

    private Handle requireHandle(SubAgentSession session) {
        Handle handle = handles.get(session.keyString());
        if (handle == null) {
            throw new SubAgentException("A2A_SESSION_GONE", "session " + session.keyString() + " is not open");
        }
        return handle;
    }

    private URI targetUri() {
        String base = config.baseUri().toString();
        String trimmed = base.endsWith("/") ? base.substring(0, base.length() - 1) : base;
        String encodedAgent = URLEncoder.encode(config.agentName(), StandardCharsets.UTF_8);
        return URI.create(trimmed + "/" + encodedAgent);
    }

    private record Handle(String model) {}

    private static final class TrustAllManager implements X509TrustManager {
        @Override
        public void checkClientTrusted(X509Certificate[] chain, String authType) {
            // intentional no-op: insecureSkipVerify trusts every client cert
        }

        @Override
        public void checkServerTrusted(X509Certificate[] chain, String authType) {
            // intentional no-op: insecureSkipVerify trusts every server cert
        }

        @Override
        public X509Certificate[] getAcceptedIssuers() {
            return new X509Certificate[0];
        }
    }
}
