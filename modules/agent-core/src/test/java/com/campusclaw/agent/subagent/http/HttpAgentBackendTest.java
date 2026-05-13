/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.campusclaw.agent.subagent.http;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import com.campusclaw.agent.subagent.SubAgentBackend;
import com.campusclaw.agent.subagent.SubAgentEvent;
import com.campusclaw.agent.subagent.SubAgentSession;
import com.campusclaw.agent.tool.CancellationToken;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class HttpAgentBackendTest {

    private HttpServer server;
    private final ObjectMapper mapper = new ObjectMapper();
    private final ConcurrentLinkedQueue<String> requestLog = new ConcurrentLinkedQueue<>();
    private final AtomicReference<String> capturedAuth = new AtomicReference<>();

    @BeforeEach
    void setUp() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/sessions", new RouteHandler());
        server.setExecutor(java.util.concurrent.Executors.newCachedThreadPool());
        server.start();
    }

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void openPromptCloseFlowsRoundTripWithBearerAuth() throws Exception {
        HttpAgentConfig config = new HttpAgentConfig(
                "test-http",
                URI.create("http://127.0.0.1:" + server.getAddress().getPort()),
                HttpAgentConfig.AuthType.BEARER,
                "secret-123",
                null,
                Duration.ofSeconds(2L),
                Duration.ofSeconds(5L),
                Duration.ofSeconds(10L));
        var backend = new HttpAgentBackend(config, mapper);

        SubAgentSession session = backend.open(
                new SubAgentBackend.OpenRequest("main", "/tmp", null, null, Map.of(), Duration.ofSeconds(30L)));
        assertThat(session.runtimeSessionId()).isEqualTo("remote-1");
        assertThat(capturedAuth.get()).isEqualTo("Bearer secret-123");

        var events = new java.util.ArrayList<SubAgentEvent>();
        var done = new CountDownLatch(1);
        backend.prompt(session, "hello", new CancellationToken())
                .subscribe(events::add, err -> done.countDown(), done::countDown);

        assertThat(done.await(5L, TimeUnit.SECONDS)).isTrue();

        assertThat(events)
                .anyMatch(e ->
                        e instanceof SubAgentEvent.TextDelta td && td.text().equals("first "));
        assertThat(events)
                .anyMatch(e ->
                        e instanceof SubAgentEvent.TextDelta td && td.text().equals("second"));
        assertThat(events.get(events.size() - 1))
                .isInstanceOfSatisfying(SubAgentEvent.Done.class, d -> assertThat(d.stopReason())
                        .isEqualTo(SubAgentEvent.StopReason.END_TURN));

        backend.close(session, "test-finished");

        List<String> recorded = List.copyOf(requestLog);
        assertThat(recorded).anyMatch(s -> s.startsWith("POST /sessions"));
        assertThat(recorded).anyMatch(s -> s.contains("POST /sessions/remote-1/prompt"));
        assertThat(recorded).anyMatch(s -> s.contains("DELETE /sessions/remote-1"));
    }

    @Test
    void promptNon2xxEmitsErrorEvent() throws Exception {
        HttpAgentConfig config = new HttpAgentConfig(
                "test-http",
                URI.create("http://127.0.0.1:" + server.getAddress().getPort()),
                HttpAgentConfig.AuthType.NONE,
                null,
                null,
                Duration.ofSeconds(2L),
                Duration.ofSeconds(5L),
                Duration.ofSeconds(10L));
        var backend = new HttpAgentBackend(config, mapper);

        SubAgentSession session = backend.open(
                new SubAgentBackend.OpenRequest("main", null, null, null, Map.of(), Duration.ofSeconds(30L)));

        var error = new AtomicReference<SubAgentEvent.Error>();
        var done = new CountDownLatch(1);
        backend.prompt(session, "fail-prompt", new CancellationToken())
                .subscribe(
                        event -> {
                            if (event instanceof SubAgentEvent.Error err) {
                                error.compareAndSet(null, err);
                            }
                        },
                        err -> done.countDown(),
                        done::countDown);

        assertThat(done.await(5L, TimeUnit.SECONDS)).isTrue();
        assertThat(error.get()).isNotNull();
        assertThat(error.get().code()).startsWith("HTTP_5");
    }

    private final class RouteHandler implements HttpHandler {

        @Override
        public void handle(HttpExchange exchange) throws IOException {
            requestLog.add(
                    exchange.getRequestMethod() + " " + exchange.getRequestURI().getPath());
            String authHeader = exchange.getRequestHeaders().getFirst("authorization");
            if (authHeader != null) {
                capturedAuth.compareAndSet(null, authHeader);
            }
            String method = exchange.getRequestMethod();
            String path = exchange.getRequestURI().getPath();
            try {
                dispatch(exchange, method, path);
            } finally {
                exchange.close();
            }
        }

        private void dispatch(HttpExchange exchange, String method, String path) throws IOException {
            if ("POST".equals(method) && "/sessions".equals(path)) {
                respondJson(exchange, 200, new HttpAgentProtocol.NewSessionResponse("remote-1"));
                return;
            }
            if ("POST".equals(method) && "/sessions/remote-1/prompt".equals(path)) {
                handlePrompt(exchange);
                return;
            }
            if ("DELETE".equals(method) && "/sessions/remote-1".equals(path)) {
                exchange.sendResponseHeaders(204, -1L);
                return;
            }
            exchange.sendResponseHeaders(404, -1L);
        }

        private void handlePrompt(HttpExchange exchange) throws IOException {
            byte[] body = exchange.getRequestBody().readAllBytes();
            HttpAgentProtocol.PromptRequest request = mapper.readValue(body, HttpAgentProtocol.PromptRequest.class);
            if ("fail-prompt".equals(request.task())) {
                exchange.sendResponseHeaders(503, 0L);
                try (OutputStream os = exchange.getResponseBody()) {
                    os.write("upstream busy".getBytes(StandardCharsets.UTF_8));
                }
                return;
            }
            exchange.getResponseHeaders().add("content-type", "application/x-ndjson");
            exchange.sendResponseHeaders(200, 0L);
            try (OutputStream os = exchange.getResponseBody()) {
                writeEvent(os, Map.of("type", "text_delta", "stream", "output", "text", "first "));
                writeEvent(os, Map.of("type", "text_delta", "stream", "output", "text", "second"));
                writeEvent(os, Map.of("type", "done", "stopReason", "end_turn"));
            }
        }

        private void writeEvent(OutputStream os, Map<String, Object> payload) throws IOException {
            os.write(mapper.writeValueAsBytes(payload));
            os.write('\n');
            os.flush();
        }

        private void respondJson(HttpExchange exchange, int status, Object body) throws IOException {
            byte[] bytes = mapper.writeValueAsBytes(body);
            exchange.getResponseHeaders().add("content-type", "application/json");
            exchange.sendResponseHeaders(status, bytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(bytes);
            }
        }
    }

    @SuppressWarnings("unused")
    private List<String> sample() {
        return Collections.emptyList();
    }
}
