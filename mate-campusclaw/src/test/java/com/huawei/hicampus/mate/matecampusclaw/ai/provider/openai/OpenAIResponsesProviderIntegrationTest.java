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
 * Integration tests for {@link OpenAIResponsesProvider} using MockWebServer
 * to simulate real OpenAI Responses API SSE events.
 */
@Timeout(30)
class OpenAIResponsesProviderIntegrationTest {

    private MockWebServer server;
    private OpenAIResponsesProvider provider;

    private Model testModel(String baseUrl) {
        return new Model(
                "gpt-4o", "GPT-4o",
                Api.OPENAI_RESPONSES, Provider.OPENAI,
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
        provider = new OpenAIResponsesProvider();
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
            String sseBody = sseEvent("response.created",
                    """
                    {"type":"response.created","response":{"id":"resp_123","object":"response","status":"in_progress","output":[],"usage":null}}""")
                    + sseEvent("response.output_item.added",
                    """
                    {"type":"response.output_item.added","output_index":0,"item":{"type":"message","id":"msg_out1","role":"assistant","content":[],"status":"in_progress"}}""")
                    + sseEvent("response.output_text.delta",
                    """
                    {"type":"response.output_text.delta","output_index":0,"content_index":0,"delta":"Hello"}""")
                    + sseEvent("response.output_text.delta",
                    """
                    {"type":"response.output_text.delta","output_index":0,"content_index":0,"delta":" world"}""")
                    + sseEvent("response.output_item.done",
                    """
                    {"type":"response.output_item.done","output_index":0,"item":{"type":"message","id":"msg_out1","role":"assistant","content":[{"type":"output_text","text":"Hello world"}],"status":"completed"}}""")
                    + sseEvent("response.completed",
                    """
                    {"type":"response.completed","response":{"id":"resp_123","object":"response","status":"completed","output":[{"type":"message","id":"msg_out1","role":"assistant","content":[{"type":"output_text","text":"Hello world"}],"status":"completed"}],"usage":{"input_tokens":10,"output_tokens":5,"input_tokens_details":{"cached_tokens":0},"output_tokens_details":{"reasoning_tokens":0}}}}""");

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

            // Verify final message
            assertNotNull(finalMsg);
            assertEquals("resp_123", finalMsg.responseId());
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
            String sseBody = sseEvent("response.created",
                    """
                    {"type":"response.created","response":{"id":"resp_456","object":"response","status":"in_progress","output":[],"usage":null}}""")
                    + sseEvent("response.output_item.added",
                    """
                    {"type":"response.output_item.added","output_index":0,"item":{"type":"message","id":"msg_out2","role":"assistant","content":[],"status":"in_progress"}}""")
                    + sseEvent("response.output_text.delta",
                    """
                    {"type":"response.output_text.delta","output_index":0,"content_index":0,"delta":"OK"}""")
                    + sseEvent("response.output_item.done",
                    """
                    {"type":"response.output_item.done","output_index":0,"item":{"type":"message","id":"msg_out2","role":"assistant","content":[{"type":"output_text","text":"OK"}],"status":"completed"}}""")
                    + sseEvent("response.completed",
                    """
                    {"type":"response.completed","response":{"id":"resp_456","object":"response","status":"completed","output":[],"usage":{"input_tokens":5,"output_tokens":1,"input_tokens_details":{"cached_tokens":0},"output_tokens_details":{"reasoning_tokens":0}}}}""");

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
            assertTrue(body.contains("Be concise."));
        }
    }

    // -------------------------------------------------------------------
    // Tool call streaming
    // -------------------------------------------------------------------

    @Nested
    class ToolCallStreaming {

        @Test
        void streamsToolCallResponse() throws Exception {
            String sseBody = sseEvent("response.created",
                    """
                    {"type":"response.created","response":{"id":"resp_tc","object":"response","status":"in_progress","output":[],"usage":null}}""")
                    + sseEvent("response.output_item.added",
                    """
                    {"type":"response.output_item.added","output_index":0,"item":{"type":"function_call","id":"fc_1","call_id":"call_xyz","name":"bash","arguments":"","status":"in_progress"}}""")
                    + sseEvent("response.function_call_arguments.delta",
                    """
                    {"type":"response.function_call_arguments.delta","output_index":0,"delta":"{\\"com"}""")
                    + sseEvent("response.function_call_arguments.delta",
                    """
                    {"type":"response.function_call_arguments.delta","output_index":0,"delta":"mand\\":\\"ls\\"}"}""")
                    + sseEvent("response.output_item.done",
                    """
                    {"type":"response.output_item.done","output_index":0,"item":{"type":"function_call","id":"fc_1","call_id":"call_xyz","name":"bash","arguments":"{\\"command\\":\\"ls\\"}","status":"completed"}}""")
                    + sseEvent("response.completed",
                    """
                    {"type":"response.completed","response":{"id":"resp_tc","object":"response","status":"completed","output":[{"type":"function_call","id":"fc_1","call_id":"call_xyz","name":"bash","arguments":"{\\"command\\":\\"ls\\"}","status":"completed"}],"usage":{"input_tokens":20,"output_tokens":15,"input_tokens_details":{"cached_tokens":0},"output_tokens_details":{"reasoning_tokens":0}}}}""");

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

            // Verify final message - has tool calls so stop reason = TOOL_USE
            assertNotNull(finalMsg);
            assertEquals(StopReason.TOOL_USE, finalMsg.stopReason());
            assertEquals(1, finalMsg.content().size());
            assertInstanceOf(ToolCall.class, finalMsg.content().get(0));
            var toolCall = (ToolCall) finalMsg.content().get(0);
            assertEquals("call_xyz", toolCall.id());
            assertEquals("bash", toolCall.name());
            assertEquals("ls", toolCall.arguments().get("command"));
        }
    }

    // -------------------------------------------------------------------
    // Thinking/reasoning streaming
    // -------------------------------------------------------------------

    @Nested
    class ThinkingStreaming {

        @Test
        void streamsReasoningResponse() throws Exception {
            String sseBody = sseEvent("response.created",
                    """
                    {"type":"response.created","response":{"id":"resp_think","object":"response","status":"in_progress","output":[],"usage":null}}""")
                    + sseEvent("response.output_item.added",
                    """
                    {"type":"response.output_item.added","output_index":0,"item":{"type":"reasoning","id":"rs_1","summary":[]}}""")
                    + sseEvent("response.reasoning_summary_text.delta",
                    """
                    {"type":"response.reasoning_summary_text.delta","output_index":0,"summary_index":0,"delta":"Let me think"}""")
                    + sseEvent("response.output_item.done",
                    """
                    {"type":"response.output_item.done","output_index":0,"item":{"type":"reasoning","id":"rs_1","summary":[{"type":"summary_text","text":"Let me think"}]}}""")
                    + sseEvent("response.output_item.added",
                    """
                    {"type":"response.output_item.added","output_index":1,"item":{"type":"message","id":"msg_out3","role":"assistant","content":[],"status":"in_progress"}}""")
                    + sseEvent("response.output_text.delta",
                    """
                    {"type":"response.output_text.delta","output_index":1,"content_index":0,"delta":"Answer"}""")
                    + sseEvent("response.output_item.done",
                    """
                    {"type":"response.output_item.done","output_index":1,"item":{"type":"message","id":"msg_out3","role":"assistant","content":[{"type":"output_text","text":"Answer"}],"status":"completed"}}""")
                    + sseEvent("response.completed",
                    """
                    {"type":"response.completed","response":{"id":"resp_think","object":"response","status":"completed","output":[],"usage":{"input_tokens":10,"output_tokens":20,"input_tokens_details":{"cached_tokens":0},"output_tokens_details":{"reasoning_tokens":10}}}}""");

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
                    null, null, null, eventStream);

            var events = collectEvents(eventStream);
            var finalMsg = eventStream.result().block();

            // Verify thinking events
            assertHasEventType(events, AssistantMessageEvent.ThinkingStartEvent.class);
            assertHasEventType(events, AssistantMessageEvent.ThinkingDeltaEvent.class);
            assertHasEventType(events, AssistantMessageEvent.ThinkingEndEvent.class);

            // Verify text events
            assertHasEventType(events, AssistantMessageEvent.TextStartEvent.class);
            assertHasEventType(events, AssistantMessageEvent.TextEndEvent.class);

            // Verify content blocks
            assertNotNull(finalMsg);
            assertEquals(2, finalMsg.content().size());
            assertInstanceOf(ThinkingContent.class, finalMsg.content().get(0));
            assertInstanceOf(TextContent.class, finalMsg.content().get(1));
        }
    }

    // -------------------------------------------------------------------
    // Usage statistics
    // -------------------------------------------------------------------

    @Nested
    class UsageStatistics {

        @Test
        void tracksUsageWithCachedTokens() throws Exception {
            String sseBody = sseEvent("response.created",
                    """
                    {"type":"response.created","response":{"id":"resp_u","object":"response","status":"in_progress","output":[],"usage":null}}""")
                    + sseEvent("response.output_item.added",
                    """
                    {"type":"response.output_item.added","output_index":0,"item":{"type":"message","id":"msg_u","role":"assistant","content":[],"status":"in_progress"}}""")
                    + sseEvent("response.output_text.delta",
                    """
                    {"type":"response.output_text.delta","output_index":0,"content_index":0,"delta":"OK"}""")
                    + sseEvent("response.output_item.done",
                    """
                    {"type":"response.output_item.done","output_index":0,"item":{"type":"message","id":"msg_u","role":"assistant","content":[{"type":"output_text","text":"OK"}],"status":"completed"}}""")
                    + sseEvent("response.completed",
                    """
                    {"type":"response.completed","response":{"id":"resp_u","object":"response","status":"completed","output":[],"usage":{"input_tokens":100,"output_tokens":5,"input_tokens_details":{"cached_tokens":30},"output_tokens_details":{"reasoning_tokens":0}}}}""");

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

        @Test
        void handlesIncompleteResponse() throws Exception {
            String sseBody = sseEvent("response.created",
                    """
                    {"type":"response.created","response":{"id":"resp_inc","object":"response","status":"in_progress","output":[],"usage":null}}""")
                    + sseEvent("response.output_item.added",
                    """
                    {"type":"response.output_item.added","output_index":0,"item":{"type":"message","id":"msg_inc","role":"assistant","content":[],"status":"in_progress"}}""")
                    + sseEvent("response.output_text.delta",
                    """
                    {"type":"response.output_text.delta","output_index":0,"content_index":0,"delta":"truncated"}""")
                    + sseEvent("response.output_item.done",
                    """
                    {"type":"response.output_item.done","output_index":0,"item":{"type":"message","id":"msg_inc","role":"assistant","content":[{"type":"output_text","text":"truncated"}],"status":"completed"}}""")
                    + sseEvent("response.incomplete",
                    """
                    {"type":"response.incomplete","response":{"id":"resp_inc","object":"response","status":"incomplete","output":[],"usage":{"input_tokens":10,"output_tokens":100,"input_tokens_details":{"cached_tokens":0},"output_tokens_details":{"reasoning_tokens":0}}}}""");

            server.enqueue(new MockResponse()
                    .setResponseCode(200)
                    .setHeader("Content-Type", "text/event-stream")
                    .setBody(sseBody)
                    .setSocketPolicy(SocketPolicy.DISCONNECT_AT_END));

            String baseUrl = server.url("/").toString();
            var model = testModel(baseUrl);
            var context = new Context(null,
                    List.of(new UserMessage("Write a lot", 1L)), null);
            var eventStream = new AssistantMessageEventStream();

            provider.executeStream(model, context, "test-api-key",
                    null, null, null, eventStream);

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
