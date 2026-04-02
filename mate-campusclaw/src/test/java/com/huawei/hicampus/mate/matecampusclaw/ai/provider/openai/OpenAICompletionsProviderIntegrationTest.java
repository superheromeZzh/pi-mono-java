package com.huawei.hicampus.mate.matecampusclaw.ai.provider.openai;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import com.huawei.hicampus.mate.matecampusclaw.ai.stream.AssistantMessageEvent;
import com.huawei.hicampus.mate.matecampusclaw.ai.stream.AssistantMessageEventStream;
import com.huawei.hicampus.mate.matecampusclaw.ai.types.*;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import okhttp3.mockwebserver.SocketPolicy;

/**
 * Integration tests for {@link OpenAICompletionsProvider} using MockWebServer
 * to simulate real OpenAI Chat Completions API SSE responses.
 */
@Timeout(30)
class OpenAICompletionsProviderIntegrationTest {

    private MockWebServer server;
    private OpenAICompletionsProvider provider;

    private Model testModel(String baseUrl) {
        return new Model(
                "gpt-4o", "GPT-4o",
                Api.OPENAI_COMPLETIONS, Provider.OPENAI,
                baseUrl, false,
                List.of(InputModality.TEXT, InputModality.IMAGE),
                new ModelCost(2.5, 10.0, 1.25, 0.0),
                128000, 16384, null, null,
                null
        );
    }

    @BeforeEach
    void setUp() throws IOException {
        server = new MockWebServer();
        server.start();
        provider = new OpenAICompletionsProvider();
    }

    @AfterEach
    void tearDown() throws IOException {
        server.shutdown();
    }

    private String chunk(String data) {
        return "data: " + data + "\n\n";
    }

    // -------------------------------------------------------------------
    // Text streaming
    // -------------------------------------------------------------------

    @Nested
    class TextStreaming {

        @Test
        void streamsTextResponse() throws Exception {
            String sseBody = chunk("""
                    {"id":"chatcmpl-123","object":"chat.completion.chunk","created":1234567890,"model":"gpt-4o","choices":[{"index":0,"delta":{"role":"assistant","content":""},"finish_reason":null}]}""")
                    + chunk("""
                    {"id":"chatcmpl-123","object":"chat.completion.chunk","created":1234567890,"model":"gpt-4o","choices":[{"index":0,"delta":{"content":"Hello"},"finish_reason":null}]}""")
                    + chunk("""
                    {"id":"chatcmpl-123","object":"chat.completion.chunk","created":1234567890,"model":"gpt-4o","choices":[{"index":0,"delta":{"content":" world"},"finish_reason":null}]}""")
                    + chunk("""
                    {"id":"chatcmpl-123","object":"chat.completion.chunk","created":1234567890,"model":"gpt-4o","choices":[{"index":0,"delta":{},"finish_reason":"stop"}],"usage":{"prompt_tokens":10,"completion_tokens":5,"total_tokens":15}}""")
                    + "data: [DONE]\n\n";

            server.enqueue(new MockResponse()
                    .setResponseCode(200)
                    .setHeader("Content-Type", "text/event-stream")
                    .setBody(sseBody)
                    .setSocketPolicy(SocketPolicy.DISCONNECT_AT_END));

            String baseUrl = server.url("/").toString();
            var model = testModel(baseUrl);
            var context = new Context(null,
                    List.of(new UserMessage("Hi", 1L)), null);
            var eventStream = new AssistantMessageEventStream();

            provider.executeStream(model, context, "test-api-key",
                    null, null, null, eventStream);

            var events = collectEvents(eventStream);
            var finalMsg = eventStream.result().block();

            // Verify event sequence
            assertHasEventType(events, AssistantMessageEvent.TextStartEvent.class);
            assertHasEventType(events, AssistantMessageEvent.TextDeltaEvent.class);
            assertHasEventType(events, AssistantMessageEvent.TextEndEvent.class);
            assertHasEventType(events, AssistantMessageEvent.DoneEvent.class);

            // Verify final message content
            assertNotNull(finalMsg);
            assertEquals("chatcmpl-123", finalMsg.responseId());
            assertEquals(StopReason.STOP, finalMsg.stopReason());
            assertEquals(1, finalMsg.content().size());
            assertInstanceOf(TextContent.class, finalMsg.content().get(0));
            assertEquals("Hello world", ((TextContent) finalMsg.content().get(0)).text());
        }

        @Test
        void verifiesRequestParameters() throws Exception {
            String sseBody = chunk("""
                    {"id":"chatcmpl-456","object":"chat.completion.chunk","created":1234567890,"model":"gpt-4o","choices":[{"index":0,"delta":{"role":"assistant","content":"OK"},"finish_reason":"stop"}],"usage":{"prompt_tokens":5,"completion_tokens":1,"total_tokens":6}}""")
                    + "data: [DONE]\n\n";

            server.enqueue(new MockResponse()
                    .setResponseCode(200)
                    .setHeader("Content-Type", "text/event-stream")
                    .setBody(sseBody)
                    .setSocketPolicy(SocketPolicy.DISCONNECT_AT_END));

            String baseUrl = server.url("/").toString();
            var model = testModel(baseUrl);
            var context = new Context("Be concise.",
                    List.of(new UserMessage("Hello", 1L)), null);
            var eventStream = new AssistantMessageEventStream();

            provider.executeStream(model, context, "test-api-key",
                    4096, 0.5, null, eventStream);

            eventStream.result().block();

            RecordedRequest request = server.takeRequest();
            assertNotNull(request);
            String body = request.getBody().readUtf8();
            assertTrue(body.contains("gpt-4o"));
        }
    }

    // -------------------------------------------------------------------
    // Tool call streaming
    // -------------------------------------------------------------------

    @Nested
    class ToolCallStreaming {

        @Test
        void streamsToolCallResponse() throws Exception {
            String sseBody = chunk("""
                    {"id":"chatcmpl-tc","object":"chat.completion.chunk","created":1234567890,"model":"gpt-4o","choices":[{"index":0,"delta":{"role":"assistant","tool_calls":[{"index":0,"id":"call_abc","type":"function","function":{"name":"bash","arguments":""}}]},"finish_reason":null}]}""")
                    + chunk("""
                    {"id":"chatcmpl-tc","object":"chat.completion.chunk","created":1234567890,"model":"gpt-4o","choices":[{"index":0,"delta":{"tool_calls":[{"index":0,"function":{"arguments":"{\\"com"}}]},"finish_reason":null}]}""")
                    + chunk("""
                    {"id":"chatcmpl-tc","object":"chat.completion.chunk","created":1234567890,"model":"gpt-4o","choices":[{"index":0,"delta":{"tool_calls":[{"index":0,"function":{"arguments":"mand\\":\\"ls\\"}"}}]},"finish_reason":null}]}""")
                    + chunk("""
                    {"id":"chatcmpl-tc","object":"chat.completion.chunk","created":1234567890,"model":"gpt-4o","choices":[{"index":0,"delta":{},"finish_reason":"tool_calls"}],"usage":{"prompt_tokens":20,"completion_tokens":15,"total_tokens":35}}""")
                    + "data: [DONE]\n\n";

            server.enqueue(new MockResponse()
                    .setResponseCode(200)
                    .setHeader("Content-Type", "text/event-stream")
                    .setBody(sseBody)
                    .setSocketPolicy(SocketPolicy.DISCONNECT_AT_END));

            String baseUrl = server.url("/").toString();
            var model = testModel(baseUrl);
            var context = new Context(null,
                    List.of(new UserMessage("Run ls", 1L)), null);
            var eventStream = new AssistantMessageEventStream();

            provider.executeStream(model, context, "test-api-key",
                    null, null, null, eventStream);

            var events = collectEvents(eventStream);
            var finalMsg = eventStream.result().block();

            // Verify tool call events
            assertHasEventType(events, AssistantMessageEvent.ToolCallStartEvent.class);
            assertHasEventType(events, AssistantMessageEvent.ToolCallDeltaEvent.class);
            assertHasEventType(events, AssistantMessageEvent.ToolCallEndEvent.class);

            // Verify final message
            assertNotNull(finalMsg);
            assertEquals(StopReason.TOOL_USE, finalMsg.stopReason());
            assertEquals(1, finalMsg.content().size());
            assertInstanceOf(ToolCall.class, finalMsg.content().get(0));
            var toolCall = (ToolCall) finalMsg.content().get(0);
            assertEquals("call_abc", toolCall.id());
            assertEquals("bash", toolCall.name());
            assertEquals("ls", toolCall.arguments().get("command"));
        }
    }

    // -------------------------------------------------------------------
    // Usage statistics
    // -------------------------------------------------------------------

    @Nested
    class UsageStatistics {

        @Test
        void tracksUsageWithCachedTokens() throws Exception {
            String sseBody = chunk("""
                    {"id":"chatcmpl-u","object":"chat.completion.chunk","created":1234567890,"model":"gpt-4o","choices":[{"index":0,"delta":{"role":"assistant","content":"OK"},"finish_reason":"stop"}],"usage":{"prompt_tokens":100,"completion_tokens":5,"total_tokens":105,"prompt_tokens_details":{"cached_tokens":30}}}""")
                    + "data: [DONE]\n\n";

            server.enqueue(new MockResponse()
                    .setResponseCode(200)
                    .setHeader("Content-Type", "text/event-stream")
                    .setBody(sseBody)
                    .setSocketPolicy(SocketPolicy.DISCONNECT_AT_END));

            String baseUrl = server.url("/").toString();
            var model = testModel(baseUrl);
            var context = new Context(null,
                    List.of(new UserMessage("Hello", 1L)), null);
            var eventStream = new AssistantMessageEventStream();

            provider.executeStream(model, context, "test-api-key",
                    null, null, null, eventStream);

            var finalMsg = eventStream.result().block();

            assertNotNull(finalMsg);
            // OpenAI includes cached tokens in prompt_tokens, provider subtracts
            assertEquals(70, finalMsg.usage().input()); // 100 - 30
            assertEquals(5, finalMsg.usage().output());
            assertEquals(30, finalMsg.usage().cacheRead());
            assertEquals(0, finalMsg.usage().cacheWrite());

            assertTrue(finalMsg.usage().cost().total() > 0);
        }
    }

    // -------------------------------------------------------------------
    // Error handling
    // -------------------------------------------------------------------

    @Nested
    class ErrorHandling {

        @Test
        void handlesApiError() throws Exception {
            server.enqueue(new MockResponse()
                    .setResponseCode(400)
                    .setBody("{\"error\":{\"message\":\"Bad request\",\"type\":\"invalid_request_error\"}}")
                    .setSocketPolicy(SocketPolicy.DISCONNECT_AT_END));

            String baseUrl = server.url("/").toString();
            var model = testModel(baseUrl);
            var context = new Context(null,
                    List.of(new UserMessage("Hello", 1L)), null);
            var eventStream = new AssistantMessageEventStream();

            provider.executeStream(model, context, "test-api-key",
                    null, null, null, eventStream);

            assertThrows(Exception.class, () -> eventStream.result().block());
        }

        @Test
        void handlesMissingApiKey() {
            var model = testModel("http://localhost:1");
            var context = new Context(null,
                    List.of(new UserMessage("Hello", 1L)), null);
            var eventStream = new AssistantMessageEventStream();

            provider.executeStream(model, context, null,
                    null, null, null, eventStream);

            assertThrows(Exception.class, () -> eventStream.result().block());
        }
    }

    // -------------------------------------------------------------------
    // Stop reason mapping in streaming
    // -------------------------------------------------------------------

    @Nested
    class StopReasonInStreaming {

        @Test
        void mapsLengthStopReason() throws Exception {
            String sseBody = chunk("""
                    {"id":"chatcmpl-len","object":"chat.completion.chunk","created":1234567890,"model":"gpt-4o","choices":[{"index":0,"delta":{"role":"assistant","content":"truncated"},"finish_reason":"length"}],"usage":{"prompt_tokens":10,"completion_tokens":100,"total_tokens":110}}""")
                    + "data: [DONE]\n\n";

            server.enqueue(new MockResponse()
                    .setResponseCode(200)
                    .setHeader("Content-Type", "text/event-stream")
                    .setBody(sseBody)
                    .setSocketPolicy(SocketPolicy.DISCONNECT_AT_END));

            String baseUrl = server.url("/").toString();
            var model = testModel(baseUrl);
            var context = new Context(null,
                    List.of(new UserMessage("Write a long essay", 1L)), null);
            var eventStream = new AssistantMessageEventStream();

            provider.executeStream(model, context, "test-api-key",
                    null, null, null, eventStream);

            var finalMsg = eventStream.result().block();

            assertNotNull(finalMsg);
            assertEquals(StopReason.LENGTH, finalMsg.stopReason());
        }

        @Test
        void mapsContentFilterStopReason() throws Exception {
            String sseBody = chunk("""
                    {"id":"chatcmpl-cf","object":"chat.completion.chunk","created":1234567890,"model":"gpt-4o","choices":[{"index":0,"delta":{"role":"assistant","content":""},"finish_reason":"content_filter"}],"usage":{"prompt_tokens":10,"completion_tokens":0,"total_tokens":10}}""")
                    + "data: [DONE]\n\n";

            server.enqueue(new MockResponse()
                    .setResponseCode(200)
                    .setHeader("Content-Type", "text/event-stream")
                    .setBody(sseBody)
                    .setSocketPolicy(SocketPolicy.DISCONNECT_AT_END));

            String baseUrl = server.url("/").toString();
            var model = testModel(baseUrl);
            var context = new Context(null,
                    List.of(new UserMessage("Something", 1L)), null);
            var eventStream = new AssistantMessageEventStream();

            provider.executeStream(model, context, "test-api-key",
                    null, null, null, eventStream);

            var finalMsg = eventStream.result().block();

            assertNotNull(finalMsg);
            assertEquals(StopReason.ERROR, finalMsg.stopReason());
        }
    }

    // -------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------

    private List<AssistantMessageEvent> collectEvents(AssistantMessageEventStream eventStream) {
        var events = new ArrayList<AssistantMessageEvent>();
        eventStream.asFlux().doOnNext(events::add).blockLast();
        return events;
    }

    private <T> void assertHasEventType(List<AssistantMessageEvent> events, Class<T> type) {
        assertTrue(events.stream().anyMatch(type::isInstance),
                "Expected event of type " + type.getSimpleName() + " but none found in: " +
                        events.stream().map(e -> e.getClass().getSimpleName()).toList());
    }
}
