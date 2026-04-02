package com.huawei.hicampus.mate.matecampusclaw.agent.event;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import com.huawei.hicampus.mate.matecampusclaw.ai.stream.AssistantMessageEvent;
import com.huawei.hicampus.mate.matecampusclaw.ai.types.AssistantMessage;
import com.huawei.hicampus.mate.matecampusclaw.ai.types.Message;
import com.huawei.hicampus.mate.matecampusclaw.ai.types.StopReason;
import com.huawei.hicampus.mate.matecampusclaw.ai.types.TextContent;
import com.huawei.hicampus.mate.matecampusclaw.ai.types.ToolResultMessage;
import com.huawei.hicampus.mate.matecampusclaw.ai.types.Usage;
import com.huawei.hicampus.mate.matecampusclaw.ai.types.UserMessage;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class AgentEventTest {

    private ObjectMapper mapper;

    @BeforeEach
    void setUp() {
        mapper = new ObjectMapper();
    }

    private UserMessage sampleUserMessage() {
        return new UserMessage("hello", 1000L);
    }

    private AssistantMessage sampleAssistantMessage(StopReason reason) {
        return new AssistantMessage(
            List.of(new TextContent("assistant")),
            "anthropic-messages",
            "anthropic",
            "claude-opus-4-6",
            null,
            Usage.empty(),
            reason,
            null,
            2000L
        );
    }

    private ToolResultMessage sampleToolResult(boolean isError) {
        return new ToolResultMessage(
            "call-1",
            "search",
            List.of(new TextContent(isError ? "failed" : "done")),
            Map.of("status", isError ? "error" : "ok"),
            isError,
            3000L
        );
    }

    private AssistantMessageEvent sampleAssistantMessageEvent() {
        return new AssistantMessageEvent.TextDeltaEvent(0, "delta", sampleAssistantMessage(StopReason.STOP));
    }

    @Nested
    class Creation {

        @Test
        void agentStartEventHasNoPayload() {
            var event = new AgentStartEvent();

            assertNotNull(event);
        }

        @Test
        void agentEndEventCarriesMessages() {
            List<Message> messages = List.of(sampleUserMessage(), sampleAssistantMessage(StopReason.STOP));
            var event = new AgentEndEvent(messages);

            assertEquals(2, event.messages().size());
            assertInstanceOf(UserMessage.class, event.messages().getFirst());
        }

        @Test
        void turnEndEventCarriesMessageAndToolResults() {
            var event = new TurnEndEvent(sampleAssistantMessage(StopReason.TOOL_USE), List.of(sampleToolResult(false)));

            assertInstanceOf(AssistantMessage.class, event.message());
            assertEquals(1, event.toolResults().size());
            assertEquals("search", event.toolResults().getFirst().toolName());
        }

        @Test
        void messageUpdateEventCarriesNestedAssistantEvent() {
            var event = new MessageUpdateEvent(sampleAssistantMessage(StopReason.STOP), sampleAssistantMessageEvent());

            assertInstanceOf(AssistantMessage.class, event.message());
            assertInstanceOf(AssistantMessageEvent.TextDeltaEvent.class, event.assistantMessageEvent());
        }

        @Test
        void toolExecutionEventsCarryArgsAndResults() {
            var start = new ToolExecutionStartEvent("call-1", "search", Map.of("q", "java"));
            var update = new ToolExecutionUpdateEvent("call-1", "search", Map.of("q", "java"), Map.of("progress", 50));
            var end = new ToolExecutionEndEvent("call-1", "search", Map.of("items", 3), false);

            assertEquals("call-1", start.toolCallId());
            assertEquals(Map.of("progress", 50), update.partialResult());
            assertFalse(end.isError());
        }

        @Test
        void listenerCanBeImplementedAsLambda() {
            var received = new AtomicReference<AgentEvent>();
            AgentEventListener listener = received::set;
            var event = new TurnStartEvent();

            listener.onEvent(event);

            assertSame(event, received.get());
        }
    }

    @Nested
    class Serialization {

        @Test
        void agentStartEventSerializesToJson() throws JsonProcessingException {
            var json = mapper.readTree(mapper.writeValueAsString(new AgentStartEvent()));

            assertEquals("agent_start", json.get("type").asText());
        }

        @Test
        void agentEndEventSerializesNestedMessages() throws JsonProcessingException {
            var event = new AgentEndEvent(List.of(sampleUserMessage(), sampleAssistantMessage(StopReason.STOP)));
            var json = mapper.readTree(mapper.writeValueAsString(event));

            assertEquals("agent_end", json.get("type").asText());
            assertTrue(json.get("messages").isArray());
            assertEquals("user", json.get("messages").get(0).get("role").asText());
            assertEquals("assistant", json.get("messages").get(1).get("role").asText());
        }

        @Test
        void messageUpdateEventSerializesNestedAssistantMessageEvent() throws JsonProcessingException {
            var event = new MessageUpdateEvent(sampleAssistantMessage(StopReason.STOP), sampleAssistantMessageEvent());
            var json = mapper.readTree(mapper.writeValueAsString(event));

            assertEquals("message_update", json.get("type").asText());
            assertEquals("assistant", json.get("message").get("role").asText());
            assertEquals("text_delta", json.get("assistantMessageEvent").get("type").asText());
        }

        @Test
        void toolExecutionEndEventSerializesToJson() throws JsonProcessingException {
            var event = new ToolExecutionEndEvent("call-1", "search", Map.of("items", 3), true);
            var json = mapper.readTree(mapper.writeValueAsString(event));

            assertEquals("tool_execution_end", json.get("type").asText());
            assertEquals("call-1", json.get("toolCallId").asText());
            assertEquals("search", json.get("toolName").asText());
            assertEquals(3, json.get("result").get("items").asInt());
            assertTrue(json.get("isError").asBoolean());
        }
    }

    @Nested
    class Deserialization {

        @Test
        void agentStartEventFromJson() throws JsonProcessingException {
            var event = mapper.readValue("{\"type\":\"agent_start\"}", AgentEvent.class);

            assertInstanceOf(AgentStartEvent.class, event);
        }

        @Test
        void turnEndEventFromJson() throws JsonProcessingException {
            var json = """
                {
                  "type": "turn_end",
                  "message": {
                    "role": "assistant",
                    "content": [{"type": "text", "text": "assistant", "textSignature": null}],
                    "api": "anthropic-messages",
                    "provider": "anthropic",
                    "model": "claude-opus-4-6",
                    "responseId": null,
                    "usage": {
                      "input": 0,
                      "output": 0,
                      "cacheRead": 0,
                      "cacheWrite": 0,
                      "totalTokens": 0,
                      "cost": {
                        "input": 0.0,
                        "output": 0.0,
                        "cacheRead": 0.0,
                        "cacheWrite": 0.0,
                        "total": 0.0
                      }
                    },
                    "stopReason": "toolUse",
                    "errorMessage": null,
                    "timestamp": 2000
                  },
                  "toolResults": [
                    {
                      "role": "toolResult",
                      "toolCallId": "call-1",
                      "toolName": "search",
                      "content": [{"type": "text", "text": "done", "textSignature": null}],
                      "details": {"status": "ok"},
                      "isError": false,
                      "timestamp": 3000
                    }
                  ]
                }""";

            var event = mapper.readValue(json, AgentEvent.class);

            assertInstanceOf(TurnEndEvent.class, event);
            var turnEnd = (TurnEndEvent) event;
            assertInstanceOf(AssistantMessage.class, turnEnd.message());
            assertEquals(1, turnEnd.toolResults().size());
            assertEquals("search", turnEnd.toolResults().getFirst().toolName());
        }

        @Test
        void messageUpdateEventFromJson() throws JsonProcessingException {
            var json = """
                {
                  "type": "message_update",
                  "message": {
                    "role": "assistant",
                    "content": [{"type": "text", "text": "assistant", "textSignature": null}],
                    "api": "anthropic-messages",
                    "provider": "anthropic",
                    "model": "claude-opus-4-6",
                    "responseId": null,
                    "usage": {
                      "input": 0,
                      "output": 0,
                      "cacheRead": 0,
                      "cacheWrite": 0,
                      "totalTokens": 0,
                      "cost": {
                        "input": 0.0,
                        "output": 0.0,
                        "cacheRead": 0.0,
                        "cacheWrite": 0.0,
                        "total": 0.0
                      }
                    },
                    "stopReason": "stop",
                    "errorMessage": null,
                    "timestamp": 2000
                  },
                  "assistantMessageEvent": {
                    "type": "text_delta",
                    "contentIndex": 0,
                    "delta": "delta",
                    "partial": {
                      "role": "assistant",
                      "content": [{"type": "text", "text": "assistant", "textSignature": null}],
                      "api": "anthropic-messages",
                      "provider": "anthropic",
                      "model": "claude-opus-4-6",
                      "responseId": null,
                      "usage": {
                        "input": 0,
                        "output": 0,
                        "cacheRead": 0,
                        "cacheWrite": 0,
                        "totalTokens": 0,
                        "cost": {
                          "input": 0.0,
                          "output": 0.0,
                          "cacheRead": 0.0,
                          "cacheWrite": 0.0,
                          "total": 0.0
                        }
                      },
                      "stopReason": "stop",
                      "errorMessage": null,
                      "timestamp": 2000
                    }
                  }
                }""";

            var event = mapper.readValue(json, AgentEvent.class);

            assertInstanceOf(MessageUpdateEvent.class, event);
            var update = (MessageUpdateEvent) event;
            assertInstanceOf(AssistantMessage.class, update.message());
            assertInstanceOf(AssistantMessageEvent.TextDeltaEvent.class, update.assistantMessageEvent());
        }

        @Test
        void toolExecutionEndEventFromJson() throws JsonProcessingException {
            var json = """
                {
                  "type": "tool_execution_end",
                  "toolCallId": "call-1",
                  "toolName": "search",
                  "result": {"items": 3},
                  "isError": true
                }""";

            var event = mapper.readValue(json, AgentEvent.class);

            assertInstanceOf(ToolExecutionEndEvent.class, event);
            var end = (ToolExecutionEndEvent) event;
            assertEquals("call-1", end.toolCallId());
            assertInstanceOf(Map.class, end.result());
            assertEquals(3, ((Map<?, ?>) end.result()).get("items"));
            assertTrue(end.isError());
        }
    }

    @Nested
    class RoundTrip {

        @Test
        void polymorphicEventListRoundTrip() throws JsonProcessingException {
            List<AgentEvent> events = List.of(
                new AgentStartEvent(),
                new MessageStartEvent(sampleUserMessage()),
                new MessageUpdateEvent(sampleAssistantMessage(StopReason.STOP), sampleAssistantMessageEvent()),
                new ToolExecutionStartEvent("call-1", "search", Map.of("q", "java")),
                new ToolExecutionUpdateEvent("call-1", "search", Map.of("q", "java"), Map.of("progress", 50)),
                new ToolExecutionEndEvent("call-1", "search", Map.of("items", 3), false),
                new TurnEndEvent(sampleAssistantMessage(StopReason.STOP), List.of(sampleToolResult(false))),
                new AgentEndEvent(List.of(sampleUserMessage(), sampleAssistantMessage(StopReason.STOP)))
            );

            var json = mapper.writerFor(new TypeReference<List<AgentEvent>>() {}).writeValueAsString(events);
            List<AgentEvent> restored = mapper.readValue(json, new TypeReference<>() {});

            assertEquals(events.size(), restored.size());
            assertInstanceOf(AgentStartEvent.class, restored.get(0));
            assertInstanceOf(MessageStartEvent.class, restored.get(1));
            assertInstanceOf(MessageUpdateEvent.class, restored.get(2));
            assertInstanceOf(ToolExecutionStartEvent.class, restored.get(3));
            assertInstanceOf(ToolExecutionUpdateEvent.class, restored.get(4));
            assertInstanceOf(ToolExecutionEndEvent.class, restored.get(5));
            assertInstanceOf(TurnEndEvent.class, restored.get(6));
            assertInstanceOf(AgentEndEvent.class, restored.get(7));
        }
    }

    @Test
    void sealedPatternMatchingCoversAllVariants() {
        List<AgentEvent> events = List.of(
            new AgentStartEvent(),
            new AgentEndEvent(List.of(sampleUserMessage())),
            new TurnStartEvent(),
            new TurnEndEvent(sampleAssistantMessage(StopReason.STOP), List.of(sampleToolResult(false))),
            new MessageStartEvent(sampleUserMessage()),
            new MessageUpdateEvent(sampleAssistantMessage(StopReason.STOP), sampleAssistantMessageEvent()),
            new MessageEndEvent(sampleAssistantMessage(StopReason.STOP)),
            new ToolExecutionStartEvent("call-1", "search", Map.of("q", "java")),
            new ToolExecutionUpdateEvent("call-1", "search", Map.of("q", "java"), Map.of("progress", 50)),
            new ToolExecutionEndEvent("call-1", "search", Map.of("items", 3), false)
        );

        for (var event : events) {
            var type = switch (event) {
                case AgentStartEvent e -> "agent_start";
                case AgentEndEvent e -> "agent_end";
                case TurnStartEvent e -> "turn_start";
                case TurnEndEvent e -> "turn_end";
                case MessageStartEvent e -> "message_start";
                case MessageUpdateEvent e -> "message_update";
                case MessageEndEvent e -> "message_end";
                case ToolExecutionStartEvent e -> "tool_execution_start";
                case ToolExecutionUpdateEvent e -> "tool_execution_update";
                case ToolExecutionEndEvent e -> "tool_execution_end";
            };

            assertNotNull(type);
        }
    }
}
