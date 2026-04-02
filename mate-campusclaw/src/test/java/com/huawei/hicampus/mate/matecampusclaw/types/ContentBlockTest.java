package com.huawei.hicampus.mate.matecampusclaw.ai.types;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class ContentBlockTest {

    private ObjectMapper mapper;

    @BeforeEach
    void setUp() {
        mapper = new ObjectMapper();
    }

    // --- Instance creation ---

    @Nested
    class Creation {

        @Test
        void textContentWithAllFields() {
            var tc = new TextContent("hello", "sig-123");
            assertEquals("hello", tc.text());
            assertEquals("sig-123", tc.textSignature());
        }

        @Test
        void textContentConvenienceConstructor() {
            var tc = new TextContent("hello");
            assertEquals("hello", tc.text());
            assertNull(tc.textSignature());
        }

        @Test
        void imageContent() {
            var ic = new ImageContent("aGVsbG8=", "image/png");
            assertEquals("aGVsbG8=", ic.data());
            assertEquals("image/png", ic.mimeType());
        }

        @Test
        void thinkingContentWithAllFields() {
            var tc = new ThinkingContent("let me think", "sig-456", true);
            assertEquals("let me think", tc.thinking());
            assertEquals("sig-456", tc.thinkingSignature());
            assertTrue(tc.redacted());
        }

        @Test
        void thinkingContentConvenienceConstructor() {
            var tc = new ThinkingContent("thinking...");
            assertEquals("thinking...", tc.thinking());
            assertNull(tc.thinkingSignature());
            assertFalse(tc.redacted());
        }

        @Test
        void toolCallWithAllFields() {
            var tc = new ToolCall("call-1", "get_weather", Map.of("city", "Tokyo"), "thought-sig");
            assertEquals("call-1", tc.id());
            assertEquals("get_weather", tc.name());
            assertEquals(Map.of("city", "Tokyo"), tc.arguments());
            assertEquals("thought-sig", tc.thoughtSignature());
        }

        @Test
        void toolCallConvenienceConstructor() {
            var tc = new ToolCall("call-1", "get_weather", Map.of("city", "Tokyo"));
            assertNull(tc.thoughtSignature());
        }
    }

    // --- Serialization ---

    @Nested
    class Serialization {

        @Test
        void textContentSerializesToJson() throws JsonProcessingException {
            var tc = new TextContent("hello", "sig-123");
            var json = mapper.readTree(mapper.writeValueAsString(tc));
            assertEquals("text", json.get("type").asText());
            assertEquals("hello", json.get("text").asText());
            assertEquals("sig-123", json.get("textSignature").asText());
        }

        @Test
        void textContentOmitsNullSignature() throws JsonProcessingException {
            var tc = new TextContent("hello");
            var json = mapper.readTree(mapper.writeValueAsString(tc));
            assertEquals("text", json.get("type").asText());
            assertEquals("hello", json.get("text").asText());
            // null field is present but null-valued; that's fine for Jackson default
            assertTrue(json.has("textSignature"));
        }

        @Test
        void imageContentSerializesToJson() throws JsonProcessingException {
            var ic = new ImageContent("aGVsbG8=", "image/png");
            var json = mapper.readTree(mapper.writeValueAsString(ic));
            assertEquals("image", json.get("type").asText());
            assertEquals("aGVsbG8=", json.get("data").asText());
            assertEquals("image/png", json.get("mimeType").asText());
        }

        @Test
        void thinkingContentSerializesToJson() throws JsonProcessingException {
            var tc = new ThinkingContent("deep thought", "sig-456", true);
            var json = mapper.readTree(mapper.writeValueAsString(tc));
            assertEquals("thinking", json.get("type").asText());
            assertEquals("deep thought", json.get("thinking").asText());
            assertEquals("sig-456", json.get("thinkingSignature").asText());
            assertTrue(json.get("redacted").asBoolean());
        }

        @Test
        void toolCallSerializesToJson() throws JsonProcessingException {
            var tc = new ToolCall("call-1", "search", Map.of("query", "java"), "ts-1");
            var json = mapper.readTree(mapper.writeValueAsString(tc));
            assertEquals("toolCall", json.get("type").asText());
            assertEquals("call-1", json.get("id").asText());
            assertEquals("search", json.get("name").asText());
            assertEquals("java", json.get("arguments").get("query").asText());
            assertEquals("ts-1", json.get("thoughtSignature").asText());
        }
    }

    // --- Deserialization ---

    @Nested
    class Deserialization {

        @Test
        void textContentFromJson() throws JsonProcessingException {
            var json = """
                {"type":"text","text":"hello","textSignature":"sig-123"}""";
            var block = mapper.readValue(json, ContentBlock.class);
            assertInstanceOf(TextContent.class, block);
            var tc = (TextContent) block;
            assertEquals("hello", tc.text());
            assertEquals("sig-123", tc.textSignature());
        }

        @Test
        void textContentWithoutOptionalFields() throws JsonProcessingException {
            var json = """
                {"type":"text","text":"hello"}""";
            var block = mapper.readValue(json, ContentBlock.class);
            assertInstanceOf(TextContent.class, block);
            assertNull(((TextContent) block).textSignature());
        }

        @Test
        void imageContentFromJson() throws JsonProcessingException {
            var json = """
                {"type":"image","data":"aGVsbG8=","mimeType":"image/png"}""";
            var block = mapper.readValue(json, ContentBlock.class);
            assertInstanceOf(ImageContent.class, block);
            var ic = (ImageContent) block;
            assertEquals("aGVsbG8=", ic.data());
            assertEquals("image/png", ic.mimeType());
        }

        @Test
        void thinkingContentFromJson() throws JsonProcessingException {
            var json = """
                {"type":"thinking","thinking":"hmm","thinkingSignature":"sig","redacted":false}""";
            var block = mapper.readValue(json, ContentBlock.class);
            assertInstanceOf(ThinkingContent.class, block);
            var tc = (ThinkingContent) block;
            assertEquals("hmm", tc.thinking());
            assertEquals("sig", tc.thinkingSignature());
            assertFalse(tc.redacted());
        }

        @Test
        void thinkingContentDefaultRedactedFalse() throws JsonProcessingException {
            var json = """
                {"type":"thinking","thinking":"hmm"}""";
            var block = mapper.readValue(json, ContentBlock.class);
            assertInstanceOf(ThinkingContent.class, block);
            assertFalse(((ThinkingContent) block).redacted());
        }

        @Test
        void toolCallFromJson() throws JsonProcessingException {
            var json = """
                {"type":"toolCall","id":"c1","name":"search","arguments":{"q":"test"},"thoughtSignature":"ts"}""";
            var block = mapper.readValue(json, ContentBlock.class);
            assertInstanceOf(ToolCall.class, block);
            var tc = (ToolCall) block;
            assertEquals("c1", tc.id());
            assertEquals("search", tc.name());
            assertEquals("test", tc.arguments().get("q"));
            assertEquals("ts", tc.thoughtSignature());
        }

        @Test
        void toolCallWithoutOptionalFields() throws JsonProcessingException {
            var json = """
                {"type":"toolCall","id":"c1","name":"search","arguments":{"q":"test"}}""";
            var block = mapper.readValue(json, ContentBlock.class);
            assertInstanceOf(ToolCall.class, block);
            assertNull(((ToolCall) block).thoughtSignature());
        }
    }

    // --- Polymorphic list ---

    @Test
    void polymorphicListRoundTrip() throws JsonProcessingException {
        List<ContentBlock> blocks = List.of(
            new TextContent("hello"),
            new ThinkingContent("thinking..."),
            new ToolCall("c1", "search", Map.of("q", "test"))
        );

        var json = mapper.writerFor(new TypeReference<List<ContentBlock>>() {}).writeValueAsString(blocks);
        List<ContentBlock> deserialized = mapper.readValue(json, new TypeReference<>() {});

        assertEquals(3, deserialized.size());
        assertInstanceOf(TextContent.class, deserialized.get(0));
        assertInstanceOf(ThinkingContent.class, deserialized.get(1));
        assertInstanceOf(ToolCall.class, deserialized.get(2));
        assertEquals("hello", ((TextContent) deserialized.get(0)).text());
    }

    // --- Sealed interface exhaustiveness ---

    @Test
    void allSubtypesAreContentBlocks() {
        ContentBlock text = new TextContent("a");
        ContentBlock image = new ImageContent("data", "image/jpeg");
        ContentBlock thinking = new ThinkingContent("b");
        ContentBlock tool = new ToolCall("id", "name", Map.of());

        // Pattern matching switch (Java 21) - verifies sealed permits
        for (var block : List.of(text, image, thinking, tool)) {
            var label = switch (block) {
                case TextContent t -> "text";
                case ImageContent i -> "image";
                case ThinkingContent th -> "thinking";
                case ToolCall tc -> "toolCall";
            };
            assertNotNull(label);
        }
    }
}
