/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.campusclaw.ai.provider.mistral;

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
import com.campusclaw.ai.types.ThinkingContent;
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

@Timeout(30)
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class MistralProviderIntegrationTest {

    @Mock
    ProviderConfigResolver providerConfigResolver;

    private MockWebServer server;
    private MistralProvider provider;

    private Model testModel(String baseUrl) {
        return new Model(
                "mistral-large",
                "Mistral Large",
                Api.MISTRAL_CONVERSATIONS,
                Provider.MISTRAL,
                baseUrl,
                false,
                List.of(InputModality.TEXT),
                new ModelCost(2.0, 6.0, 0.0, 0.0),
                128000,
                4096,
                null,
                null,
                null);
    }

    @BeforeEach
    void setUp() throws IOException {
        server = new MockWebServer();
        server.start();
        provider = new MistralProvider(providerConfigResolver);

        // Strip trailing slash so baseUrl + "/chat/completions" works
        String baseUrl = server.url("").toString().replaceAll("/$", "");
        when(providerConfigResolver.resolve(any(Provider.class), any(Model.class)))
                .thenReturn(new ResolvedProviderConfig("test-api-key", baseUrl, null));
    }

    @AfterEach
    void tearDown() throws IOException {
        server.shutdown();
    }

    private String chunk(String data) {
        return "data: " + data + "\n\n";
    }

    @Test
    void apiAndConstructor() {
        assertEquals(Api.MISTRAL_CONVERSATIONS, provider.getApi());
    }

    @Test
    void streamsPlainTextResponse() throws Exception {
        String body = chunk("{\"choices\":[{\"index\":0,\"delta\":{\"content\":\"Hello\"},\"finish_reason\":null}]}")
                + chunk("{\"choices\":[{\"index\":0,\"delta\":{\"content\":\" world\"},\"finish_reason\":null}]}")
                + chunk("{\"choices\":[{\"index\":0,\"delta\":{},\"finish_reason\":\"stop\"}],"
                        + "\"usage\":{\"prompt_tokens\":10,\"completion_tokens\":5,\"total_tokens\":15}}")
                + "data: [DONE]\n\n";
        server.enqueue(new MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "text/event-stream")
                .setBody(body)
                .setSocketPolicy(SocketPolicy.DISCONNECT_AT_END));

        Model model = testModel(server.url("").toString().replaceAll("/$", ""));
        Context ctx = new Context(null, List.of(new UserMessage("Hi", 1L)), null);
        var stream = provider.streamSimple(model, ctx, null);
        AssistantMessage finalMsg = stream.result().block();
        assertNotNull(finalMsg);
        assertEquals(StopReason.STOP, finalMsg.stopReason());
        assertInstanceOf(TextContent.class, finalMsg.content().get(0));
        assertEquals("Hello world", ((TextContent) finalMsg.content().get(0)).text());
        assertEquals(10, finalMsg.usage().input());
        assertEquals(5, finalMsg.usage().output());
    }

    @Test
    void streamsThinkingThenText() throws Exception {
        // Mistral can emit content as an array with thinking/text items.
        String body = chunk("{\"choices\":[{\"index\":0,\"delta\":{\"content\":["
                        + "{\"type\":\"thinking\",\"thinking\":[{\"type\":\"text\",\"text\":\"pondering\"}]}"
                        + "]},\"finish_reason\":null}]}")
                + chunk("{\"choices\":[{\"index\":0,\"delta\":{\"content\":["
                        + "{\"type\":\"text\",\"text\":\"final answer\"}"
                        + "]},\"finish_reason\":null}]}")
                + chunk("{\"choices\":[{\"index\":0,\"delta\":{},\"finish_reason\":\"stop\"}],"
                        + "\"usage\":{\"prompt_tokens\":2,\"completion_tokens\":3,\"total_tokens\":5}}")
                + "data: [DONE]\n\n";
        server.enqueue(new MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "text/event-stream")
                .setBody(body)
                .setSocketPolicy(SocketPolicy.DISCONNECT_AT_END));

        Model model = testModel(server.url("").toString().replaceAll("/$", ""));
        Context ctx = new Context(null, List.of(new UserMessage("Hi", 1L)), null);
        AssistantMessage finalMsg =
                provider.streamSimple(model, ctx, null).result().block();
        assertNotNull(finalMsg);

        // Should include thinking and text blocks
        boolean hasThinking = finalMsg.content().stream().anyMatch(b -> b instanceof ThinkingContent);
        boolean hasText = finalMsg.content().stream().anyMatch(b -> b instanceof TextContent);
        assertTrue(hasThinking);
        assertTrue(hasText);
    }

    @Test
    void streamsToolCall() throws Exception {
        String body = chunk("{\"choices\":[{\"index\":0,\"delta\":{\"tool_calls\":["
                        + "{\"index\":0,\"id\":\"call-1\",\"function\":{\"name\":\"search\",\"arguments\":\"\"}}]"
                        + "},\"finish_reason\":null}]}")
                + chunk("{\"choices\":[{\"index\":0,\"delta\":{\"tool_calls\":["
                        + "{\"index\":0,\"function\":{\"arguments\":\"{\\\"q\\\":\\\"x\\\"}\"}}]"
                        + "},\"finish_reason\":null}]}")
                + chunk("{\"choices\":[{\"index\":0,\"delta\":{},\"finish_reason\":\"tool_calls\"}],"
                        + "\"usage\":{\"prompt_tokens\":3,\"completion_tokens\":4,\"total_tokens\":7}}")
                + "data: [DONE]\n\n";
        server.enqueue(new MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "text/event-stream")
                .setBody(body)
                .setSocketPolicy(SocketPolicy.DISCONNECT_AT_END));

        Model model = testModel(server.url("").toString().replaceAll("/$", ""));
        Context ctx = new Context(null, List.of(new UserMessage("Hi", 1L)), null);
        AssistantMessage finalMsg =
                provider.streamSimple(model, ctx, null).result().block();
        assertNotNull(finalMsg);
        assertEquals(StopReason.TOOL_USE, finalMsg.stopReason());
        boolean hasToolCall = finalMsg.content().stream().anyMatch(b -> b instanceof ToolCall);
        assertTrue(hasToolCall);
    }

    @Test
    void verifyRequestParameters() throws Exception {
        String body = chunk("{\"choices\":[{\"index\":0,\"delta\":{\"content\":\"ok\"},\"finish_reason\":\"stop\"}],"
                        + "\"usage\":{\"prompt_tokens\":1,\"completion_tokens\":1,\"total_tokens\":2}}")
                + "data: [DONE]\n\n";
        server.enqueue(new MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "text/event-stream")
                .setBody(body)
                .setSocketPolicy(SocketPolicy.DISCONNECT_AT_END));

        Model model = testModel(server.url("").toString().replaceAll("/$", ""));
        Context ctx = new Context("Be brief.", List.of(new UserMessage("Hi", 1L)), null);
        provider.streamSimple(model, ctx, null).result().block();

        RecordedRequest request = server.takeRequest();
        assertNotNull(request);
        assertEquals("POST", request.getMethod());
        assertEquals("Bearer test-api-key", request.getHeader("Authorization"));
        assertEquals("application/json", request.getHeader("Content-Type"));
        String reqBody = request.getBody().readUtf8();
        assertTrue(reqBody.contains("mistral-large"));
        assertTrue(reqBody.contains("\"stream\":true"));
        assertTrue(reqBody.contains("Be brief."));
    }

    @Test
    void missingApiKeyEmitsError() {
        when(providerConfigResolver.resolve(any(Provider.class), any(Model.class)))
                .thenReturn(new ResolvedProviderConfig(
                        null, server.url("").toString().replaceAll("/$", ""), null));
        Model model = testModel(server.url("").toString().replaceAll("/$", ""));
        Context ctx = new Context(null, List.of(new UserMessage("Hi", 1L)), null);
        org.junit.jupiter.api.Assertions.assertThrows(
                Exception.class,
                () -> provider.streamSimple(model, ctx, null).result().block());
    }
}
