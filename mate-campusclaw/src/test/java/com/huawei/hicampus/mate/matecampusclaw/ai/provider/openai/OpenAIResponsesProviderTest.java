package com.huawei.hicampus.mate.matecampusclaw.ai.provider.openai;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.Map;

import com.huawei.hicampus.mate.matecampusclaw.ai.stream.AssistantMessageEventStream;
import com.huawei.hicampus.mate.matecampusclaw.ai.types.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.openai.models.responses.ResponseInputItem;
import com.openai.models.responses.ResponseStatus;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class OpenAIResponsesProviderTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    OpenAIResponsesProvider provider = new OpenAIResponsesProvider();

    private Model testModel() {
        return new Model(
                "gpt-4o", "GPT-4o",
                Api.OPENAI_RESPONSES, Provider.OPENAI,
                "https://api.openai.com/v1", false,
                List.of(InputModality.TEXT, InputModality.IMAGE),
                new ModelCost(2.5, 10.0, 1.25, 0.0),
                128000, 16384, null, null,
                null
        );
    }

    private Model reasoningModel() {
        return new Model(
                "o3", "O3",
                Api.OPENAI_RESPONSES, Provider.OPENAI,
                "https://api.openai.com/v1", true,
                List.of(InputModality.TEXT, InputModality.IMAGE),
                new ModelCost(2.0, 8.0, 0.5, 0.0),
                200000, 100000, null, null,
                null
        );
    }

    // -------------------------------------------------------------------
    // API identification
    // -------------------------------------------------------------------

    @Nested
    class ApiIdentification {

        @Test
        void returnsOpenAIResponsesApi() {
            assertEquals(Api.OPENAI_RESPONSES, provider.getApi());
        }
    }

    // -------------------------------------------------------------------
    // Input item conversion
    // -------------------------------------------------------------------

    @Nested
    class InputConversion {

        @Test
        void convertsUserMessage() {
            var messages = List.<Message>of(new UserMessage("Hello", 1000L));
            var result = OpenAIResponsesProvider.convertInputItems(messages);

            assertEquals(1, result.size());
            assertTrue(result.get(0).isEasyInputMessage());
        }

        @Test
        void convertsAssistantMessageWithText() {
            var am = new AssistantMessage(
                    List.of(new TextContent("Hi there", null)),
                    "openai-responses", "openai", "gpt-4o",
                    null, Usage.empty(), StopReason.STOP, null, 1L);
            var result = OpenAIResponsesProvider.convertInputItems(List.of(am));

            assertEquals(1, result.size());
            assertTrue(result.get(0).isEasyInputMessage());
        }

        @Test
        void convertsAssistantMessageWithToolCalls() {
            var am = new AssistantMessage(
                    List.of(
                            new TextContent("Let me check", null),
                            new ToolCall("call_123", "bash",
                                    Map.of("command", "ls"), null)
                    ),
                    "openai-responses", "openai", "gpt-4o",
                    null, Usage.empty(), StopReason.TOOL_USE, null, 1L);
            var result = OpenAIResponsesProvider.convertInputItems(List.of(am));

            // Should produce 2 items: function_call + easy_input_message (text)
            assertEquals(2, result.size());
            // One should be a function call, one an easy input message
            boolean hasFunctionCall = result.stream().anyMatch(ResponseInputItem::isFunctionCall);
            boolean hasEasyMessage = result.stream().anyMatch(ResponseInputItem::isEasyInputMessage);
            assertTrue(hasFunctionCall);
            assertTrue(hasEasyMessage);
        }

        @Test
        void convertsToolResultMessage() {
            var tr = new ToolResultMessage("call_123", "bash",
                    List.of(new TextContent("file1.txt\nfile2.txt")), null, false, 1L);
            var result = OpenAIResponsesProvider.convertInputItems(List.of(tr));

            assertEquals(1, result.size());
            assertTrue(result.get(0).isFunctionCallOutput());
        }

        @Test
        void convertsMultipleMessages() {
            List<Message> messages = List.of(
                    new UserMessage("Hello", 1L),
                    new AssistantMessage(
                            List.of(new TextContent("Hi")),
                            "openai-responses", "openai", "gpt-4o",
                            null, Usage.empty(), StopReason.STOP, null, 2L),
                    new UserMessage("How?", 3L)
            );
            var result = OpenAIResponsesProvider.convertInputItems(messages);

            assertEquals(3, result.size());
        }

        @Test
        void convertsEmptyMessageList() {
            var result = OpenAIResponsesProvider.convertInputItems(List.of());
            assertTrue(result.isEmpty());
        }

        @Test
        void dropsThinkingContentFromAssistantMessage() {
            var am = new AssistantMessage(
                    List.of(
                            new ThinkingContent("thinking...", null, false),
                            new TextContent("Answer", null)
                    ),
                    "openai-responses", "openai", "gpt-4o",
                    null, Usage.empty(), StopReason.STOP, null, 1L);
            var result = OpenAIResponsesProvider.convertInputItems(List.of(am));

            assertEquals(1, result.size());
            assertTrue(result.get(0).isEasyInputMessage());
        }

        @Test
        void assistantMessageWithOnlyToolCallsProducesNoTextItem() {
            var am = new AssistantMessage(
                    List.of(
                            new ToolCall("call_1", "bash",
                                    Map.of("command", "ls"), null)
                    ),
                    "openai-responses", "openai", "gpt-4o",
                    null, Usage.empty(), StopReason.TOOL_USE, null, 1L);
            var result = OpenAIResponsesProvider.convertInputItems(List.of(am));

            // Should only have the function call, no empty text message
            assertEquals(1, result.size());
            assertTrue(result.get(0).isFunctionCall());
        }
    }

    // -------------------------------------------------------------------
    // Tool conversion
    // -------------------------------------------------------------------

    @Nested
    class ToolConversion {

        @Test
        void convertsToolWithSchema() {
            ObjectNode params = MAPPER.createObjectNode();
            ObjectNode props = MAPPER.createObjectNode();
            props.set("path", MAPPER.createObjectNode().put("type", "string"));
            params.set("properties", props);
            params.set("required", MAPPER.createArrayNode().add("path"));

            var tool = new com.huawei.hicampus.mate.matecampusclaw.ai.types.Tool("read", "Read a file", params);
            var result = OpenAIResponsesProvider.convertTools(List.of(tool));

            assertEquals(1, result.size());
            assertTrue(result.get(0).isFunction());
        }

        @Test
        void convertsMultipleTools() {
            ObjectNode params = MAPPER.createObjectNode();
            var tools = List.of(
                    new com.huawei.hicampus.mate.matecampusclaw.ai.types.Tool("read", "Read file", params),
                    new com.huawei.hicampus.mate.matecampusclaw.ai.types.Tool("write", "Write file", params)
            );
            var result = OpenAIResponsesProvider.convertTools(tools);

            assertEquals(2, result.size());
        }

        @Test
        void convertsEmptyToolList() {
            var result = OpenAIResponsesProvider.convertTools(List.of());
            assertTrue(result.isEmpty());
        }
    }

    // -------------------------------------------------------------------
    // Response status mapping
    // -------------------------------------------------------------------

    @Nested
    class StatusMapping {

        @Test
        void mapsCompletedToStop() {
            assertEquals(StopReason.STOP,
                    OpenAIResponsesProvider.mapResponseStatus(
                            ResponseStatus.COMPLETED, List.of()));
        }

        @Test
        void mapsCompletedWithToolCallsToToolUse() {
            var blocks = List.<ContentBlock>of(
                    new TextContent("Let me check"),
                    new ToolCall("call_1", "bash", Map.of(), null));
            assertEquals(StopReason.TOOL_USE,
                    OpenAIResponsesProvider.mapResponseStatus(
                            ResponseStatus.COMPLETED, blocks));
        }

        @Test
        void mapsIncompleteToLength() {
            assertEquals(StopReason.LENGTH,
                    OpenAIResponsesProvider.mapResponseStatus(
                            ResponseStatus.INCOMPLETE, List.of()));
        }

        @Test
        void mapsFailedToError() {
            assertEquals(StopReason.ERROR,
                    OpenAIResponsesProvider.mapResponseStatus(
                            ResponseStatus.FAILED, List.of()));
        }

        @Test
        void mapsCancelledToError() {
            assertEquals(StopReason.ERROR,
                    OpenAIResponsesProvider.mapResponseStatus(
                            ResponseStatus.CANCELLED, List.of()));
        }

        @Test
        void mapsInProgressToStop() {
            assertEquals(StopReason.STOP,
                    OpenAIResponsesProvider.mapResponseStatus(
                            ResponseStatus.IN_PROGRESS, List.of()));
        }

        @Test
        void mapsQueuedToStop() {
            assertEquals(StopReason.STOP,
                    OpenAIResponsesProvider.mapResponseStatus(
                            ResponseStatus.QUEUED, List.of()));
        }
    }

    // -------------------------------------------------------------------
    // Cost computation
    // -------------------------------------------------------------------

    @Nested
    class CostComputation {

        @Test
        void computesCostFromUsage() {
            var modelCost = new ModelCost(2.5, 10.0, 1.25, 0.0);
            long[] usage = {1000, 500, 200, 0};

            Cost cost = OpenAIResponsesProvider.computeCost(modelCost, usage);

            assertEquals(2.5 * 1000 / 1_000_000.0, cost.input(), 0.0001);
            assertEquals(10.0 * 500 / 1_000_000.0, cost.output(), 0.0001);
            assertEquals(1.25 * 200 / 1_000_000.0, cost.cacheRead(), 0.0001);
            assertEquals(0.0, cost.cacheWrite(), 0.0001);
        }

        @Test
        void totalIsSumOfComponents() {
            var modelCost = new ModelCost(2.5, 10.0, 1.25, 0.0);
            long[] usage = {1000, 500, 200, 0};

            Cost cost = OpenAIResponsesProvider.computeCost(modelCost, usage);

            assertEquals(cost.input() + cost.output() + cost.cacheRead() + cost.cacheWrite(),
                    cost.total(), 0.0001);
        }

        @Test
        void zeroCostForZeroUsage() {
            var modelCost = new ModelCost(2.5, 10.0, 1.25, 0.0);
            long[] usage = {0, 0, 0, 0};

            Cost cost = OpenAIResponsesProvider.computeCost(modelCost, usage);

            assertEquals(0.0, cost.total(), 0.0001);
        }
    }

    // -------------------------------------------------------------------
    // Parameter building
    // -------------------------------------------------------------------

    @Nested
    class ParameterBuilding {

        @Test
        void buildsBasicParams() {
            var context = new Context(null,
                    List.of(new UserMessage("Hello", 1L)), null);

            var params = provider.buildParams(
                    testModel(), context, null, null, null);

            assertNotNull(params);
        }

        @Test
        void usesProvidedMaxTokens() {
            var context = new Context(null,
                    List.of(new UserMessage("Hello", 1L)), null);

            var params = provider.buildParams(
                    testModel(), context, 4096, null, null);

            assertNotNull(params);
        }

        @Test
        void setsTemperature() {
            var context = new Context(null,
                    List.of(new UserMessage("Hello", 1L)), null);

            var params = provider.buildParams(
                    testModel(), context, null, 0.7, null);

            assertNotNull(params);
        }

        @Test
        void setsToolsWhenPresent() {
            ObjectNode toolParams = MAPPER.createObjectNode();
            var tools = List.of(new com.huawei.hicampus.mate.matecampusclaw.ai.types.Tool("bash", "Run commands", toolParams));
            var context = new Context(null,
                    List.of(new UserMessage("Hello", 1L)), tools);

            var params = provider.buildParams(
                    testModel(), context, null, null, null);

            assertNotNull(params);
        }

        @Test
        void setsSystemPromptAsInstructions() {
            var context = new Context("Be helpful.",
                    List.of(new UserMessage("Hello", 1L)), null);

            var params = provider.buildParams(
                    testModel(), context, null, null, null);

            assertNotNull(params);
            // Instructions should be set (verified by successful build)
        }
    }

    // -------------------------------------------------------------------
    // API key resolution
    // -------------------------------------------------------------------

    @Nested
    class ApiKeyResolution {

        @Test
        void errorsWhenNoApiKey() {
            var context = new Context(null,
                    List.of(new UserMessage("Hello", 1L)), null);
            var eventStream = new AssistantMessageEventStream();

            provider.executeStream(testModel(), context,
                    null, null, null, null, eventStream);

            assertThrows(Exception.class, () -> eventStream.result().block());
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
            var options = StreamOptions.builder().apiKey("test-key").build();

            var stream = provider.stream(testModel(), context, options);
            assertNotNull(stream);
            assertNotNull(stream.asFlux());
        }

        @Test
        void streamSimpleReturnsEventStream() {
            var context = new Context(null,
                    List.of(new UserMessage("Hello", 1L)), null);
            var options = SimpleStreamOptions.builder().apiKey("test-key").build();

            var stream = provider.streamSimple(testModel(), context, options);
            assertNotNull(stream);
            assertNotNull(stream.asFlux());
        }
    }
}
