package com.huawei.hicampus.mate.matecampusclaw.ai.provider.bedrock;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.Map;

import com.huawei.hicampus.mate.matecampusclaw.ai.types.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import software.amazon.awssdk.core.document.Document;
import software.amazon.awssdk.services.bedrockruntime.model.TokenUsage;

class BedrockProviderTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    BedrockProvider provider = new BedrockProvider();

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

    // -------------------------------------------------------------------
    // API identification
    // -------------------------------------------------------------------

    @Nested
    class ApiIdentification {

        @Test
        void returnsBedrockConverseStreamApi() {
            assertEquals(Api.BEDROCK_CONVERSE_STREAM, provider.getApi());
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
            var result = BedrockProvider.convertMessages(messages);

            assertEquals(1, result.size());
            assertEquals("user", result.get(0).roleAsString());
            assertTrue(result.get(0).hasContent());
        }

        @Test
        void convertsAssistantMessageWithText() {
            var am = new AssistantMessage(
                    List.of(new TextContent("Hi there", null)),
                    "bedrock-converse-stream", "amazon-bedrock",
                    "anthropic.claude-sonnet-4-20250514-v1:0",
                    null, Usage.empty(), StopReason.STOP, null, 1L);
            var result = BedrockProvider.convertMessages(List.of(am));

            assertEquals(1, result.size());
            assertEquals("assistant", result.get(0).roleAsString());
        }

        @Test
        void convertsAssistantMessageWithToolCalls() {
            var am = new AssistantMessage(
                    List.of(
                            new TextContent("Let me check", null),
                            new ToolCall("toolu_123", "bash",
                                    Map.of("command", "ls"), null)
                    ),
                    "bedrock-converse-stream", "amazon-bedrock",
                    "anthropic.claude-sonnet-4-20250514-v1:0",
                    null, Usage.empty(), StopReason.TOOL_USE, null, 1L);
            var result = BedrockProvider.convertMessages(List.of(am));

            assertEquals(1, result.size());
            // Should have 2 content blocks in single assistant message
            assertTrue(result.get(0).hasContent());
            assertEquals(2, result.get(0).content().size());
        }

        @Test
        void convertsToolResultMessage() {
            var tr = new ToolResultMessage("toolu_123", "bash",
                    List.of(new TextContent("file1.txt\nfile2.txt")), null, false, 1L);
            var result = BedrockProvider.convertMessages(List.of(tr));

            assertEquals(1, result.size());
            // Tool results sent as user role in Bedrock
            assertEquals("user", result.get(0).roleAsString());
        }

        @Test
        void convertsEmptyMessageList() {
            var result = BedrockProvider.convertMessages(List.of());
            assertTrue(result.isEmpty());
        }

        @Test
        void convertsMultipleMessages() {
            List<Message> messages = List.of(
                    new UserMessage("Hello", 1L),
                    new AssistantMessage(
                            List.of(new TextContent("Hi")),
                            "bedrock-converse-stream", "amazon-bedrock",
                            "anthropic.claude-sonnet-4-20250514-v1:0",
                            null, Usage.empty(), StopReason.STOP, null, 2L),
                    new UserMessage("How?", 3L)
            );
            var result = BedrockProvider.convertMessages(messages);

            assertEquals(3, result.size());
        }

        @Test
        void convertsThinkingToTextFallbackWhenSignatureMissing() {
            // Anthropic Claude with missing signature: thinking falls back to plain text
            var am = new AssistantMessage(
                    List.of(
                            new ThinkingContent("thinking...", null, false),
                            new TextContent("Answer", null)
                    ),
                    "bedrock-converse-stream", "amazon-bedrock",
                    "anthropic.claude-sonnet-4-20250514-v1:0",
                    null, Usage.empty(), StopReason.STOP, null, 1L);
            var result = BedrockProvider.convertMessages(List.of(am));

            assertEquals(1, result.size());
            // Thinking (as text fallback) + text = 2 blocks
            assertEquals(2, result.get(0).content().size());
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
            var result = BedrockProvider.convertTools(List.of(tool));

            assertEquals(1, result.size());
            assertNotNull(result.get(0).toolSpec());
            assertEquals("read", result.get(0).toolSpec().name());
            assertEquals("Read a file", result.get(0).toolSpec().description());
        }

        @Test
        void convertsMultipleTools() {
            ObjectNode params = MAPPER.createObjectNode();
            var tools = List.of(
                    new com.huawei.hicampus.mate.matecampusclaw.ai.types.Tool("read", "Read file", params),
                    new com.huawei.hicampus.mate.matecampusclaw.ai.types.Tool("write", "Write file", params)
            );
            var result = BedrockProvider.convertTools(tools);

            assertEquals(2, result.size());
        }

        @Test
        void convertsEmptyToolList() {
            var result = BedrockProvider.convertTools(List.of());
            assertTrue(result.isEmpty());
        }

        @Test
        void handlesNullParameters() {
            var tool = new com.huawei.hicampus.mate.matecampusclaw.ai.types.Tool("bash", "Run command", null);
            var result = BedrockProvider.convertTools(List.of(tool));

            assertEquals(1, result.size());
            assertNotNull(result.get(0).toolSpec().inputSchema());
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
                    BedrockProvider.mapStopReason("end_turn"));
        }

        @Test
        void mapsStopSequenceToStop() {
            assertEquals(StopReason.STOP,
                    BedrockProvider.mapStopReason("stop_sequence"));
        }

        @Test
        void mapsToolUse() {
            assertEquals(StopReason.TOOL_USE,
                    BedrockProvider.mapStopReason("tool_use"));
        }

        @Test
        void mapsMaxTokensToLength() {
            assertEquals(StopReason.LENGTH,
                    BedrockProvider.mapStopReason("max_tokens"));
        }

        @Test
        void mapsModelContextWindowExceededToLength() {
            assertEquals(StopReason.LENGTH,
                    BedrockProvider.mapStopReason("model_context_window_exceeded"));
        }

        @Test
        void mapsContentFilteredToError() {
            assertEquals(StopReason.ERROR,
                    BedrockProvider.mapStopReason("content_filtered"));
        }

        @Test
        void mapsGuardrailIntervenedToError() {
            assertEquals(StopReason.ERROR,
                    BedrockProvider.mapStopReason("guardrail_intervened"));
        }

        @Test
        void mapsMalformedModelOutputToError() {
            assertEquals(StopReason.ERROR,
                    BedrockProvider.mapStopReason("malformed_model_output"));
        }

        @Test
        void mapsMalformedToolUseToError() {
            assertEquals(StopReason.ERROR,
                    BedrockProvider.mapStopReason("malformed_tool_use"));
        }

        @Test
        void mapsNullToStop() {
            assertEquals(StopReason.STOP,
                    BedrockProvider.mapStopReason(null));
        }

        @Test
        void mapsUnknownToStop() {
            assertEquals(StopReason.STOP,
                    BedrockProvider.mapStopReason("unknown_reason"));
        }
    }

    // -------------------------------------------------------------------
    // Usage parsing
    // -------------------------------------------------------------------

    @Nested
    class UsageParsing {

        @Test
        void parsesBasicUsage() {
            var usage = TokenUsage.builder()
                    .inputTokens(1000)
                    .outputTokens(500)
                    .totalTokens(1500)
                    .build();
            long[] accumulated = {0, 0, 0, 0};

            BedrockProvider.parseUsage(usage, accumulated);

            assertEquals(1000, accumulated[0]); // input
            assertEquals(500, accumulated[1]);   // output
            assertEquals(0, accumulated[2]);     // cacheRead
            assertEquals(0, accumulated[3]);     // cacheWrite
        }

        @Test
        void parsesUsageWithCache() {
            var usage = TokenUsage.builder()
                    .inputTokens(1000)
                    .outputTokens(500)
                    .totalTokens(1500)
                    .cacheReadInputTokens(200)
                    .cacheWriteInputTokens(50)
                    .build();
            long[] accumulated = {0, 0, 0, 0};

            BedrockProvider.parseUsage(usage, accumulated);

            assertEquals(800, accumulated[0]);  // input - cacheRead
            assertEquals(500, accumulated[1]);  // output
            assertEquals(200, accumulated[2]);  // cacheRead
            assertEquals(50, accumulated[3]);   // cacheWrite
        }

        @Test
        void handlesNullCacheTokens() {
            var usage = TokenUsage.builder()
                    .inputTokens(500)
                    .outputTokens(100)
                    .totalTokens(600)
                    .build();
            long[] accumulated = {0, 0, 0, 0};

            BedrockProvider.parseUsage(usage, accumulated);

            assertEquals(500, accumulated[0]);
            assertEquals(100, accumulated[1]);
            assertEquals(0, accumulated[2]);
            assertEquals(0, accumulated[3]);
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
            long[] usage = {1000, 500, 200, 100};

            Cost cost = BedrockProvider.computeCost(modelCost, usage);

            assertEquals(3.0 * 1000 / 1_000_000.0, cost.input(), 0.0001);
            assertEquals(15.0 * 500 / 1_000_000.0, cost.output(), 0.0001);
            assertEquals(0.3 * 200 / 1_000_000.0, cost.cacheRead(), 0.0001);
            assertEquals(3.75 * 100 / 1_000_000.0, cost.cacheWrite(), 0.0001);
        }

        @Test
        void totalIsSumOfComponents() {
            var modelCost = new ModelCost(3.0, 15.0, 0.3, 3.75);
            long[] usage = {1000, 500, 200, 100};

            Cost cost = BedrockProvider.computeCost(modelCost, usage);

            assertEquals(cost.input() + cost.output() + cost.cacheRead() + cost.cacheWrite(),
                    cost.total(), 0.0001);
        }

        @Test
        void zeroCostForZeroUsage() {
            var modelCost = new ModelCost(3.0, 15.0, 0.3, 3.75);
            long[] usage = {0, 0, 0, 0};

            Cost cost = BedrockProvider.computeCost(modelCost, usage);

            assertEquals(0.0, cost.total(), 0.0001);
        }
    }

    // -------------------------------------------------------------------
    // Document conversion
    // -------------------------------------------------------------------

    @Nested
    class DocumentConversion {

        @Test
        void convertsStringToDocument() {
            Document doc = BedrockProvider.objectToDocument("hello");
            assertEquals("hello", doc.asString());
        }

        @Test
        void convertsIntegerToDocument() {
            Document doc = BedrockProvider.objectToDocument(42);
            assertEquals(42, doc.asNumber().intValue());
        }

        @Test
        void convertsBooleanToDocument() {
            Document doc = BedrockProvider.objectToDocument(true);
            assertTrue(doc.asBoolean());
        }

        @Test
        void convertsNullToDocument() {
            Document doc = BedrockProvider.objectToDocument(null);
            assertNotNull(doc);
        }

        @Test
        void convertsMapToDocument() {
            Map<String, Object> map = Map.of("key", "value", "num", 42);
            Document doc = BedrockProvider.mapToDocument(map);
            var resultMap = doc.asMap();
            assertEquals("value", resultMap.get("key").asString());
            assertEquals(42, resultMap.get("num").asNumber().intValue());
        }

        @Test
        void convertsNestedMapToDocument() {
            Map<String, Object> inner = Map.of("nested", "value");
            Map<String, Object> outer = Map.of("inner", inner);
            Document doc = BedrockProvider.mapToDocument(outer);
            var outerMap = doc.asMap();
            var innerMap = outerMap.get("inner").asMap();
            assertEquals("value", innerMap.get("nested").asString());
        }

        @Test
        void convertsEmptyMapToDocument() {
            Document doc = BedrockProvider.mapToDocument(Map.of());
            assertTrue(doc.asMap().isEmpty());
        }

        @Test
        void convertsNullMapToDocument() {
            Document doc = BedrockProvider.mapToDocument(null);
            assertTrue(doc.asMap().isEmpty());
        }

        @Test
        void convertsJsonNodeToDocument() {
            ObjectNode node = MAPPER.createObjectNode();
            node.put("type", "object");
            ObjectNode props = MAPPER.createObjectNode();
            props.set("name", MAPPER.createObjectNode().put("type", "string"));
            node.set("properties", props);

            Document doc = BedrockProvider.jsonNodeToDocument(node);
            var map = doc.asMap();
            assertEquals("object", map.get("type").asString());
        }
    }

    // -------------------------------------------------------------------
    // Tool argument parsing
    // -------------------------------------------------------------------

    @Nested
    class ToolArgumentParsing {

        @Test
        void parsesValidJson() {
            var result = BedrockProvider.parseToolArguments("{\"path\":\"/tmp/foo\"}");
            assertEquals("/tmp/foo", result.get("path"));
        }

        @Test
        void returnsEmptyMapForEmptyString() {
            var result = BedrockProvider.parseToolArguments("");
            assertTrue(result.isEmpty());
        }

        @Test
        void returnsEmptyMapForNull() {
            var result = BedrockProvider.parseToolArguments(null);
            assertTrue(result.isEmpty());
        }

        @Test
        void returnsEmptyMapForInvalidJson() {
            var result = BedrockProvider.parseToolArguments("{invalid");
            assertTrue(result.isEmpty());
        }
    }

    // -------------------------------------------------------------------
    // Parameter building
    // -------------------------------------------------------------------

    @Nested
    class ParameterBuilding {

        @Test
        void buildsBasicRequest() {
            var context = new Context(null,
                    List.of(new UserMessage("Hello", 1L)), null);

            var request = provider.buildRequest(
                    testModel(), context, null, null, null, null);

            assertNotNull(request);
            assertEquals("anthropic.claude-sonnet-4-20250514-v1:0", request.modelId());
        }

        @Test
        void usesProvidedMaxTokens() {
            var context = new Context(null,
                    List.of(new UserMessage("Hello", 1L)), null);

            var request = provider.buildRequest(
                    testModel(), context, 4096, null, null, null);

            assertNotNull(request);
            assertEquals(4096, request.inferenceConfig().maxTokens());
        }

        @Test
        void setsTemperature() {
            var context = new Context(null,
                    List.of(new UserMessage("Hello", 1L)), null);

            var request = provider.buildRequest(
                    testModel(), context, null, 0.7, null, null);

            assertNotNull(request);
            assertEquals(0.7f, request.inferenceConfig().temperature(), 0.01);
        }

        @Test
        void setsSystemPrompt() {
            var context = new Context("Be helpful.",
                    List.of(new UserMessage("Hello", 1L)), null);

            var request = provider.buildRequest(
                    testModel(), context, null, null, null, null);

            assertNotNull(request);
            assertFalse(request.system().isEmpty());
        }

        @Test
        void setsToolsWhenPresent() {
            ObjectNode toolParams = MAPPER.createObjectNode();
            var tools = List.of(new com.huawei.hicampus.mate.matecampusclaw.ai.types.Tool("bash", "Run commands", toolParams));
            var context = new Context(null,
                    List.of(new UserMessage("Hello", 1L)), tools);

            var request = provider.buildRequest(
                    testModel(), context, null, null, null, null);

            assertNotNull(request);
            assertNotNull(request.toolConfig());
            assertFalse(request.toolConfig().tools().isEmpty());
        }

        @Test
        void omitsSystemPromptWhenNull() {
            var context = new Context(null,
                    List.of(new UserMessage("Hello", 1L)), null);

            var request = provider.buildRequest(
                    testModel(), context, null, null, null, null);

            assertTrue(request.system().isEmpty());
        }

        @Test
        void omitsToolConfigWhenNoTools() {
            var context = new Context(null,
                    List.of(new UserMessage("Hello", 1L)), null);

            var request = provider.buildRequest(
                    testModel(), context, null, null, null, null);

            assertNull(request.toolConfig());
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
}
