package com.huawei.hicampus.mate.matecampusclaw.ai.provider.anthropic;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;

import com.anthropic.models.messages.MessageCreateParams;
import com.anthropic.models.messages.MessageParam;
import com.huawei.hicampus.mate.matecampusclaw.ai.types.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class AnthropicProviderTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    AnthropicProvider provider = new AnthropicProvider();

    private Model testModel() {
        return new Model(
                "claude-sonnet-4-20250514", "Claude Sonnet 4",
                Api.ANTHROPIC_MESSAGES, Provider.ANTHROPIC,
                "https://api.anthropic.com", true,
                List.of(InputModality.TEXT, InputModality.IMAGE),
                new ModelCost(3.0, 15.0, 0.3, 3.75),
                200000, 16000, null, null,
                null
        );
    }

    // -------------------------------------------------------------------
    // API identification
    // -------------------------------------------------------------------

    @Nested
    class ApiIdentification {

        @Test
        void returnsAnthropicMessagesApi() {
            assertEquals(Api.ANTHROPIC_MESSAGES, provider.getApi());
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
            var result = AnthropicProvider.convertMessages(messages, false);

            assertEquals(1, result.size());
            assertEquals(MessageParam.Role.USER, result.get(0).role());
        }

        @Test
        void convertsAssistantMessage() {
            var am = new AssistantMessage(
                    List.of(new TextContent("Hi there", null)),
                    "messages", "anthropic", "model",
                    null, Usage.empty(), StopReason.STOP, null, 1L);
            var result = AnthropicProvider.convertMessages(List.of(am), false);

            assertEquals(1, result.size());
            assertEquals(MessageParam.Role.ASSISTANT, result.get(0).role());
        }

        @Test
        void convertsToolResultAsUserMessage() {
            var tr = new ToolResultMessage("tc-1", "bash",
                    List.of(new TextContent("output")), null, false, 1L);
            var result = AnthropicProvider.convertMessages(List.of(tr), false);

            assertEquals(1, result.size());
            // Tool results are sent as USER messages in the Anthropic API
            assertEquals(MessageParam.Role.USER, result.get(0).role());
        }

        @Test
        void convertsMultipleMessages() {
            List<Message> messages = List.of(
                    new UserMessage("Hello", 1L),
                    new AssistantMessage(
                            List.of(new TextContent("Hi")),
                            "messages", "anthropic", "model",
                            null, Usage.empty(), StopReason.STOP, null, 2L),
                    new UserMessage("How?", 3L)
            );
            var result = AnthropicProvider.convertMessages(messages, false);

            assertEquals(3, result.size());
        }

        @Test
        void convertsEmptyMessageList() {
            var result = AnthropicProvider.convertMessages(List.of(), false);
            assertTrue(result.isEmpty());
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
            var result = AnthropicProvider.convertTools(List.of(tool));

            assertEquals(1, result.size());
        }

        @Test
        void convertsMultipleTools() {
            ObjectNode params = MAPPER.createObjectNode();
            var tools = List.of(
                    new Tool("read", "Read file", params),
                    new Tool("write", "Write file", params)
            );
            var result = AnthropicProvider.convertTools(tools);

            assertEquals(2, result.size());
        }

        @Test
        void convertsEmptyToolList() {
            var result = AnthropicProvider.convertTools(List.of());
            assertTrue(result.isEmpty());
        }
    }

    // -------------------------------------------------------------------
    // Stop reason mapping
    // -------------------------------------------------------------------

    @Nested
    class StopReasonMapping {

        @Test
        void mapsEndTurnToStop() {
            assertEquals(StopReason.STOP,
                    AnthropicProvider.mapStopReason(com.anthropic.models.messages.StopReason.END_TURN));
        }

        @Test
        void mapsMaxTokensToLength() {
            assertEquals(StopReason.LENGTH,
                    AnthropicProvider.mapStopReason(com.anthropic.models.messages.StopReason.MAX_TOKENS));
        }

        @Test
        void mapsToolUse() {
            assertEquals(StopReason.TOOL_USE,
                    AnthropicProvider.mapStopReason(com.anthropic.models.messages.StopReason.TOOL_USE));
        }
    }

    // -------------------------------------------------------------------
    // Cost computation
    // -------------------------------------------------------------------

    @Nested
    class CostComputation {

        @Test
        void computesCostFromUsage() {
            var modelCost = new ModelCost(3.0, 15.0, 0.3, 3.75);
            // usage: [input=1000, output=500, cacheRead=200, cacheWrite=100]
            long[] usage = {1000, 500, 200, 100};

            Cost cost = AnthropicProvider.computeCost(modelCost, usage);

            assertEquals(3.0 * 1000 / 1_000_000.0, cost.input(), 0.0001);
            assertEquals(15.0 * 500 / 1_000_000.0, cost.output(), 0.0001);
            assertEquals(0.3 * 200 / 1_000_000.0, cost.cacheRead(), 0.0001);
            assertEquals(3.75 * 100 / 1_000_000.0, cost.cacheWrite(), 0.0001);
        }

        @Test
        void totalIsSumOfComponents() {
            var modelCost = new ModelCost(3.0, 15.0, 0.3, 3.75);
            long[] usage = {1000, 500, 200, 100};

            Cost cost = AnthropicProvider.computeCost(modelCost, usage);

            assertEquals(cost.input() + cost.output() + cost.cacheRead() + cost.cacheWrite(),
                    cost.total(), 0.0001);
        }

        @Test
        void zeroCostForZeroUsage() {
            var modelCost = new ModelCost(3.0, 15.0, 0.3, 3.75);
            long[] usage = {0, 0, 0, 0};

            Cost cost = AnthropicProvider.computeCost(modelCost, usage);

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

            MessageCreateParams params = provider.buildParams(
                    testModel(), context, null, null, null, null);

            assertEquals(16000, params.maxTokens());
        }

        @Test
        void usesProvidedMaxTokens() {
            var context = new Context(null,
                    List.of(new UserMessage("Hello", 1L)), null);

            MessageCreateParams params = provider.buildParams(
                    testModel(), context, 4096, null, null, null);

            assertEquals(4096, params.maxTokens());
        }

        @Test
        void setsSystemPrompt() {
            var context = new Context("Be helpful.",
                    List.of(new UserMessage("Hello", 1L)), null);

            MessageCreateParams params = provider.buildParams(
                    testModel(), context, null, null, null, null);

            assertTrue(params.system().isPresent());
        }

        @Test
        void omitsSystemPromptWhenNull() {
            var context = new Context(null,
                    List.of(new UserMessage("Hello", 1L)), null);

            MessageCreateParams params = provider.buildParams(
                    testModel(), context, null, null, null, null);

            assertTrue(params.system().isEmpty());
        }

        @Test
        void setsTemperature() {
            var context = new Context(null,
                    List.of(new UserMessage("Hello", 1L)), null);

            MessageCreateParams params = provider.buildParams(
                    testModel(), context, null, 0.7, null, null);

            assertTrue(params.temperature().isPresent());
            assertEquals(0.7, params.temperature().get(), 0.001);
        }

        @Test
        void enablesThinkingForNonOffLevel() {
            var context = new Context(null,
                    List.of(new UserMessage("Hello", 1L)), null);

            MessageCreateParams params = provider.buildParams(
                    testModel(), context, null, null, ThinkingLevel.MEDIUM, null);

            assertTrue(params.thinking().isPresent());
        }

        @Test
        void noThinkingForOffLevel() {
            var context = new Context(null,
                    List.of(new UserMessage("Hello", 1L)), null);

            MessageCreateParams params = provider.buildParams(
                    testModel(), context, null, null, ThinkingLevel.OFF, null);

            assertTrue(params.thinking().isEmpty());
        }

        @Test
        void noThinkingWhenNull() {
            var context = new Context(null,
                    List.of(new UserMessage("Hello", 1L)), null);

            MessageCreateParams params = provider.buildParams(
                    testModel(), context, null, null, null, null);

            assertTrue(params.thinking().isEmpty());
        }

        @Test
        void setsToolsWhenPresent() {
            ObjectNode toolParams = MAPPER.createObjectNode();
            var tools = List.of(new Tool("bash", "Run commands", toolParams));
            var context = new Context(null,
                    List.of(new UserMessage("Hello", 1L)), tools);

            MessageCreateParams params = provider.buildParams(
                    testModel(), context, null, null, null, null);

            assertTrue(params.tools().isPresent());
            assertEquals(1, params.tools().get().size());
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
            var eventStream = new com.huawei.hicampus.mate.matecampusclaw.ai.stream.AssistantMessageEventStream();

            // executeStream with null apiKey and no env var
            provider.executeStream(testModel(), context,
                    null, null, null, null, null, eventStream);

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
