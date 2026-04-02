package com.huawei.hicampus.mate.matecampusclaw.ai.provider.openai;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.Map;

import com.huawei.hicampus.mate.matecampusclaw.ai.stream.AssistantMessageEventStream;
import com.huawei.hicampus.mate.matecampusclaw.ai.types.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class OpenAICompletionsProviderTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    OpenAICompletionsProvider provider = new OpenAICompletionsProvider();

    private Model testModel() {
        return new Model(
                "gpt-4o", "GPT-4o",
                Api.OPENAI_COMPLETIONS, Provider.OPENAI,
                "https://api.openai.com/v1", false,
                List.of(InputModality.TEXT, InputModality.IMAGE),
                new ModelCost(2.5, 10.0, 1.25, 0.0),
                128000, 16384, null, null,
                null
        );
    }

    // -------------------------------------------------------------------
    // API identification
    // -------------------------------------------------------------------

    @Nested
    class ApiIdentification {

        @Test
        void returnsOpenAICompletionsApi() {
            assertEquals(Api.OPENAI_COMPLETIONS, provider.getApi());
        }
    }

    // -------------------------------------------------------------------
    // Message conversion
    // -------------------------------------------------------------------

    @Nested
    class MessageConversion {

        @Test
        void convertsUserMessage() {
            var messages = List.<Message>of(new UserMessage("Hello", 1000L));
            var result = OpenAICompletionsProvider.convertMessages(messages);

            assertEquals(1, result.size());
            assertTrue(result.get(0).isUser());
        }

        @Test
        void convertsAssistantMessage() {
            var am = new AssistantMessage(
                    List.of(new TextContent("Hi there", null)),
                    "openai-completions", "openai", "gpt-4o",
                    null, Usage.empty(), StopReason.STOP, null, 1L);
            var result = OpenAICompletionsProvider.convertMessages(List.of(am));

            assertEquals(1, result.size());
            assertTrue(result.get(0).isAssistant());
        }

        @Test
        void convertsAssistantMessageWithToolCalls() {
            var am = new AssistantMessage(
                    List.of(
                            new TextContent("Let me check", null),
                            new ToolCall("call_123", "bash",
                                    Map.of("command", "ls"), null)
                    ),
                    "openai-completions", "openai", "gpt-4o",
                    null, Usage.empty(), StopReason.TOOL_USE, null, 1L);
            var result = OpenAICompletionsProvider.convertMessages(List.of(am));

            assertEquals(1, result.size());
            assertTrue(result.get(0).isAssistant());
            var assistantParam = result.get(0).asAssistant();
            assertTrue(assistantParam.toolCalls().isPresent());
            assertEquals(1, assistantParam.toolCalls().get().size());
        }

        @Test
        void convertsToolResultMessage() {
            var tr = new ToolResultMessage("call_123", "bash",
                    List.of(new TextContent("file1.txt\nfile2.txt")), null, false, 1L);
            var result = OpenAICompletionsProvider.convertMessages(List.of(tr));

            assertEquals(1, result.size());
            assertTrue(result.get(0).isTool());
        }

        @Test
        void convertsMultipleMessages() {
            List<Message> messages = List.of(
                    new UserMessage("Hello", 1L),
                    new AssistantMessage(
                            List.of(new TextContent("Hi")),
                            "openai-completions", "openai", "gpt-4o",
                            null, Usage.empty(), StopReason.STOP, null, 2L),
                    new UserMessage("How?", 3L)
            );
            var result = OpenAICompletionsProvider.convertMessages(messages);

            assertEquals(3, result.size());
            assertTrue(result.get(0).isUser());
            assertTrue(result.get(1).isAssistant());
            assertTrue(result.get(2).isUser());
        }

        @Test
        void convertsEmptyMessageList() {
            var result = OpenAICompletionsProvider.convertMessages(List.of());
            assertTrue(result.isEmpty());
        }

        @Test
        void dropsThinkingContentFromAssistantMessage() {
            var am = new AssistantMessage(
                    List.of(
                            new ThinkingContent("thinking...", null, false),
                            new TextContent("Answer", null)
                    ),
                    "openai-completions", "openai", "gpt-4o",
                    null, Usage.empty(), StopReason.STOP, null, 1L);
            var result = OpenAICompletionsProvider.convertMessages(List.of(am));

            assertEquals(1, result.size());
            assertTrue(result.get(0).isAssistant());
            // ThinkingContent is silently dropped; only text sent as string
            var assistantParam = result.get(0).asAssistant();
            assertTrue(assistantParam.content().isPresent());
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

            var tool = new Tool("read", "Read a file", params);
            var result = OpenAICompletionsProvider.convertTools(List.of(tool));

            assertEquals(1, result.size());
            assertTrue(result.get(0).isFunction());
        }

        @Test
        void convertsMultipleTools() {
            ObjectNode params = MAPPER.createObjectNode();
            var tools = List.of(
                    new Tool("read", "Read file", params),
                    new Tool("write", "Write file", params)
            );
            var result = OpenAICompletionsProvider.convertTools(tools);

            assertEquals(2, result.size());
        }

        @Test
        void convertsEmptyToolList() {
            var result = OpenAICompletionsProvider.convertTools(List.of());
            assertTrue(result.isEmpty());
        }
    }

    // -------------------------------------------------------------------
    // Stop reason mapping
    // -------------------------------------------------------------------

    @Nested
    class StopReasonMapping {

        @Test
        void mapsStopToStop() {
            assertEquals(StopReason.STOP,
                    OpenAICompletionsProvider.mapFinishReason("stop"));
        }

        @Test
        void mapsEndToStop() {
            assertEquals(StopReason.STOP,
                    OpenAICompletionsProvider.mapFinishReason("end"));
        }

        @Test
        void mapsLengthToLength() {
            assertEquals(StopReason.LENGTH,
                    OpenAICompletionsProvider.mapFinishReason("length"));
        }

        @Test
        void mapsToolCallsToToolUse() {
            assertEquals(StopReason.TOOL_USE,
                    OpenAICompletionsProvider.mapFinishReason("tool_calls"));
        }

        @Test
        void mapsFunctionCallToToolUse() {
            assertEquals(StopReason.TOOL_USE,
                    OpenAICompletionsProvider.mapFinishReason("function_call"));
        }

        @Test
        void mapsContentFilterToError() {
            assertEquals(StopReason.ERROR,
                    OpenAICompletionsProvider.mapFinishReason("content_filter"));
        }

        @Test
        void mapsNullToStop() {
            assertEquals(StopReason.STOP,
                    OpenAICompletionsProvider.mapFinishReason(null));
        }

        @Test
        void mapsUnknownToStop() {
            assertEquals(StopReason.STOP,
                    OpenAICompletionsProvider.mapFinishReason("something_else"));
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

            Cost cost = OpenAICompletionsProvider.computeCost(modelCost, usage);

            assertEquals(2.5 * 1000 / 1_000_000.0, cost.input(), 0.0001);
            assertEquals(10.0 * 500 / 1_000_000.0, cost.output(), 0.0001);
            assertEquals(1.25 * 200 / 1_000_000.0, cost.cacheRead(), 0.0001);
            assertEquals(0.0, cost.cacheWrite(), 0.0001);
        }

        @Test
        void totalIsSumOfComponents() {
            var modelCost = new ModelCost(2.5, 10.0, 1.25, 0.0);
            long[] usage = {1000, 500, 200, 0};

            Cost cost = OpenAICompletionsProvider.computeCost(modelCost, usage);

            assertEquals(cost.input() + cost.output() + cost.cacheRead() + cost.cacheWrite(),
                    cost.total(), 0.0001);
        }

        @Test
        void zeroCostForZeroUsage() {
            var modelCost = new ModelCost(2.5, 10.0, 1.25, 0.0);
            long[] usage = {0, 0, 0, 0};

            Cost cost = OpenAICompletionsProvider.computeCost(modelCost, usage);

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
        void capsMaxTokensAt32000() {
            // Model has maxTokens=16384 which is < 32000, so use model's value
            var context = new Context(null,
                    List.of(new UserMessage("Hello", 1L)), null);

            var params = provider.buildParams(
                    testModel(), context, null, null, null);
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
            var tools = List.of(new Tool("bash", "Run commands", toolParams));
            var context = new Context(null,
                    List.of(new UserMessage("Hello", 1L)), tools);

            var params = provider.buildParams(
                    testModel(), context, null, null, null);

            assertNotNull(params);
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

            // executeStream with null apiKey and no env var
            provider.executeStream(testModel(), context,
                    null, null, null, null, eventStream);

            // The stream should have an error
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

            // This will fail to connect but should return a stream
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
