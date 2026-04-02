package com.huawei.hicampus.mate.matecampusclaw.ai.provider.anthropic;

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
 * Integration tests for {@link AnthropicProvider} using MockWebServer to simulate
 * real Anthropic Messages API SSE responses.
 */
@Timeout(30)
class AnthropicProviderIntegrationTest {

    private MockWebServer server;
    private AnthropicProvider provider;

    private Model testModel(String baseUrl) {
        return new Model(
                "claude-sonnet-4-20250514", "Claude Sonnet 4",
                Api.ANTHROPIC_MESSAGES, Provider.ANTHROPIC,
                baseUrl, true,
                List.of(InputModality.TEXT, InputModality.IMAGE),
                new ModelCost(3.0, 15.0, 0.3, 3.75),
                200000, 16000, null, null,
                null
        );
    }

    @BeforeEach
    void setUp() throws IOException {
        server = new MockWebServer();
        server.start();
        provider = new AnthropicProvider();
    }

    @AfterEach
    void tearDown() throws IOException {
        server.shutdown();
    }

    private String sseEvent(String event, String data) {
        return "event: " + event + "\ndata: " + data + "\n\n";
    }

    // -------------------------------------------------------------------
    // Text streaming
    // -------------------------------------------------------------------

    @Nested
    class TextStreaming {

        @Test
        void streamsTextResponse() throws Exception {
            String sseBody = sseEvent("message_start",
                    """
                    {"type":"message_start","message":{"id":"msg_123","type":"message","role":"assistant","model":"claude-sonnet-4-20250514","content":[],"stop_reason":null,"stop_sequence":null,"usage":{"input_tokens":10,"output_tokens":0,"cache_creation_input_tokens":0,"cache_read_input_tokens":0}}}""")
                    + sseEvent("content_block_start",
                    """
                    {"type":"content_block_start","index":0,"content_block":{"type":"text","text":""}}""")
                    + sseEvent("content_block_delta",
                    """
                    {"type":"content_block_delta","index":0,"delta":{"type":"text_delta","text":"Hello"}}""")
                    + sseEvent("content_block_delta",
                    """
                    {"type":"content_block_delta","index":0,"delta":{"type":"text_delta","text":" world"}}""")
                    + sseEvent("content_block_stop",
                    """
                    {"type":"content_block_stop","index":0}""")
                    + sseEvent("message_delta",
                    """
                    {"type":"message_delta","delta":{"stop_reason":"end_turn","stop_sequence":null},"usage":{"output_tokens":5}}""")
                    + sseEvent("message_stop",
                    """
                    {"type":"message_stop"}""");

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
                    null, null, null, null, eventStream);

            var events = collectEvents(eventStream);
            var finalMsg = eventStream.result().block();

            // Verify event sequence
            assertHasEventType(events, AssistantMessageEvent.StartEvent.class);
            assertHasEventType(events, AssistantMessageEvent.TextStartEvent.class);
            assertHasEventType(events, AssistantMessageEvent.TextDeltaEvent.class);
            assertHasEventType(events, AssistantMessageEvent.TextEndEvent.class);
            assertHasEventType(events, AssistantMessageEvent.DoneEvent.class);

            // Verify final message
            assertNotNull(finalMsg);
            assertEquals("msg_123", finalMsg.responseId());
            assertEquals(StopReason.STOP, finalMsg.stopReason());
            assertEquals(1, finalMsg.content().size());
            assertInstanceOf(TextContent.class, finalMsg.content().get(0));
            assertEquals("Hello world", ((TextContent) finalMsg.content().get(0)).text());

            // Verify usage
            assertEquals(10, finalMsg.usage().input());
            assertEquals(5, finalMsg.usage().output());
        }

        @Test
        void verifiesRequestParameters() throws Exception {
            String sseBody = sseEvent("message_start",
                    """
                    {"type":"message_start","message":{"id":"msg_456","type":"message","role":"assistant","model":"claude-sonnet-4-20250514","content":[],"stop_reason":null,"stop_sequence":null,"usage":{"input_tokens":5,"output_tokens":0,"cache_creation_input_tokens":0,"cache_read_input_tokens":0}}}""")
                    + sseEvent("content_block_start",
                    """
                    {"type":"content_block_start","index":0,"content_block":{"type":"text","text":""}}""")
                    + sseEvent("content_block_delta",
                    """
                    {"type":"content_block_delta","index":0,"delta":{"type":"text_delta","text":"OK"}}""")
                    + sseEvent("content_block_stop",
                    """
                    {"type":"content_block_stop","index":0}""")
                    + sseEvent("message_delta",
                    """
                    {"type":"message_delta","delta":{"stop_reason":"end_turn","stop_sequence":null},"usage":{"output_tokens":1}}""")
                    + sseEvent("message_stop",
                    """
                    {"type":"message_stop"}""");

            server.enqueue(new MockResponse()
                    .setResponseCode(200)
                    .setHeader("Content-Type", "text/event-stream")
                    .setBody(sseBody)
                    .setSocketPolicy(SocketPolicy.DISCONNECT_AT_END));

            String baseUrl = server.url("/").toString();
            var model = testModel(baseUrl);
            var context = new Context("Be helpful.",
                    List.of(new UserMessage("Hello", 1L)), null);
            var eventStream = new AssistantMessageEventStream();

            provider.executeStream(model, context, "test-api-key",
                    4096, 0.7, null, null, eventStream);

            eventStream.result().block();

            // Verify request was sent
            RecordedRequest request = server.takeRequest();
            assertNotNull(request);
            String body = request.getBody().readUtf8();
            assertTrue(body.contains("claude-sonnet-4-20250514"));
            assertTrue(body.contains("4096")); // maxTokens
        }
    }

    // -------------------------------------------------------------------
    // Tool call streaming
    // -------------------------------------------------------------------

    @Nested
    class ToolCallStreaming {

        @Test
        void streamsToolCallResponse() throws Exception {
            String sseBody = sseEvent("message_start",
                    """
                    {"type":"message_start","message":{"id":"msg_789","type":"message","role":"assistant","model":"claude-sonnet-4-20250514","content":[],"stop_reason":null,"stop_sequence":null,"usage":{"input_tokens":20,"output_tokens":0,"cache_creation_input_tokens":0,"cache_read_input_tokens":0}}}""")
                    + sseEvent("content_block_start",
                    """
                    {"type":"content_block_start","index":0,"content_block":{"type":"tool_use","id":"toolu_abc","name":"bash"}}""")
                    + sseEvent("content_block_delta",
                    """
                    {"type":"content_block_delta","index":0,"delta":{"type":"input_json_delta","partial_json":"{\\"com"}}""")
                    + sseEvent("content_block_delta",
                    """
                    {"type":"content_block_delta","index":0,"delta":{"type":"input_json_delta","partial_json":"mand\\":\\"ls\\"}"}}""")
                    + sseEvent("content_block_stop",
                    """
                    {"type":"content_block_stop","index":0}""")
                    + sseEvent("message_delta",
                    """
                    {"type":"message_delta","delta":{"stop_reason":"tool_use","stop_sequence":null},"usage":{"output_tokens":15}}""")
                    + sseEvent("message_stop",
                    """
                    {"type":"message_stop"}""");

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
                    null, null, null, null, eventStream);

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
            assertEquals("toolu_abc", toolCall.id());
            assertEquals("bash", toolCall.name());
            assertEquals("ls", toolCall.arguments().get("command"));
        }
    }

    // -------------------------------------------------------------------
    // Thinking streaming
    // -------------------------------------------------------------------

    @Nested
    class ThinkingStreaming {

        @Test
        void streamsThinkingResponse() throws Exception {
            String sseBody = sseEvent("message_start",
                    """
                    {"type":"message_start","message":{"id":"msg_think","type":"message","role":"assistant","model":"claude-sonnet-4-20250514","content":[],"stop_reason":null,"stop_sequence":null,"usage":{"input_tokens":10,"output_tokens":0,"cache_creation_input_tokens":0,"cache_read_input_tokens":0}}}""")
                    + sseEvent("content_block_start",
                    """
                    {"type":"content_block_start","index":0,"content_block":{"type":"thinking","thinking":""}}""")
                    + sseEvent("content_block_delta",
                    """
                    {"type":"content_block_delta","index":0,"delta":{"type":"thinking_delta","thinking":"Let me think"}}""")
                    + sseEvent("content_block_stop",
                    """
                    {"type":"content_block_stop","index":0}""")
                    + sseEvent("content_block_start",
                    """
                    {"type":"content_block_start","index":1,"content_block":{"type":"text","text":""}}""")
                    + sseEvent("content_block_delta",
                    """
                    {"type":"content_block_delta","index":1,"delta":{"type":"text_delta","text":"Answer"}}""")
                    + sseEvent("content_block_stop",
                    """
                    {"type":"content_block_stop","index":1}""")
                    + sseEvent("message_delta",
                    """
                    {"type":"message_delta","delta":{"stop_reason":"end_turn","stop_sequence":null},"usage":{"output_tokens":20}}""")
                    + sseEvent("message_stop",
                    """
                    {"type":"message_stop"}""");

            server.enqueue(new MockResponse()
                    .setResponseCode(200)
                    .setHeader("Content-Type", "text/event-stream")
                    .setBody(sseBody)
                    .setSocketPolicy(SocketPolicy.DISCONNECT_AT_END));

            String baseUrl = server.url("/").toString();
            var model = testModel(baseUrl);
            var context = new Context(null,
                    List.of(new UserMessage("Think about this", 1L)), null);
            var eventStream = new AssistantMessageEventStream();

            provider.executeStream(model, context, "test-api-key",
                    null, null, ThinkingLevel.MEDIUM, null, eventStream);

            var events = collectEvents(eventStream);
            var finalMsg = eventStream.result().block();

            // Verify thinking events
            assertHasEventType(events, AssistantMessageEvent.ThinkingStartEvent.class);
            assertHasEventType(events, AssistantMessageEvent.ThinkingDeltaEvent.class);
            assertHasEventType(events, AssistantMessageEvent.ThinkingEndEvent.class);

            // Verify content blocks
            assertNotNull(finalMsg);
            assertEquals(2, finalMsg.content().size());
            assertInstanceOf(ThinkingContent.class, finalMsg.content().get(0));
            assertInstanceOf(TextContent.class, finalMsg.content().get(1));
            assertEquals("Let me think", ((ThinkingContent) finalMsg.content().get(0)).thinking());
            assertEquals("Answer", ((TextContent) finalMsg.content().get(1)).text());
        }
    }

    // -------------------------------------------------------------------
    // Usage and cost
    // -------------------------------------------------------------------

    @Nested
    class UsageAndCost {

        @Test
        void tracksUsageWithCacheTokens() throws Exception {
            String sseBody = sseEvent("message_start",
                    """
                    {"type":"message_start","message":{"id":"msg_usage","type":"message","role":"assistant","model":"claude-sonnet-4-20250514","content":[],"stop_reason":null,"stop_sequence":null,"usage":{"input_tokens":100,"output_tokens":0,"cache_creation_input_tokens":50,"cache_read_input_tokens":200}}}""")
                    + sseEvent("content_block_start",
                    """
                    {"type":"content_block_start","index":0,"content_block":{"type":"text","text":""}}""")
                    + sseEvent("content_block_delta",
                    """
                    {"type":"content_block_delta","index":0,"delta":{"type":"text_delta","text":"OK"}}""")
                    + sseEvent("content_block_stop",
                    """
                    {"type":"content_block_stop","index":0}""")
                    + sseEvent("message_delta",
                    """
                    {"type":"message_delta","delta":{"stop_reason":"end_turn","stop_sequence":null},"usage":{"output_tokens":10}}""")
                    + sseEvent("message_stop",
                    """
                    {"type":"message_stop"}""");

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
                    null, null, null, null, eventStream);

            var finalMsg = eventStream.result().block();

            assertNotNull(finalMsg);
            assertEquals(100, finalMsg.usage().input());
            assertEquals(10, finalMsg.usage().output());
            assertEquals(200, finalMsg.usage().cacheRead());
            assertEquals(50, finalMsg.usage().cacheWrite());

            // Verify cost is computed
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
                    .setBody("{\"error\":{\"type\":\"invalid_request_error\",\"message\":\"Bad request\"}}")
                    .setSocketPolicy(SocketPolicy.DISCONNECT_AT_END));

            String baseUrl = server.url("/").toString();
            var model = testModel(baseUrl);
            var context = new Context(null,
                    List.of(new UserMessage("Hello", 1L)), null);
            var eventStream = new AssistantMessageEventStream();

            provider.executeStream(model, context, "test-api-key",
                    null, null, null, null, eventStream);

            assertThrows(Exception.class, () -> eventStream.result().block());
        }

        @Test
        void handlesMissingApiKey() {
            var model = testModel("http://localhost:1");
            var context = new Context(null,
                    List.of(new UserMessage("Hello", 1L)), null);
            var eventStream = new AssistantMessageEventStream();

            provider.executeStream(model, context, null,
                    null, null, null, null, eventStream);

            assertThrows(Exception.class, () -> eventStream.result().block());
        }
    }

    // -------------------------------------------------------------------
    // Stop reason mapping in streaming
    // -------------------------------------------------------------------

    @Nested
    class StopReasonInStreaming {

        @Test
        void mapsMaxTokensStopReason() throws Exception {
            String sseBody = sseEvent("message_start",
                    """
                    {"type":"message_start","message":{"id":"msg_len","type":"message","role":"assistant","model":"claude-sonnet-4-20250514","content":[],"stop_reason":null,"stop_sequence":null,"usage":{"input_tokens":10,"output_tokens":0,"cache_creation_input_tokens":0,"cache_read_input_tokens":0}}}""")
                    + sseEvent("content_block_start",
                    """
                    {"type":"content_block_start","index":0,"content_block":{"type":"text","text":""}}""")
                    + sseEvent("content_block_delta",
                    """
                    {"type":"content_block_delta","index":0,"delta":{"type":"text_delta","text":"truncated"}}""")
                    + sseEvent("content_block_stop",
                    """
                    {"type":"content_block_stop","index":0}""")
                    + sseEvent("message_delta",
                    """
                    {"type":"message_delta","delta":{"stop_reason":"max_tokens","stop_sequence":null},"usage":{"output_tokens":100}}""")
                    + sseEvent("message_stop",
                    """
                    {"type":"message_stop"}""");

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
                    null, null, null, null, eventStream);

            var finalMsg = eventStream.result().block();

            assertNotNull(finalMsg);
            assertEquals(StopReason.LENGTH, finalMsg.stopReason());
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
