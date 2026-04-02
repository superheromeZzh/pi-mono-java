package com.huawei.hicampus.mate.matecampusclaw.ai.stream;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.Map;

import com.huawei.hicampus.mate.matecampusclaw.ai.stream.AssistantMessageEvent.*;
import com.huawei.hicampus.mate.matecampusclaw.ai.types.*;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class AssistantMessageEventTest {

    private ObjectMapper mapper;

    @BeforeEach
    void setUp() {
        mapper = new ObjectMapper();
    }

    /** Creates a minimal AssistantMessage for use in event tests. */
    private AssistantMessage sampleMessage(StopReason reason) {
        return new AssistantMessage(
            List.of(new TextContent("hello")),
            "anthropic-messages",
            "anthropic",
            "claude-opus-4-6",
            null,
            Usage.empty(),
            reason,
            null,
            System.currentTimeMillis()
        );
    }

    private AssistantMessage samplePartial() {
        return sampleMessage(StopReason.STOP);
    }

    // --- Creation ---

    @Nested
    class Creation {

        @Test
        void startEvent() {
            var partial = samplePartial();
            var event = new StartEvent(partial);
            assertSame(partial, event.partial());
        }

        @Test
        void textStartEvent() {
            var partial = samplePartial();
            var event = new TextStartEvent(0, partial);
            assertEquals(0, event.contentIndex());
            assertSame(partial, event.partial());
        }

        @Test
        void textDeltaEvent() {
            var partial = samplePartial();
            var event = new TextDeltaEvent(1, "hello ", partial);
            assertEquals(1, event.contentIndex());
            assertEquals("hello ", event.delta());
            assertSame(partial, event.partial());
        }

        @Test
        void textEndEvent() {
            var partial = samplePartial();
            var event = new TextEndEvent(0, "full text", partial);
            assertEquals(0, event.contentIndex());
            assertEquals("full text", event.content());
            assertSame(partial, event.partial());
        }

        @Test
        void thinkingStartEvent() {
            var partial = samplePartial();
            var event = new ThinkingStartEvent(0, partial);
            assertEquals(0, event.contentIndex());
        }

        @Test
        void thinkingDeltaEvent() {
            var event = new ThinkingDeltaEvent(0, "let me think", samplePartial());
            assertEquals("let me think", event.delta());
        }

        @Test
        void thinkingEndEvent() {
            var event = new ThinkingEndEvent(0, "full thinking", samplePartial());
            assertEquals("full thinking", event.content());
        }

        @Test
        void toolCallStartEvent() {
            var event = new ToolCallStartEvent(2, samplePartial());
            assertEquals(2, event.contentIndex());
        }

        @Test
        void toolCallDeltaEvent() {
            var event = new ToolCallDeltaEvent(2, "{\"query\":", samplePartial());
            assertEquals("{\"query\":", event.delta());
        }

        @Test
        void toolCallEndEvent() {
            var toolCall = new ToolCall("call-1", "search", Map.of("q", "test"));
            var event = new ToolCallEndEvent(2, toolCall, samplePartial());
            assertEquals(2, event.contentIndex());
            assertSame(toolCall, event.toolCall());
        }

        @Test
        void doneEvent() {
            var msg = sampleMessage(StopReason.STOP);
            var event = new DoneEvent(StopReason.STOP, msg);
            assertEquals(StopReason.STOP, event.reason());
            assertSame(msg, event.message());
        }

        @Test
        void doneEventWithToolUseReason() {
            var msg = sampleMessage(StopReason.TOOL_USE);
            var event = new DoneEvent(StopReason.TOOL_USE, msg);
            assertEquals(StopReason.TOOL_USE, event.reason());
        }

        @Test
        void errorEvent() {
            var msg = sampleMessage(StopReason.ERROR);
            var event = new ErrorEvent("error", msg);
            assertEquals("error", event.reason());
            assertSame(msg, event.error());
        }

        @Test
        void errorEventAborted() {
            var msg = sampleMessage(StopReason.ABORTED);
            var event = new ErrorEvent("aborted", msg);
            assertEquals("aborted", event.reason());
        }
    }

    // --- Serialization ---

    @Nested
    class Serialization {

        @Test
        void startEventSerializesToJson() throws JsonProcessingException {
            var event = new StartEvent(samplePartial());
            var json = mapper.readTree(mapper.writeValueAsString(event));
            assertEquals("start", json.get("type").asText());
            assertTrue(json.has("partial"));
            assertEquals("claude-opus-4-6", json.get("partial").get("model").asText());
        }

        @Test
        void textDeltaEventSerializesToJson() throws JsonProcessingException {
            var event = new TextDeltaEvent(1, "chunk", samplePartial());
            var json = mapper.readTree(mapper.writeValueAsString(event));
            assertEquals("text_delta", json.get("type").asText());
            assertEquals(1, json.get("contentIndex").asInt());
            assertEquals("chunk", json.get("delta").asText());
            assertTrue(json.has("partial"));
        }

        @Test
        void textEndEventSerializesToJson() throws JsonProcessingException {
            var event = new TextEndEvent(0, "full text", samplePartial());
            var json = mapper.readTree(mapper.writeValueAsString(event));
            assertEquals("text_end", json.get("type").asText());
            assertEquals("full text", json.get("content").asText());
        }

        @Test
        void thinkingDeltaEventSerializesToJson() throws JsonProcessingException {
            var event = new ThinkingDeltaEvent(0, "hmm", samplePartial());
            var json = mapper.readTree(mapper.writeValueAsString(event));
            assertEquals("thinking_delta", json.get("type").asText());
            assertEquals("hmm", json.get("delta").asText());
        }

        @Test
        void toolCallEndEventSerializesToJson() throws JsonProcessingException {
            var toolCall = new ToolCall("c1", "search", Map.of("q", "java"));
            var event = new ToolCallEndEvent(2, toolCall, samplePartial());
            var json = mapper.readTree(mapper.writeValueAsString(event));
            assertEquals("toolcall_end", json.get("type").asText());
            assertEquals(2, json.get("contentIndex").asInt());
            assertTrue(json.has("toolCall"));
            assertEquals("c1", json.get("toolCall").get("id").asText());
            assertEquals("search", json.get("toolCall").get("name").asText());
        }

        @Test
        void doneEventSerializesToJson() throws JsonProcessingException {
            var event = new DoneEvent(StopReason.LENGTH, sampleMessage(StopReason.LENGTH));
            var json = mapper.readTree(mapper.writeValueAsString(event));
            assertEquals("done", json.get("type").asText());
            assertEquals("length", json.get("reason").asText());
            assertTrue(json.has("message"));
        }

        @Test
        void errorEventSerializesToJson() throws JsonProcessingException {
            var event = new ErrorEvent("aborted", sampleMessage(StopReason.ABORTED));
            var json = mapper.readTree(mapper.writeValueAsString(event));
            assertEquals("error", json.get("type").asText());
            assertEquals("aborted", json.get("reason").asText());
            assertTrue(json.has("error"));
        }
    }

    // --- Deserialization ---

    @Nested
    class Deserialization {

        private String partialJson() {
            return """
                {
                  "role": "assistant",
                  "content": [{"type":"text","text":"hi","textSignature":null}],
                  "api": "anthropic-messages",
                  "provider": "anthropic",
                  "model": "claude-opus-4-6",
                  "responseId": null,
                  "usage": {"input":0,"output":0,"cacheRead":0,"cacheWrite":0,"totalTokens":0,
                            "cost":{"input":0,"output":0,"cacheRead":0,"cacheWrite":0,"total":0}},
                  "stopReason": "stop",
                  "errorMessage": null,
                  "timestamp": 1700000000000
                }""";
        }

        @Test
        void startEventFromJson() throws JsonProcessingException {
            var json = """
                {"type":"start","partial":%s}""".formatted(partialJson());
            var event = mapper.readValue(json, AssistantMessageEvent.class);
            assertInstanceOf(StartEvent.class, event);
            assertEquals("claude-opus-4-6", ((StartEvent) event).partial().model());
        }

        @Test
        void textDeltaEventFromJson() throws JsonProcessingException {
            var json = """
                {"type":"text_delta","contentIndex":1,"delta":"chunk","partial":%s}"""
                .formatted(partialJson());
            var event = mapper.readValue(json, AssistantMessageEvent.class);
            assertInstanceOf(TextDeltaEvent.class, event);
            var delta = (TextDeltaEvent) event;
            assertEquals(1, delta.contentIndex());
            assertEquals("chunk", delta.delta());
        }

        @Test
        void thinkingEndEventFromJson() throws JsonProcessingException {
            var json = """
                {"type":"thinking_end","contentIndex":0,"content":"full thought","partial":%s}"""
                .formatted(partialJson());
            var event = mapper.readValue(json, AssistantMessageEvent.class);
            assertInstanceOf(ThinkingEndEvent.class, event);
            assertEquals("full thought", ((ThinkingEndEvent) event).content());
        }

        @Test
        void toolCallStartEventFromJson() throws JsonProcessingException {
            var json = """
                {"type":"toolcall_start","contentIndex":3,"partial":%s}"""
                .formatted(partialJson());
            var event = mapper.readValue(json, AssistantMessageEvent.class);
            assertInstanceOf(ToolCallStartEvent.class, event);
            assertEquals(3, ((ToolCallStartEvent) event).contentIndex());
        }

        @Test
        void toolCallEndEventFromJson() throws JsonProcessingException {
            var json = """
                {"type":"toolcall_end","contentIndex":2,
                 "toolCall":{"type":"toolCall","id":"c1","name":"search","arguments":{"q":"test"},"thoughtSignature":null},
                 "partial":%s}""".formatted(partialJson());
            var event = mapper.readValue(json, AssistantMessageEvent.class);
            assertInstanceOf(ToolCallEndEvent.class, event);
            var tce = (ToolCallEndEvent) event;
            assertEquals("c1", tce.toolCall().id());
            assertEquals("search", tce.toolCall().name());
        }

        @Test
        void doneEventFromJson() throws JsonProcessingException {
            var json = """
                {"type":"done","reason":"toolUse","message":%s}"""
                .formatted(partialJson());
            var event = mapper.readValue(json, AssistantMessageEvent.class);
            assertInstanceOf(DoneEvent.class, event);
            assertEquals(StopReason.TOOL_USE, ((DoneEvent) event).reason());
        }

        @Test
        void errorEventFromJson() throws JsonProcessingException {
            var json = """
                {"type":"error","reason":"error","error":%s}"""
                .formatted(partialJson());
            var event = mapper.readValue(json, AssistantMessageEvent.class);
            assertInstanceOf(ErrorEvent.class, event);
            assertEquals("error", ((ErrorEvent) event).reason());
        }
    }

    // --- Round-trip ---

    @Nested
    class RoundTrip {

        @Test
        void startEventRoundTrip() throws JsonProcessingException {
            var original = new StartEvent(samplePartial());
            var json = mapper.writeValueAsString(original);
            var restored = mapper.readValue(json, AssistantMessageEvent.class);
            assertInstanceOf(StartEvent.class, restored);
            assertEquals(original.partial().model(), ((StartEvent) restored).partial().model());
        }

        @Test
        void doneEventRoundTrip() throws JsonProcessingException {
            var original = new DoneEvent(StopReason.STOP, sampleMessage(StopReason.STOP));
            var json = mapper.writeValueAsString(original);
            var restored = mapper.readValue(json, AssistantMessageEvent.class);
            assertInstanceOf(DoneEvent.class, restored);
            assertEquals(StopReason.STOP, ((DoneEvent) restored).reason());
        }

        @Test
        void errorEventRoundTrip() throws JsonProcessingException {
            var original = new ErrorEvent("aborted", sampleMessage(StopReason.ABORTED));
            var json = mapper.writeValueAsString(original);
            var restored = mapper.readValue(json, AssistantMessageEvent.class);
            assertInstanceOf(ErrorEvent.class, restored);
            assertEquals("aborted", ((ErrorEvent) restored).reason());
        }
    }

    // --- Sealed interface exhaustiveness ---

    @Test
    void patternMatchingCoversAllVariants() {
        var partial = samplePartial();
        var toolCall = new ToolCall("c1", "search", Map.of());
        List<AssistantMessageEvent> events = List.of(
            new StartEvent(partial),
            new TextStartEvent(0, partial),
            new TextDeltaEvent(0, "d", partial),
            new TextEndEvent(0, "text", partial),
            new ThinkingStartEvent(0, partial),
            new ThinkingDeltaEvent(0, "d", partial),
            new ThinkingEndEvent(0, "thought", partial),
            new ToolCallStartEvent(1, partial),
            new ToolCallDeltaEvent(1, "{}", partial),
            new ToolCallEndEvent(1, toolCall, partial),
            new DoneEvent(StopReason.STOP, partial),
            new ErrorEvent("error", partial)
        );

        for (var event : events) {
            var label = switch (event) {
                case StartEvent e -> "start";
                case TextStartEvent e -> "text_start";
                case TextDeltaEvent e -> "text_delta";
                case TextEndEvent e -> "text_end";
                case ThinkingStartEvent e -> "thinking_start";
                case ThinkingDeltaEvent e -> "thinking_delta";
                case ThinkingEndEvent e -> "thinking_end";
                case ToolCallStartEvent e -> "toolcall_start";
                case ToolCallDeltaEvent e -> "toolcall_delta";
                case ToolCallEndEvent e -> "toolcall_end";
                case DoneEvent e -> "done";
                case ErrorEvent e -> "error";
            };
            assertNotNull(label);
        }
    }
}
