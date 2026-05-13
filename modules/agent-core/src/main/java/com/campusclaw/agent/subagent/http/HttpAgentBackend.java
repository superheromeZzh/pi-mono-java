/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.campusclaw.agent.subagent.http;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

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
 * {@link SubAgentBackend} backed by a remote HTTP agent that speaks the CampusClaw HTTP sub-agent
 * protocol (see {@link HttpAgentProtocol}).
 *
 * <p>One backend instance can host many concurrent sessions; each session holds a single in-flight
 * prompt at a time.
 *
 * @version [br_eCampusCore 25.1.0_Next, 2026/05/12]
 * @since [br_eCampusCore 25.1.0_Next]
 */
public class HttpAgentBackend implements SubAgentBackend {

    private static final Logger log = LoggerFactory.getLogger(HttpAgentBackend.class);

    private final HttpAgentConfig config;
    private final ObjectMapper mapper;
    private final HttpClient http;
    private final Map<String, Handle> handles = new ConcurrentHashMap<>();

    public HttpAgentBackend(HttpAgentConfig config, ObjectMapper mapper) {
        this(config, mapper, defaultClient(config));
    }

    public HttpAgentBackend(HttpAgentConfig config, ObjectMapper mapper, HttpClient http) {
        this.config = config;
        this.mapper = mapper;
        this.http = http;
    }

    private static HttpClient defaultClient(HttpAgentConfig config) {
        return HttpClient.newBuilder()
                .connectTimeout(config.connectTimeout())
                .version(HttpClient.Version.HTTP_1_1)
                .build();
    }

    @Override
    public String id() {
        return config.id();
    }

    @Override
    public SubAgentSession open(OpenRequest request) {
        var key = SubAgentSessionKey.newKey(request.parentAgentId(), id());
        var body = new HttpAgentProtocol.NewSessionRequest(
                request.parentAgentId(), request.cwd(), request.model(), request.thinking());
        try {
            HttpResponse<String> response = http.send(
                    builder(uri("/sessions"))
                            .timeout(config.requestTimeout())
                            .POST(jsonBody(body))
                            .header("content-type", "application/json")
                            .build(),
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            ensureSuccess(response, "open");
            var parsed = mapper.readValue(response.body(), HttpAgentProtocol.NewSessionResponse.class);
            handles.put(key.asString(), new Handle(parsed.sessionId()));
            return new SubAgentSession(key, parsed.sessionId(), this);
        } catch (IOException ex) {
            throw new SubAgentException("HTTP_OPEN_IO", "failed to open session", ex);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new SubAgentException("HTTP_OPEN_INTERRUPTED", "interrupted while opening session", ex);
        }
    }

    @Override
    public Flux<SubAgentEvent> prompt(SubAgentSession session, String text, CancellationToken signal) {
        Handle handle = requireHandle(session);
        Sinks.Many<SubAgentEvent> sink = Sinks.many().multicast().onBackpressureBuffer(1024, false);
        var cancelled = new AtomicBoolean(false);

        if (signal != null) {
            signal.onCancel(() -> {
                cancelled.set(true);
                cancel(session, "parent-cancelled");
            });
        }

        Thread.ofVirtual()
                .name("http-agent-prompt-" + id() + "-" + session.key().uuid())
                .start(() -> streamPrompt(session, handle, text, sink, cancelled));

        return sink.asFlux()
                .takeUntil(event -> event instanceof SubAgentEvent.Done || event instanceof SubAgentEvent.Error);
    }

    @Override
    public void cancel(SubAgentSession session, String reason) {
        Handle handle = handles.get(session.keyString());
        if (handle == null) {
            return;
        }
        try {
            http.send(
                    builder(uri("/sessions/" + handle.remoteId + "/cancel"))
                            .timeout(config.requestTimeout())
                            .POST(jsonBody(new HttpAgentProtocol.CancelRequest(reason)))
                            .header("content-type", "application/json")
                            .build(),
                    HttpResponse.BodyHandlers.discarding());
        } catch (IOException | InterruptedException ex) {
            if (ex instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            log.debug("cancel failed for {}: {}", session.keyString(), ex.toString());
        }
    }

    @Override
    public void close(SubAgentSession session, String reason) {
        Handle handle = handles.remove(session.keyString());
        if (handle == null) {
            return;
        }
        try {
            http.send(
                    builder(uri("/sessions/" + handle.remoteId))
                            .timeout(config.requestTimeout())
                            .DELETE()
                            .build(),
                    HttpResponse.BodyHandlers.discarding());
        } catch (IOException | InterruptedException ex) {
            if (ex instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            log.debug("close failed for {}: {}", session.keyString(), ex.toString());
        }
    }

    private void streamPrompt(
            SubAgentSession session,
            Handle handle,
            String text,
            Sinks.Many<SubAgentEvent> sink,
            AtomicBoolean cancelled) {
        HttpRequest request = builder(uri("/sessions/" + handle.remoteId + "/prompt"))
                .timeout(config.promptTimeout())
                .POST(jsonBody(new HttpAgentProtocol.PromptRequest(text)))
                .header("content-type", "application/json")
                .header("accept", "application/x-ndjson")
                .build();
        try {
            HttpResponse<InputStream> response = http.send(request, HttpResponse.BodyHandlers.ofInputStream());
            if (response.statusCode() / 100 != 2) {
                sink.tryEmitNext(new SubAgentEvent.Error(
                        "HTTP_" + response.statusCode(),
                        "prompt failed with status " + response.statusCode(),
                        response.statusCode() >= 500));
                sink.tryEmitComplete();
                return;
            }
            readEventStream(response.body(), sink, cancelled);
        } catch (IOException ex) {
            sink.tryEmitNext(new SubAgentEvent.Error("HTTP_IO", ex.getMessage(), true));
            sink.tryEmitComplete();
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            sink.tryEmitNext(new SubAgentEvent.Error("HTTP_INTERRUPTED", ex.getMessage(), false));
            sink.tryEmitComplete();
        } catch (RuntimeException ex) {
            sink.tryEmitNext(new SubAgentEvent.Error("HTTP_PROMPT", ex.getMessage(), false));
            sink.tryEmitComplete();
        }
    }

    private void readEventStream(InputStream body, Sinks.Many<SubAgentEvent> sink, AtomicBoolean cancelled) {
        try (var reader = new BufferedReader(new InputStreamReader(body, StandardCharsets.UTF_8))) {
            String line;
            boolean sawDone = false;
            while ((line = reader.readLine()) != null) {
                if (cancelled.get()) {
                    sink.tryEmitNext(new SubAgentEvent.Done(SubAgentEvent.StopReason.CANCELLED));
                    sawDone = true;
                    break;
                }
                SubAgentEvent event = HttpAgentProtocol.decodeEvent(line, mapper);
                if (event == null) {
                    continue;
                }
                sink.tryEmitNext(event);
                if (event instanceof SubAgentEvent.Done) {
                    sawDone = true;
                    break;
                }
                if (event instanceof SubAgentEvent.Error) {
                    break;
                }
            }
            if (!sawDone) {
                sink.tryEmitNext(new SubAgentEvent.Done(SubAgentEvent.StopReason.END_TURN));
            }
        } catch (IOException ex) {
            sink.tryEmitNext(new SubAgentEvent.Error("HTTP_STREAM", ex.getMessage(), true));
        } finally {
            sink.tryEmitComplete();
        }
    }

    private Handle requireHandle(SubAgentSession session) {
        Handle handle = handles.get(session.keyString());
        if (handle == null) {
            throw new SubAgentException("HTTP_SESSION_GONE", "session " + session.keyString() + " is not open");
        }
        return handle;
    }

    private HttpRequest.Builder builder(URI uri) {
        var builder = HttpRequest.newBuilder(uri);
        applyAuth(builder);
        return builder;
    }

    private void applyAuth(HttpRequest.Builder builder) {
        switch (config.authType()) {
            case BEARER -> builder.header("authorization", "Bearer " + config.authToken());
            case HEADER -> builder.header(config.authHeaderName(), config.authToken());
            case NONE -> {
                // no-op
            }
        }
    }

    private URI uri(String path) {
        String base = config.baseUri().toString();
        String trimmedBase = base.endsWith("/") ? base.substring(0, base.length() - 1) : base;
        String trimmedPath = path.startsWith("/") ? path : ("/" + path);
        return URI.create(trimmedBase + trimmedPath);
    }

    private HttpRequest.BodyPublisher jsonBody(Object value) {
        try {
            byte[] bytes = mapper.writeValueAsBytes(value);
            return HttpRequest.BodyPublishers.ofByteArray(bytes);
        } catch (com.fasterxml.jackson.core.JsonProcessingException ex) {
            throw new SubAgentException("HTTP_JSON", "failed to encode request body", ex);
        }
    }

    private static void ensureSuccess(HttpResponse<?> response, String op) {
        if (response.statusCode() / 100 != 2) {
            throw new SubAgentException(
                    "HTTP_" + response.statusCode(), op + " returned status " + response.statusCode());
        }
    }

    /**
     * Configures the request timeout this backend uses for non-streaming endpoints.
     *
     * @return the configured per-request timeout
     */
    public Duration requestTimeout() {
        return config.requestTimeout();
    }

    private record Handle(String remoteId) {}
}
