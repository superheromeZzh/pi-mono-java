/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.campusclaw.ai.provider.google;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.util.List;

import com.campusclaw.ai.env.ProviderConfigResolver;
import com.campusclaw.ai.env.ResolvedProviderConfig;
import com.campusclaw.ai.types.Api;
import com.campusclaw.ai.types.AssistantMessage;
import com.campusclaw.ai.types.Context;
import com.campusclaw.ai.types.InputModality;
import com.campusclaw.ai.types.Model;
import com.campusclaw.ai.types.ModelCost;
import com.campusclaw.ai.types.Provider;
import com.campusclaw.ai.types.StopReason;
import com.campusclaw.ai.types.TextContent;
import com.campusclaw.ai.types.ToolCall;
import com.campusclaw.ai.types.UserMessage;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import okhttp3.mockwebserver.SocketPolicy;

/**
 * Integration tests for {@link GoogleVertexAIProvider}. The provider routes
 * to the Gemini API via Vertex AI; when the model carries an explicit baseUrl
 * we can point it at MockWebServer to exercise the SSE parser without going
 * through the real Vertex AI endpoint resolution.
 */
@Timeout(30)
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class GoogleVertexAIProviderIntegrationTest {

    @Mock
    ProviderConfigResolver providerConfigResolver;

    private MockWebServer server;
    private GoogleVertexAIProvider provider;

    private Model testModel() {
        // baseUrl is required so the provider hits MockWebServer instead of resolving
        // through GOOGLE_CLOUD_PROJECT / API key heuristics
        return new Model(
                "gemini-2.0-flash",
                "Gemini",
                Api.GOOGLE_VERTEX,
                Provider.GOOGLE_VERTEX,
                server.url("").toString().replaceAll("/$", ""),
                false,
                List.of(InputModality.TEXT),
                new ModelCost(0.075, 0.30, 0.0, 0.0),
                1000000,
                8192,
                null,
                null,
                null);
    }

    @BeforeEach
    void setUp() throws IOException {
        server = new MockWebServer();
        server.start();
        provider = new GoogleVertexAIProvider(providerConfigResolver);
        when(providerConfigResolver.resolve(any(Provider.class), any(Model.class)))
                .thenReturn(new ResolvedProviderConfig(
                        null, server.url("").toString().replaceAll("/$", ""), null));
    }

    @AfterEach
    void tearDown() throws IOException {
        server.shutdown();
    }

    private String chunk(String json) {
        return "data: " + json + "\n\n";
    }

    @Test
    void apiIdentity() {
        assertEquals(Api.GOOGLE_VERTEX, provider.getApi());
    }

    @Test
    void streamsPlainText() {
        String body = chunk(
                        "{\"candidates\":[{\"content\":{\"parts\":[{\"text\":\"hello\"}]}}],"
                                + "\"usageMetadata\":{\"promptTokenCount\":2,\"candidatesTokenCount\":1,\"totalTokenCount\":3}}")
                + chunk(
                        "{\"candidates\":[{\"content\":{\"parts\":[{\"text\":\" world\"}]},"
                                + "\"finishReason\":\"STOP\"}],"
                                + "\"usageMetadata\":{\"promptTokenCount\":2,\"candidatesTokenCount\":2,\"totalTokenCount\":4}}");
        server.enqueue(new MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "text/event-stream")
                .setBody(body)
                .setSocketPolicy(SocketPolicy.DISCONNECT_AT_END));

        Context ctx = new Context(null, List.of(new UserMessage("Hi", 1L)), null);
        AssistantMessage finalMsg =
                provider.streamSimple(testModel(), ctx, null).result().block();
        assertNotNull(finalMsg);
        assertEquals(StopReason.STOP, finalMsg.stopReason());
        assertInstanceOf(TextContent.class, finalMsg.content().get(0));
        assertEquals("hello world", ((TextContent) finalMsg.content().get(0)).text());
    }

    @Test
    void streamsFunctionCallSetsToolUse() {
        String body = chunk("{\"candidates\":[{\"content\":{\"parts\":["
                + "{\"functionCall\":{\"name\":\"search\",\"args\":{\"q\":\"x\"}}}"
                + "]},\"finishReason\":\"STOP\"}],"
                + "\"usageMetadata\":{\"promptTokenCount\":2,\"candidatesTokenCount\":3,\"totalTokenCount\":5}}");
        server.enqueue(new MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "text/event-stream")
                .setBody(body)
                .setSocketPolicy(SocketPolicy.DISCONNECT_AT_END));

        Context ctx = new Context(null, List.of(new UserMessage("Hi", 1L)), null);
        AssistantMessage finalMsg =
                provider.streamSimple(testModel(), ctx, null).result().block();
        assertNotNull(finalMsg);
        assertEquals(StopReason.TOOL_USE, finalMsg.stopReason());
        assertTrue(finalMsg.content().stream().anyMatch(b -> b instanceof ToolCall));
    }

    @Test
    void requestBodyContainsContentsAndSystemInstruction() throws Exception {
        String body = chunk("{\"candidates\":[{\"content\":{\"parts\":[{\"text\":\"ok\"}]},"
                + "\"finishReason\":\"STOP\"}],"
                + "\"usageMetadata\":{\"promptTokenCount\":1,\"candidatesTokenCount\":1,\"totalTokenCount\":2}}");
        server.enqueue(new MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "text/event-stream")
                .setBody(body)
                .setSocketPolicy(SocketPolicy.DISCONNECT_AT_END));

        Context ctx = new Context("Stay brief.", List.of(new UserMessage("Hi", 1L)), null);
        provider.streamSimple(testModel(), ctx, null).result().block();
        RecordedRequest request = server.takeRequest();
        assertEquals("POST", request.getMethod());
        String reqBody = request.getBody().readUtf8();
        assertTrue(reqBody.contains("\"contents\""));
        assertTrue(reqBody.contains("systemInstruction"));
        assertTrue(reqBody.contains("Stay brief."));
    }

    @Test
    void mapsSafetyFinishReasonToError() {
        String body = chunk("{\"candidates\":[{\"content\":{\"parts\":[]},\"finishReason\":\"SAFETY\"}]}");
        server.enqueue(new MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "text/event-stream")
                .setBody(body)
                .setSocketPolicy(SocketPolicy.DISCONNECT_AT_END));

        Context ctx = new Context(null, List.of(new UserMessage("Hi", 1L)), null);
        AssistantMessage finalMsg =
                provider.streamSimple(testModel(), ctx, null).result().block();
        assertNotNull(finalMsg);
        assertEquals(StopReason.ERROR, finalMsg.stopReason());
    }
}
