/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.hicampus.mate.matecampusclaw.ai.provider.google;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;

import com.huawei.hicampus.mate.matecampusclaw.ai.types.AssistantMessage;
import com.huawei.hicampus.mate.matecampusclaw.ai.types.ContentBlock;
import com.huawei.hicampus.mate.matecampusclaw.ai.types.ImageContent;
import com.huawei.hicampus.mate.matecampusclaw.ai.types.StopReason;
import com.huawei.hicampus.mate.matecampusclaw.ai.types.TextContent;
import com.huawei.hicampus.mate.matecampusclaw.ai.types.ThinkingContent;
import com.huawei.hicampus.mate.matecampusclaw.ai.types.Tool;
import com.huawei.hicampus.mate.matecampusclaw.ai.types.ToolCall;
import com.huawei.hicampus.mate.matecampusclaw.ai.types.ToolResultMessage;
import com.huawei.hicampus.mate.matecampusclaw.ai.types.Usage;
import com.huawei.hicampus.mate.matecampusclaw.ai.types.UserMessage;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class GoogleSharedTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static AssistantMessage assistant(List<ContentBlock> content) {
        return new AssistantMessage(
                content, "google-generative-ai", "google", "gemini", null, Usage.empty(), StopReason.STOP, null, 0L);
    }

    @Nested
    class ConvertMessages {

        @Test
        void userMessageWithTextAndImage() {
            UserMessage um = new UserMessage(
                    List.of(new TextContent("hello", null), new ImageContent("base64data", "image/png")), 0L);
            ArrayNode result = GoogleShared.convertMessages(List.of(um));
            assertEquals(1, result.size());
            JsonNode content = result.get(0);
            assertEquals("user", content.get("role").asText());
            JsonNode parts = content.get("parts");
            assertEquals(2, parts.size());
            assertEquals("hello", parts.get(0).get("text").asText());
            assertEquals(
                    "image/png", parts.get(1).get("inlineData").get("mimeType").asText());
            assertEquals(
                    "base64data", parts.get(1).get("inlineData").get("data").asText());
        }

        @Test
        void assistantMessageWithMultipleBlockTypes() {
            ContentBlock text = new TextContent("answer", null);
            ContentBlock thinking = new ThinkingContent("ponder", "sig", false);
            ContentBlock call = new ToolCall("c1", "search", Map.of("q", "x"));
            AssistantMessage am = assistant(List.of(text, thinking, call));
            ArrayNode result = GoogleShared.convertMessages(List.of(am));
            JsonNode content = result.get(0);
            assertEquals("model", content.get("role").asText());
            JsonNode parts = content.get("parts");
            assertEquals(3, parts.size());
            assertEquals("answer", parts.get(0).get("text").asText());
            assertEquals("ponder", parts.get(1).get("text").asText());
            assertTrue(parts.get(1).get("thought").asBoolean());
            assertEquals("sig", parts.get(1).get("thoughtSignature").asText());
            assertEquals("search", parts.get(2).get("functionCall").get("name").asText());
            assertEquals(
                    "x", parts.get(2).get("functionCall").get("args").get("q").asText());
        }

        @Test
        void assistantThinkingWithoutSignatureOmitsSig() {
            ContentBlock thinking = new ThinkingContent("ponder", null, false);
            AssistantMessage am = assistant(List.of(thinking));
            ArrayNode result = GoogleShared.convertMessages(List.of(am));
            JsonNode part = result.get(0).get("parts").get(0);
            assertFalse(part.has("thoughtSignature"));
        }

        @Test
        void assistantBlankThinkingSkipped() {
            ContentBlock thinking = new ThinkingContent("   ", null, false);
            AssistantMessage am = assistant(List.of(thinking));
            ArrayNode result = GoogleShared.convertMessages(List.of(am));
            assertEquals(0, result.get(0).get("parts").size());
        }

        @Test
        void assistantImageSkipped() {
            ContentBlock img = new ImageContent("d", "image/png");
            AssistantMessage am = assistant(List.of(img));
            ArrayNode result = GoogleShared.convertMessages(List.of(am));
            assertEquals(0, result.get(0).get("parts").size());
        }

        @Test
        void toolResultErrorWrappedAsError() {
            ToolResultMessage trm =
                    new ToolResultMessage("c1", "search", List.of(new TextContent("boom", null)), null, true, 0L);
            ArrayNode result = GoogleShared.convertMessages(List.of(trm));
            JsonNode fr = result.get(0).get("parts").get(0).get("functionResponse");
            assertEquals("search", fr.get("name").asText());
            assertEquals("boom", fr.get("response").get("error").asText());
        }

        @Test
        void toolResultOkWrappedAsOutput() {
            ToolResultMessage trm = new ToolResultMessage(
                    "c1",
                    "search",
                    List.of(new TextContent("part1", null), new TextContent("part2", null)),
                    null,
                    false,
                    0L);
            ArrayNode result = GoogleShared.convertMessages(List.of(trm));
            JsonNode fr = result.get(0).get("parts").get(0).get("functionResponse");
            assertEquals("part1part2", fr.get("response").get("output").asText());
        }

        @Test
        void emptyMessageListProducesEmptyArray() {
            ArrayNode result = GoogleShared.convertMessages(List.of());
            assertEquals(0, result.size());
        }
    }

    @Nested
    class ConvertTools {

        @Test
        void nullReturnsNull() {
            assertNull(GoogleShared.convertTools(null));
        }

        @Test
        void emptyReturnsNull() {
            assertNull(GoogleShared.convertTools(List.of()));
        }

        @Test
        void withParameters() {
            JsonNode params = MAPPER.createObjectNode().put("type", "object");
            Tool tool = new Tool("search", "search the web", params);
            ArrayNode result = GoogleShared.convertTools(List.of(tool));
            JsonNode fd = result.get(0).get("functionDeclarations").get(0);
            assertEquals("search", fd.get("name").asText());
            assertEquals("search the web", fd.get("description").asText());
            assertEquals("object", fd.get("parameters").get("type").asText());
        }

        @Test
        void withoutParameters() {
            Tool tool = new Tool("ping", "pings", null);
            ArrayNode result = GoogleShared.convertTools(List.of(tool));
            JsonNode fd = result.get(0).get("functionDeclarations").get(0);
            assertEquals("ping", fd.get("name").asText());
            assertFalse(fd.has("parameters"));
        }
    }

    @Nested
    class ParseChunk {

        @Test
        void missingCandidatesReturnsEmpty() {
            JsonNode chunk = MAPPER.createObjectNode();
            GoogleShared.ParsedChunk pc = GoogleShared.parseChunk(chunk);
            assertTrue(pc.blocks().isEmpty());
            assertNull(pc.finishReason());
            assertNull(pc.usage());
        }

        @Test
        void parseTextAndFunctionCall() throws Exception {
            String json = "{\"candidates\":[{"
                    + "\"content\":{\"parts\":["
                    + "  {\"text\":\"answer\"},"
                    + "  {\"functionCall\":{\"name\":\"search\",\"args\":{\"q\":\"x\"}}}"
                    + "]},\"finishReason\":\"STOP\"}],"
                    + "\"usageMetadata\":{\"promptTokenCount\":10,\"candidatesTokenCount\":5,\"totalTokenCount\":15}}";
            JsonNode chunk = MAPPER.readTree(json);
            GoogleShared.ParsedChunk pc = GoogleShared.parseChunk(chunk);
            assertEquals(2, pc.blocks().size());
            assertEquals("answer", ((TextContent) pc.blocks().get(0)).text());
            ToolCall tc = (ToolCall) pc.blocks().get(1);
            assertEquals("search", tc.name());
            assertEquals("x", tc.arguments().get("q"));
            assertEquals("STOP", pc.finishReason());
            assertNotNull(pc.usage());
            assertEquals(10, pc.usage().input());
            assertEquals(5, pc.usage().output());
        }

        @Test
        void parseThinkingPartWithSignature() throws Exception {
            String json = "{\"candidates\":[{"
                    + "\"content\":{\"parts\":[{\"text\":\"hmm\",\"thought\":true,\"thoughtSignature\":\"s\"}]}}]}";
            JsonNode chunk = MAPPER.readTree(json);
            GoogleShared.ParsedChunk pc = GoogleShared.parseChunk(chunk);
            ThinkingContent tc = (ThinkingContent) pc.blocks().get(0);
            assertEquals("hmm", tc.thinking());
            assertEquals("s", tc.thinkingSignature());
        }

        @Test
        void parseFunctionCallWithoutArgs() throws Exception {
            String json = "{\"candidates\":[{\"content\":{\"parts\":[{\"functionCall\":{\"name\":\"noop\"}}]}}]}";
            JsonNode chunk = MAPPER.readTree(json);
            GoogleShared.ParsedChunk pc = GoogleShared.parseChunk(chunk);
            ToolCall tc = (ToolCall) pc.blocks().get(0);
            assertEquals("noop", tc.name());
            assertTrue(tc.arguments().isEmpty());
        }

        @Test
        void unknownPartShapeReturnsNull() throws Exception {
            String json = "{\"candidates\":[{\"content\":{\"parts\":[{\"unknown\":1}]}}]}";
            JsonNode chunk = MAPPER.readTree(json);
            GoogleShared.ParsedChunk pc = GoogleShared.parseChunk(chunk);
            assertTrue(pc.blocks().isEmpty());
        }

        @Test
        void usageWithThoughtsTokens() throws Exception {
            String json = "{\"candidates\":[{\"content\":{\"parts\":[]}}],"
                    + "\"usageMetadata\":{\"promptTokenCount\":20,\"cachedContentTokenCount\":3,"
                    + "\"candidatesTokenCount\":4,\"thoughtsTokenCount\":2}}";
            JsonNode chunk = MAPPER.readTree(json);
            GoogleShared.ParsedChunk pc = GoogleShared.parseChunk(chunk);
            Usage u = pc.usage();

            // input = prompt - cached = 17, output = 4 + 2 = 6, total = default 23
            assertEquals(17, u.input());
            assertEquals(6, u.output());
            assertEquals(3, u.cacheRead());
            assertEquals(23, u.totalTokens());
        }
    }

    @Nested
    class MapFinishReason {

        @Test
        void nullDefaultsToStop() {
            assertEquals(StopReason.STOP, GoogleShared.mapFinishReason(null));
        }

        @Test
        void stopMapped() {
            assertEquals(StopReason.STOP, GoogleShared.mapFinishReason("STOP"));
        }

        @Test
        void maxTokensMapsToLength() {
            assertEquals(StopReason.LENGTH, GoogleShared.mapFinishReason("MAX_TOKENS"));
        }

        @Test
        void safetyRecitationOtherMapToError() {
            assertEquals(StopReason.ERROR, GoogleShared.mapFinishReason("SAFETY"));
            assertEquals(StopReason.ERROR, GoogleShared.mapFinishReason("RECITATION"));
            assertEquals(StopReason.ERROR, GoogleShared.mapFinishReason("OTHER"));
        }

        @Test
        void unknownDefaultsToStop() {
            assertEquals(StopReason.STOP, GoogleShared.mapFinishReason("MYSTERY"));
        }
    }
}
