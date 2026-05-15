/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.hicampus.mate.matecampusclaw.agent.subagent.a2a;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import com.huawei.hicampus.mate.matecampusclaw.agent.subagent.SubAgentBackend;
import com.huawei.hicampus.mate.matecampusclaw.agent.subagent.SubAgentEvent;
import com.huawei.hicampus.mate.matecampusclaw.agent.subagent.SubAgentSession;
import com.huawei.hicampus.mate.matecampusclaw.agent.tool.CancellationToken;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class A2aAgentBackendTest {

    private HttpServer server;
    private final ObjectMapper mapper = new ObjectMapper();
    private final ConcurrentLinkedQueue<RecordedRequest> requestLog = new ConcurrentLinkedQueue<>();
    private final AtomicReference<ResponderFn> responder = new AtomicReference<>();

    @BeforeEach
    void setUp() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/a2a/request/", new RouteHandler());
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
    void sendsHuaweiHeadersAndExtractsTextFromBareMessageResult() throws Exception {
        responder.set((exchange, body) -> respondJson(
                exchange,
                200,
                Map.of(
                        "jsonrpc", "2.0",
                        "id", body.path("id").asText(),
                        "result", Map.of("parts", List.of(Map.of("text", "answer one"))))));

        var backend = newBackend("KnowledgeQAAgent", "a2a_test", "appkey-xxx", "100003");

        SubAgentSession session = backend.open(
                new SubAgentBackend.OpenRequest("main", null, null, null, Map.of(), Duration.ofSeconds(30L)));

        List<SubAgentEvent> events = new ArrayList<>();
        CountDownLatch done = new CountDownLatch(1);
        backend.prompt(session, "ping", new CancellationToken())
                .subscribe(events::add, err -> done.countDown(), done::countDown);

        assertThat(done.await(5L, TimeUnit.SECONDS)).isTrue();

        RecordedRequest recorded = requestLog.peek();
        assertThat(recorded).isNotNull();
        assertThat(recorded.path).isEqualTo("/v1/a2a/request/KnowledgeQAAgent");
        assertThat(recorded.headers.get("X-Hw-Id")).isEqualTo("a2a_test");
        assertThat(recorded.headers.get("X-Hw-Appkey")).isEqualTo("appkey-xxx");
        assertThat(recorded.body.path("jsonrpc").asText()).isEqualTo("2.0");
        assertThat(recorded.body.path("method").asText()).isEqualTo("SendMessage");
        assertThat(recorded.body
                        .path("params")
                        .path("message")
                        .path("parts")
                        .get(0)
                        .path("text")
                        .asText())
                .isEqualTo("ping");
        assertThat(recorded.body.path("params").path("metadata").path("model").asText())
                .isEqualTo("100003");

        assertThat(events)
                .anyMatch(e -> e instanceof SubAgentEvent.TextDelta td
                        && td.text().equals("answer one")
                        && td.stream() == SubAgentEvent.Stream.OUTPUT);
        assertThat(events.get(events.size() - 1))
                .isInstanceOfSatisfying(SubAgentEvent.Done.class, d -> assertThat(d.stopReason())
                        .isEqualTo(SubAgentEvent.StopReason.END_TURN));

        backend.close(session, "test");
    }

    @Test
    void extractsTextFromTaskArtifactsWhenResultHasNoDirectParts() throws Exception {
        responder.set((exchange, body) -> respondJson(
                exchange,
                200,
                Map.of(
                        "jsonrpc", "2.0",
                        "id", body.path("id").asText(),
                        "result",
                                Map.of(
                                        "status",
                                        Map.of("state", "completed"),
                                        "artifacts",
                                        List.of(Map.of("parts", List.of(Map.of("text", "from artifact"))))))));

        var backend = newBackend("X", "h", "k", null);
        SubAgentSession session = backend.open(
                new SubAgentBackend.OpenRequest("main", null, null, null, Map.of(), Duration.ofSeconds(30L)));

        List<SubAgentEvent> events = new ArrayList<>();
        CountDownLatch done = new CountDownLatch(1);
        backend.prompt(session, "q", new CancellationToken())
                .subscribe(events::add, err -> done.countDown(), done::countDown);

        assertThat(done.await(5L, TimeUnit.SECONDS)).isTrue();
        assertThat(events)
                .anyMatch(e ->
                        e instanceof SubAgentEvent.TextDelta td && td.text().equals("from artifact"));
    }

    @Test
    void emitsErrorOnJsonRpcErrorResponse() throws Exception {
        responder.set((exchange, body) -> respondJson(
                exchange,
                200,
                Map.of(
                        "jsonrpc", "2.0",
                        "id", body.path("id").asText(),
                        "error", Map.of("code", -32600, "message", "Invalid Request"))));

        var backend = newBackend("X", "h", "k", null);
        SubAgentSession session = backend.open(
                new SubAgentBackend.OpenRequest("main", null, null, null, Map.of(), Duration.ofSeconds(30L)));

        AtomicReference<SubAgentEvent.Error> errorRef = new AtomicReference<>();
        CountDownLatch done = new CountDownLatch(1);
        backend.prompt(session, "q", new CancellationToken())
                .subscribe(
                        event -> {
                            if (event instanceof SubAgentEvent.Error err) {
                                errorRef.compareAndSet(null, err);
                            }
                        },
                        err -> done.countDown(),
                        done::countDown);

        assertThat(done.await(5L, TimeUnit.SECONDS)).isTrue();
        assertThat(errorRef.get()).isNotNull();
        assertThat(errorRef.get().code()).isEqualTo("A2A_RPC_-32600");
        assertThat(errorRef.get().message()).isEqualTo("Invalid Request");
    }

    @Test
    void emitsErrorOnHttp5xx() throws Exception {
        responder.set((exchange, body) -> {
            exchange.sendResponseHeaders(503, 0L);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write("upstream busy".getBytes(StandardCharsets.UTF_8));
            }
        });

        var backend = newBackend("X", "h", "k", null);
        SubAgentSession session = backend.open(
                new SubAgentBackend.OpenRequest("main", null, null, null, Map.of(), Duration.ofSeconds(30L)));

        AtomicReference<SubAgentEvent.Error> errorRef = new AtomicReference<>();
        CountDownLatch done = new CountDownLatch(1);
        backend.prompt(session, "q", new CancellationToken())
                .subscribe(
                        event -> {
                            if (event instanceof SubAgentEvent.Error err) {
                                errorRef.compareAndSet(null, err);
                            }
                        },
                        err -> done.countDown(),
                        done::countDown);

        assertThat(done.await(5L, TimeUnit.SECONDS)).isTrue();
        assertThat(errorRef.get()).isNotNull();
        assertThat(errorRef.get().code()).isEqualTo("A2A_HTTP_503");
        assertThat(errorRef.get().retryable()).isTrue();
    }

    @Test
    void perSessionModelOverrideTakesPrecedenceOverDefault() throws Exception {
        responder.set((exchange, body) -> respondJson(
                exchange,
                200,
                Map.of(
                        "jsonrpc", "2.0",
                        "id", body.path("id").asText(),
                        "result", Map.of("parts", List.of(Map.of("text", "ok"))))));

        var backend = newBackend("X", "h", "k", "default-model");
        SubAgentSession session = backend.open(new SubAgentBackend.OpenRequest(
                "main", null, "override-model", null, Map.of(), Duration.ofSeconds(30L)));

        CountDownLatch done = new CountDownLatch(1);
        backend.prompt(session, "q", new CancellationToken())
                .subscribe(e -> {}, err -> done.countDown(), done::countDown);
        assertThat(done.await(5L, TimeUnit.SECONDS)).isTrue();

        RecordedRequest recorded = requestLog.peek();
        assertThat(recorded.body.path("params").path("metadata").path("model").asText())
                .isEqualTo("override-model");
    }

    private A2aAgentBackend newBackend(String agentName, String hwId, String hwAppKey, String defaultModel) {
        URI base = URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/v1/a2a/request");
        A2aAgentConfig config = new A2aAgentConfig(
                "test-a2a",
                base,
                agentName,
                hwId,
                hwAppKey,
                defaultModel,
                Duration.ofSeconds(2L),
                Duration.ofSeconds(5L));
        return new A2aAgentBackend(config, mapper);
    }

    private void respondJson(HttpExchange exchange, int status, Object body) throws IOException {
        byte[] bytes = mapper.writeValueAsBytes(body);
        exchange.getResponseHeaders().add("content-type", "application/json");
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }

    @FunctionalInterface
    private interface ResponderFn {
        void respond(HttpExchange exchange, JsonNode body) throws IOException;
    }

    private record RecordedRequest(String method, String path, Map<String, String> headers, JsonNode body) {}

    private final class RouteHandler implements HttpHandler {

        @Override
        public void handle(HttpExchange exchange) throws IOException {
            byte[] reqBytes = exchange.getRequestBody().readAllBytes();
            JsonNode body = reqBytes.length == 0 ? mapper.nullNode() : mapper.readTree(reqBytes);
            Map<String, String> headers = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
            exchange.getRequestHeaders().forEach((k, v) -> headers.put(k, v.isEmpty() ? "" : v.get(0)));
            requestLog.add(new RecordedRequest(
                    exchange.getRequestMethod(), exchange.getRequestURI().getPath(), headers, body));

            ResponderFn fn = responder.get();
            try {
                if (fn == null) {
                    exchange.sendResponseHeaders(500, -1L);
                    return;
                }
                fn.respond(exchange, body);
            } finally {
                exchange.close();
            }
        }
    }
}
