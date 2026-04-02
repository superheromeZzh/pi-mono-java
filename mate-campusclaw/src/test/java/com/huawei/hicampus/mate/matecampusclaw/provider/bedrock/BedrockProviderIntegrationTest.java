package com.huawei.hicampus.mate.matecampusclaw.ai.provider.bedrock;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import com.huawei.hicampus.mate.matecampusclaw.ai.stream.AssistantMessageEvent;
import com.huawei.hicampus.mate.matecampusclaw.ai.stream.AssistantMessageEventStream;
import com.huawei.hicampus.mate.matecampusclaw.ai.types.Api;
import com.huawei.hicampus.mate.matecampusclaw.ai.types.AssistantMessage;
import com.huawei.hicampus.mate.matecampusclaw.ai.types.Context;
import com.huawei.hicampus.mate.matecampusclaw.ai.types.Cost;
import com.huawei.hicampus.mate.matecampusclaw.ai.types.InputModality;
import com.huawei.hicampus.mate.matecampusclaw.ai.types.Model;
import com.huawei.hicampus.mate.matecampusclaw.ai.types.ModelCost;
import com.huawei.hicampus.mate.matecampusclaw.ai.types.Provider;
import com.huawei.hicampus.mate.matecampusclaw.ai.types.StopReason;
import com.huawei.hicampus.mate.matecampusclaw.ai.types.TextContent;
import com.huawei.hicampus.mate.matecampusclaw.ai.types.ToolCall;
import com.huawei.hicampus.mate.matecampusclaw.ai.types.ToolResultMessage;
import com.huawei.hicampus.mate.matecampusclaw.ai.types.Usage;
import com.huawei.hicampus.mate.matecampusclaw.ai.types.UserMessage;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeAsyncClient;
import software.amazon.awssdk.services.bedrockruntime.model.ConverseStreamRequest;
import software.amazon.awssdk.services.bedrockruntime.model.ConverseStreamResponseHandler;
import software.amazon.awssdk.services.bedrockruntime.model.TokenUsage;

/**
 * Integration tests for {@link BedrockProvider} using Mockito to simulate
 * AWS Bedrock ConverseStream event callbacks.
 *
 * <p>Since AWS Bedrock uses a binary event stream protocol (not HTTP SSE),
 * we mock the {@link BedrockRuntimeAsyncClient} and verify the provider's
 * request building, event mapping, usage tracking, and error handling.
 */
@ExtendWith(MockitoExtension.class)
class BedrockProviderIntegrationTest {

    @Mock
    private BedrockRuntimeAsyncClient mockClient;

    private BedrockProvider provider;

    private Model testModel() {
        return new Model(
                "anthropic.claude-sonnet-4-20250514-v1:0", "Claude Sonnet 4 (Bedrock)",
                Api.BEDROCK_CONVERSE_STREAM, Provider.AMAZON_BEDROCK,
                null, false,
                List.of(InputModality.TEXT, InputModality.IMAGE),
                new ModelCost(3.0, 15.0, 0.3, 3.75),
                200000, 8192, null, null,
                null
        );
    }

    @BeforeEach
    void setUp() {
        // Subclass that returns our mock client
        provider = new BedrockProvider() {
            @Override
            BedrockRuntimeAsyncClient buildClient() {
                return mockClient;
            }
        };
    }

    // -------------------------------------------------------------------
    // Empty stream (default behavior)
    // -------------------------------------------------------------------

    @Nested
    class EmptyStream {

        @Test
        void producesDefaultDoneEvent() {
            when(mockClient.converseStream(any(ConverseStreamRequest.class),
                    any(ConverseStreamResponseHandler.class)))
                    .thenReturn(CompletableFuture.completedFuture(null));

            var context = new Context(null,
                    List.of(new UserMessage("Hi", 1L)), null);
            var eventStream = new AssistantMessageEventStream();

            provider.executeStream(testModel(), context, null, null, null, null, eventStream);

            var events = collectEvents(eventStream);
            var finalMsg = eventStream.result().block();

            assertNotNull(finalMsg);
            assertEquals(StopReason.STOP, finalMsg.stopReason());
            assertHasEventType(events, AssistantMessageEvent.DoneEvent.class);
        }
    }

    // -------------------------------------------------------------------
    // Request building verification
    // -------------------------------------------------------------------

    @Nested
    class RequestBuilding {

        @Test
        void buildsCorrectRequestWithSystemPrompt() {
            var context = new Context("Be helpful.",
                    List.of(new UserMessage("Hello", 1L)), null);

            var request = provider.buildRequest(testModel(), context, null, null, null, null);

            assertEquals("anthropic.claude-sonnet-4-20250514-v1:0", request.modelId());
            assertFalse(request.system().isEmpty());
            assertNotNull(request.inferenceConfig());
        }

        @Test
        void buildsRequestWithTools() {
            var tools = List.of(
                    new com.huawei.hicampus.mate.matecampusclaw.ai.types.Tool("bash", "Run commands", null));
            var context = new Context(null,
                    List.of(new UserMessage("Hello", 1L)), tools);

            var request = provider.buildRequest(testModel(), context, null, null, null, null);

            assertNotNull(request.toolConfig());
            assertEquals(1, request.toolConfig().tools().size());
        }

        @Test
        void buildsRequestWithCustomMaxTokensAndTemperature() {
            var context = new Context(null,
                    List.of(new UserMessage("Hello", 1L)), null);

            var request = provider.buildRequest(testModel(), context, 4096, 0.7, null, null);

            assertEquals(4096, request.inferenceConfig().maxTokens());
            assertEquals(0.7f, request.inferenceConfig().temperature(), 0.01);
        }

        @Test
        void omitsSystemPromptWhenNull() {
            var context = new Context(null,
                    List.of(new UserMessage("Hello", 1L)), null);

            var request = provider.buildRequest(testModel(), context, null, null, null, null);

            assertTrue(request.system().isEmpty());
        }

        @Test
        void omitsToolConfigWhenNoTools() {
            var context = new Context(null,
                    List.of(new UserMessage("Hello", 1L)), null);

            var request = provider.buildRequest(testModel(), context, null, null, null, null);

            assertNull(request.toolConfig());
        }

        @Test
        void verifiesRequestSentToClient() {
            when(mockClient.converseStream(any(ConverseStreamRequest.class),
                    any(ConverseStreamResponseHandler.class)))
                    .thenReturn(CompletableFuture.completedFuture(null));

            var context = new Context("System prompt",
                    List.of(new UserMessage("Hello", 1L)), null);
            var eventStream = new AssistantMessageEventStream();

            provider.executeStream(testModel(), context, null, null, null, null, eventStream);
            eventStream.result().block();

            verify(mockClient).converseStream(any(ConverseStreamRequest.class),
                    any(ConverseStreamResponseHandler.class));
        }
    }

    // -------------------------------------------------------------------
    // Message conversion in streaming context
    // -------------------------------------------------------------------

    @Nested
    class MessageConversionInContext {

        @Test
        void convertsContextWithMultipleMessageTypes() {
            List<com.huawei.hicampus.mate.matecampusclaw.ai.types.Message> messages = List.of(
                    new UserMessage("Hello", 1L),
                    new AssistantMessage(
                            List.of(new TextContent("Hi", null),
                                    new ToolCall("toolu_1", "bash",
                                            Map.of("command", "ls"), null)),
                            "bedrock-converse-stream", "amazon-bedrock",
                            "anthropic.claude-sonnet-4-20250514-v1:0",
                            null, Usage.empty(), StopReason.TOOL_USE, null, 2L),
                    new ToolResultMessage("toolu_1", "bash",
                            List.of(new TextContent("file1.txt")), null, false, 3L),
                    new UserMessage("Good", 4L)
            );

            var result = BedrockProvider.convertMessages(messages);
            assertEquals(4, result.size());
            assertEquals("user", result.get(0).roleAsString());
            assertEquals("assistant", result.get(1).roleAsString());
            assertEquals("user", result.get(2).roleAsString());
            assertEquals("user", result.get(3).roleAsString());
        }
    }

    // -------------------------------------------------------------------
    // Stop reason mapping
    // -------------------------------------------------------------------

    @Nested
    class StopReasonInStreaming {

        @Test
        void mapsEndTurnToStop() {
            assertEquals(StopReason.STOP, BedrockProvider.mapStopReason("end_turn"));
        }

        @Test
        void mapsToolUse() {
            assertEquals(StopReason.TOOL_USE, BedrockProvider.mapStopReason("tool_use"));
        }

        @Test
        void mapsMaxTokensToLength() {
            assertEquals(StopReason.LENGTH, BedrockProvider.mapStopReason("max_tokens"));
        }

        @Test
        void mapsContentFilteredToError() {
            assertEquals(StopReason.ERROR, BedrockProvider.mapStopReason("content_filtered"));
        }

        @Test
        void mapsNullToStop() {
            assertEquals(StopReason.STOP, BedrockProvider.mapStopReason(null));
        }
    }

    // -------------------------------------------------------------------
    // Usage parsing
    // -------------------------------------------------------------------

    @Nested
    class UsageParsingInStreaming {

        @Test
        void parsesUsageWithAllFields() {
            var tokenUsage = TokenUsage.builder()
                    .inputTokens(1000)
                    .outputTokens(500)
                    .totalTokens(1500)
                    .cacheReadInputTokens(200)
                    .cacheWriteInputTokens(50)
                    .build();
            long[] accumulated = {0, 0, 0, 0};

            BedrockProvider.parseUsage(tokenUsage, accumulated);

            assertEquals(800, accumulated[0]); // 1000 - 200
            assertEquals(500, accumulated[1]);
            assertEquals(200, accumulated[2]);
            assertEquals(50, accumulated[3]);
        }

        @Test
        void computesCostFromAccumulatedUsage() {
            var modelCost = new ModelCost(3.0, 15.0, 0.3, 3.75);
            long[] usage = {800, 500, 200, 50};

            Cost cost = BedrockProvider.computeCost(modelCost, usage);

            assertEquals(3.0 * 800 / 1_000_000.0, cost.input(), 0.0001);
            assertEquals(15.0 * 500 / 1_000_000.0, cost.output(), 0.0001);
            assertEquals(0.3 * 200 / 1_000_000.0, cost.cacheRead(), 0.0001);
            assertEquals(3.75 * 50 / 1_000_000.0, cost.cacheWrite(), 0.0001);
            assertTrue(cost.total() > 0);
        }
    }

    // -------------------------------------------------------------------
    // Error handling
    // -------------------------------------------------------------------

    @Nested
    class ErrorHandling {

        @Test
        void handlesClientException() {
            when(mockClient.converseStream(any(ConverseStreamRequest.class),
                    any(ConverseStreamResponseHandler.class)))
                    .thenThrow(new RuntimeException("Connection failed"));

            var context = new Context(null,
                    List.of(new UserMessage("Hello", 1L)), null);
            var eventStream = new AssistantMessageEventStream();

            provider.executeStream(testModel(), context, null, null, null, null, eventStream);

            assertThrows(Exception.class, () -> eventStream.result().block());
            verify(mockClient).close();
        }

        @Test
        void handlesCompletableFutureFailure() {
            when(mockClient.converseStream(any(ConverseStreamRequest.class),
                    any(ConverseStreamResponseHandler.class)))
                    .thenReturn(CompletableFuture.failedFuture(
                            new RuntimeException("Service unavailable")));

            var context = new Context(null,
                    List.of(new UserMessage("Hello", 1L)), null);
            var eventStream = new AssistantMessageEventStream();

            provider.executeStream(testModel(), context, null, null, null, null, eventStream);

            assertThrows(Exception.class, () -> eventStream.result().block());
        }
    }

    // -------------------------------------------------------------------
    // Client lifecycle
    // -------------------------------------------------------------------

    @Nested
    class ClientLifecycle {

        @Test
        void closesClientAfterSuccessfulExecution() {
            when(mockClient.converseStream(any(ConverseStreamRequest.class),
                    any(ConverseStreamResponseHandler.class)))
                    .thenReturn(CompletableFuture.completedFuture(null));

            var context = new Context(null,
                    List.of(new UserMessage("Hello", 1L)), null);
            var eventStream = new AssistantMessageEventStream();

            provider.executeStream(testModel(), context, null, null, null, null, eventStream);

            eventStream.result().block();
            verify(mockClient).close();
        }

        @Test
        void closesClientOnException() {
            when(mockClient.converseStream(any(ConverseStreamRequest.class),
                    any(ConverseStreamResponseHandler.class)))
                    .thenThrow(new RuntimeException("Connection failed"));

            var context = new Context(null,
                    List.of(new UserMessage("Hello", 1L)), null);
            var eventStream = new AssistantMessageEventStream();

            provider.executeStream(testModel(), context, null, null, null, null, eventStream);

            assertThrows(Exception.class, () -> eventStream.result().block());
            verify(mockClient).close();
        }
    }

    // -------------------------------------------------------------------
    // Stream interface
    // -------------------------------------------------------------------

    @Nested
    class StreamInterface {

        @Test
        void streamReturnsEventStream() {
            var context = new Context(null,
                    List.of(new UserMessage("Hello", 1L)), null);

            var stream = provider.stream(testModel(), context, null);
            assertNotNull(stream);
            assertNotNull(stream.asFlux());
        }

        @Test
        void streamSimpleReturnsEventStream() {
            var context = new Context(null,
                    List.of(new UserMessage("Hello", 1L)), null);

            var stream = provider.streamSimple(testModel(), context, null);
            assertNotNull(stream);
            assertNotNull(stream.asFlux());
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
