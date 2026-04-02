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

class MessageTest {

    private ObjectMapper mapper;

    @BeforeEach
    void setUp() {
        mapper = new ObjectMapper();
    }

    // --- StopReason ---

    @Nested
    class StopReasonTests {

        @Test
        void jsonValues() {
            assertEquals("stop", StopReason.STOP.value());
            assertEquals("length", StopReason.LENGTH.value());
            assertEquals("toolUse", StopReason.TOOL_USE.value());
            assertEquals("error", StopReason.ERROR.value());
            assertEquals("aborted", StopReason.ABORTED.value());
        }

        @Test
        void fromValue() {
            assertEquals(StopReason.STOP, StopReason.fromValue("stop"));
            assertEquals(StopReason.TOOL_USE, StopReason.fromValue("toolUse"));
        }

        @Test
        void fromValueUnknownThrows() {
            assertThrows(IllegalArgumentException.class, () -> StopReason.fromValue("unknown"));
        }

        @Test
        void serializesToJsonString() throws JsonProcessingException {
            assertEquals("\"stop\"", mapper.writeValueAsString(StopReason.STOP));
            assertEquals("\"toolUse\"", mapper.writeValueAsString(StopReason.TOOL_USE));
        }

        @Test
        void deserializesFromJsonString() throws JsonProcessingException {
            assertEquals(StopReason.LENGTH, mapper.readValue("\"length\"", StopReason.class));
            assertEquals(StopReason.ABORTED, mapper.readValue("\"aborted\"", StopReason.class));
        }
    }

    // --- UserMessage ---

    @Nested
    class UserMessageTests {

        @Test
        void creationWithContentBlocks() {
            var content = List.<ContentBlock>of(new TextContent("hello"), new ImageContent("data", "image/png"));
            var msg = new UserMessage(content, 1000L);
            assertEquals(2, msg.content().size());
            assertEquals(1000L, msg.timestamp());
        }

        @Test
        void convenienceStringConstructor() {
            var msg = new UserMessage("hello world", 2000L);
            assertEquals(1, msg.content().size());
            assertInstanceOf(TextContent.class, msg.content().get(0));
            assertEquals("hello world", ((TextContent) msg.content().get(0)).text());
            assertNull(((TextContent) msg.content().get(0)).textSignature());
            assertEquals(2000L, msg.timestamp());
        }

        @Test
        void serialization() throws JsonProcessingException {
            var msg = new UserMessage("hi", 3000L);
            var json = mapper.readTree(mapper.writeValueAsString(msg));
            assertEquals("user", json.get("role").asText());
            assertEquals(3000L, json.get("timestamp").asLong());
            assertTrue(json.get("content").isArray());
            assertEquals("text", json.get("content").get(0).get("type").asText());
            assertEquals("hi", json.get("content").get(0).get("text").asText());
        }

        @Test
        void deserialization() throws JsonProcessingException {
            var json = """
                {
                  "role": "user",
                  "content": [{"type": "text", "text": "hello"}],
                  "timestamp": 4000
                }""";
            var msg = mapper.readValue(json, Message.class);
            assertInstanceOf(UserMessage.class, msg);
            var user = (UserMessage) msg;
            assertEquals(1, user.content().size());
            assertEquals("hello", ((TextContent) user.content().get(0)).text());
            assertEquals(4000L, user.timestamp());
        }
    }

    // --- AssistantMessage ---

    @Nested
    class AssistantMessageTests {

        private AssistantMessage createSample() {
            return new AssistantMessage(
                List.of(new TextContent("Sure, here's the answer.")),
                "messages", "anthropic", "claude-opus-4-6", "resp-123",
                new Usage(100, 50, 0, 0, 150, new Cost(0.01, 0.03, 0.0, 0.0, 0.04)),
                StopReason.STOP, null, 5000L
            );
        }

        @Test
        void creation() {
            var msg = createSample();
            assertEquals("messages", msg.api());
            assertEquals("anthropic", msg.provider());
            assertEquals("claude-opus-4-6", msg.model());
            assertEquals("resp-123", msg.responseId());
            assertEquals(StopReason.STOP, msg.stopReason());
            assertNull(msg.errorMessage());
            assertEquals(5000L, msg.timestamp());
            assertEquals(150, msg.usage().totalTokens());
        }

        @Test
        void creationWithNullOptionals() {
            var msg = new AssistantMessage(
                List.of(new TextContent("error")),
                "messages", "anthropic", "claude-opus-4-6", null,
                Usage.empty(), StopReason.ERROR, "rate limit exceeded", 6000L
            );
            assertNull(msg.responseId());
            assertEquals("rate limit exceeded", msg.errorMessage());
            assertEquals(StopReason.ERROR, msg.stopReason());
        }

        @Test
        void serialization() throws JsonProcessingException {
            var msg = createSample();
            var json = mapper.readTree(mapper.writeValueAsString(msg));
            assertEquals("assistant", json.get("role").asText());
            assertEquals("messages", json.get("api").asText());
            assertEquals("anthropic", json.get("provider").asText());
            assertEquals("claude-opus-4-6", json.get("model").asText());
            assertEquals("resp-123", json.get("responseId").asText());
            assertEquals("stop", json.get("stopReason").asText());
            assertEquals(5000L, json.get("timestamp").asLong());
            assertTrue(json.get("content").isArray());
            assertTrue(json.has("usage"));
            assertEquals(150, json.get("usage").get("totalTokens").asInt());
        }

        @Test
        void deserialization() throws JsonProcessingException {
            var json = """
                {
                  "role": "assistant",
                  "content": [{"type": "text", "text": "hello"}],
                  "api": "chat",
                  "provider": "openai",
                  "model": "gpt-4o",
                  "usage": {"input":10,"output":5,"cacheRead":0,"cacheWrite":0,"totalTokens":15,
                            "cost":{"input":0.001,"output":0.002,"cacheRead":0.0,"cacheWrite":0.0,"total":0.003}},
                  "stopReason": "length",
                  "timestamp": 7000
                }""";
            var msg = mapper.readValue(json, Message.class);
            assertInstanceOf(AssistantMessage.class, msg);
            var asst = (AssistantMessage) msg;
            assertEquals("chat", asst.api());
            assertEquals("openai", asst.provider());
            assertEquals("gpt-4o", asst.model());
            assertNull(asst.responseId());
            assertNull(asst.errorMessage());
            assertEquals(StopReason.LENGTH, asst.stopReason());
            assertEquals(15, asst.usage().totalTokens());
            assertEquals(7000L, asst.timestamp());
        }

        @Test
        void toolUseStopReason() throws JsonProcessingException {
            var msg = new AssistantMessage(
                List.of(new ToolCall("tc-1", "search", Map.of("q", "java"), null)),
                "messages", "anthropic", "claude-opus-4-6", null,
                Usage.empty(), StopReason.TOOL_USE, null, 8000L
            );
            var json = mapper.readTree(mapper.writeValueAsString(msg));
            assertEquals("toolUse", json.get("stopReason").asText());
        }
    }

    // --- ToolResultMessage ---

    @Nested
    class ToolResultMessageTests {

        @Test
        void creation() {
            var msg = new ToolResultMessage(
                "tc-1", "search",
                List.of(new TextContent("result data")),
                Map.of("status", 200), false, 9000L
            );
            assertEquals("tc-1", msg.toolCallId());
            assertEquals("search", msg.toolName());
            assertEquals(1, msg.content().size());
            assertNotNull(msg.details());
            assertFalse(msg.isError());
            assertEquals(9000L, msg.timestamp());
        }

        @Test
        void creationWithNullDetails() {
            var msg = new ToolResultMessage(
                "tc-2", "read_file",
                List.of(new TextContent("file contents")),
                null, false, 10000L
            );
            assertNull(msg.details());
        }

        @Test
        void errorResult() {
            var msg = new ToolResultMessage(
                "tc-3", "write_file",
                List.of(new TextContent("Permission denied")),
                null, true, 11000L
            );
            assertTrue(msg.isError());
        }

        @Test
        void serialization() throws JsonProcessingException {
            var msg = new ToolResultMessage(
                "tc-1", "search",
                List.of(new TextContent("found it")),
                null, false, 12000L
            );
            var json = mapper.readTree(mapper.writeValueAsString(msg));
            assertEquals("toolResult", json.get("role").asText());
            assertEquals("tc-1", json.get("toolCallId").asText());
            assertEquals("search", json.get("toolName").asText());
            assertFalse(json.get("isError").asBoolean());
            assertEquals(12000L, json.get("timestamp").asLong());
            assertTrue(json.get("content").isArray());
        }

        @Test
        void deserialization() throws JsonProcessingException {
            var json = """
                {
                  "role": "toolResult",
                  "toolCallId": "tc-5",
                  "toolName": "bash",
                  "content": [{"type": "text", "text": "output"}],
                  "isError": true,
                  "timestamp": 13000
                }""";
            var msg = mapper.readValue(json, Message.class);
            assertInstanceOf(ToolResultMessage.class, msg);
            var tool = (ToolResultMessage) msg;
            assertEquals("tc-5", tool.toolCallId());
            assertEquals("bash", tool.toolName());
            assertTrue(tool.isError());
            assertNull(tool.details());
        }
    }

    // --- Polymorphic list ---

    @Test
    void polymorphicConversationRoundTrip() throws JsonProcessingException {
        List<Message> conversation = List.of(
            new UserMessage("What is Java?", 1000L),
            new AssistantMessage(
                List.of(new TextContent("Java is a programming language.")),
                "messages", "anthropic", "claude-opus-4-6", null,
                Usage.empty(), StopReason.STOP, null, 2000L
            ),
            new UserMessage("Tell me more", 3000L)
        );

        var json = mapper.writerFor(new TypeReference<List<Message>>() {}).writeValueAsString(conversation);
        List<Message> deserialized = mapper.readValue(json, new TypeReference<>() {});

        assertEquals(3, deserialized.size());
        assertInstanceOf(UserMessage.class, deserialized.get(0));
        assertInstanceOf(AssistantMessage.class, deserialized.get(1));
        assertInstanceOf(UserMessage.class, deserialized.get(2));
        assertEquals("What is Java?",
            ((TextContent) ((UserMessage) deserialized.get(0)).content().get(0)).text());
    }

    // --- Sealed exhaustiveness ---

    @Test
    void sealedPatternMatching() {
        List<Message> messages = List.of(
            new UserMessage("hi", 1L),
            new AssistantMessage(List.of(new TextContent("hello")),
                "messages", "anthropic", "model", null, Usage.empty(),
                StopReason.STOP, null, 2L),
            new ToolResultMessage("tc", "tool", List.of(new TextContent("ok")),
                null, false, 3L)
        );

        for (var msg : messages) {
            var role = switch (msg) {
                case UserMessage u -> "user";
                case AssistantMessage a -> "assistant";
                case ToolResultMessage t -> "toolResult";
            };
            assertNotNull(role);
        }
    }
}
