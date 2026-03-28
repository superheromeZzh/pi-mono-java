package com.mariozechner.pi.agent.loop;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mariozechner.pi.agent.context.ContextTransformer;
import com.mariozechner.pi.agent.context.DefaultMessageConverter;
import com.mariozechner.pi.agent.event.AgentEndEvent;
import com.mariozechner.pi.agent.event.AgentEvent;
import com.mariozechner.pi.agent.event.AgentStartEvent;
import com.mariozechner.pi.agent.event.MessageEndEvent;
import com.mariozechner.pi.agent.event.ToolExecutionStartEvent;
import com.mariozechner.pi.agent.event.TurnEndEvent;
import com.mariozechner.pi.agent.queue.MessageQueue;
import com.mariozechner.pi.agent.state.AgentState;
import com.mariozechner.pi.agent.tool.AgentContext;
import com.mariozechner.pi.agent.tool.AgentTool;
import com.mariozechner.pi.agent.tool.AgentToolResult;
import com.mariozechner.pi.agent.tool.AgentToolUpdateCallback;
import com.mariozechner.pi.agent.tool.CancellationToken;
import com.mariozechner.pi.agent.tool.ToolExecutionMode;
import com.mariozechner.pi.agent.tool.ToolExecutionPipeline;
import com.mariozechner.pi.ai.PiAiService;
import com.mariozechner.pi.ai.model.ModelRegistry;
import com.mariozechner.pi.ai.provider.ApiProvider;
import com.mariozechner.pi.ai.provider.ApiProviderRegistry;
import com.mariozechner.pi.ai.stream.AssistantMessageEvent;
import com.mariozechner.pi.ai.stream.AssistantMessageEventStream;
import com.mariozechner.pi.ai.types.Api;
import com.mariozechner.pi.ai.types.AssistantMessage;
import com.mariozechner.pi.ai.types.ContentBlock;
import com.mariozechner.pi.ai.types.Context;
import com.mariozechner.pi.ai.types.InputModality;
import com.mariozechner.pi.ai.types.Message;
import com.mariozechner.pi.ai.types.Model;
import com.mariozechner.pi.ai.types.ModelCost;
import com.mariozechner.pi.ai.types.Provider;
import com.mariozechner.pi.ai.types.SimpleStreamOptions;
import com.mariozechner.pi.ai.types.StopReason;
import com.mariozechner.pi.ai.types.TextContent;
import com.mariozechner.pi.ai.types.ToolCall;
import com.mariozechner.pi.ai.types.ToolResultMessage;
import com.mariozechner.pi.ai.types.Usage;
import com.mariozechner.pi.ai.types.UserMessage;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class AgentLoopTest {

    @Test
    void runCompletesPromptToolAndSteeringCycle() {
        var model = sampleModel();
        var steeringQueue = new MessageQueue();
        var followUpQueue = new MessageQueue();
        var toolPipeline = new ToolExecutionPipeline();
        var transformCalls = new AtomicInteger();
        ContextTransformer transformer = (messages, signal) -> {
            transformCalls.incrementAndGet();
            return CompletableFuture.completedFuture(messages);
        };
        var tool = new SteeringTool(steeringQueue);

        var state = new AgentState();
        state.setSystemPrompt("system");
        state.setTools(List.of(tool));
        var context = new AgentContext(state);

        var loop = new AgentLoop(new AgentLoopConfig(
            piAiService(model, new ScenarioProvider()),
            model,
            new DefaultMessageConverter(),
            transformer,
            toolPipeline,
            ToolExecutionMode.SEQUENTIAL,
            steeringQueue,
            followUpQueue,
            SimpleStreamOptions.empty()
        ));

        var events = new ArrayList<AgentEvent>();
        var result = loop.run(
            List.of(new UserMessage("prompt", 1L)),
            context,
            events::add,
            new CancellationToken()
        );

        assertEquals(5, result.size());
        assertEquals("prompt", text((UserMessage) result.get(0)));
        assertInstanceOf(AssistantMessage.class, result.get(1));
        assertInstanceOf(ToolResultMessage.class, result.get(2));
        assertEquals("steer", text((UserMessage) result.get(3)));
        assertEquals("steered done", text((AssistantMessage) result.get(4)));
        assertEquals(2, transformCalls.get());

        assertInstanceOf(AgentStartEvent.class, events.getFirst());
        assertInstanceOf(AgentEndEvent.class, events.getLast());
        assertTrue(events.stream().anyMatch(ToolExecutionStartEvent.class::isInstance));
        assertEquals(2, events.stream().filter(TurnEndEvent.class::isInstance).count());
    }

    @Test
    void continueLoopCanProcessExistingContextAndFollowUpMessages() {
        var model = sampleModel();
        var steeringQueue = new MessageQueue();
        var followUpQueue = new MessageQueue();
        followUpQueue.enqueue(new UserMessage("follow-up", 2L));

        var state = new AgentState();
        state.setSystemPrompt("system");
        state.setMessages(List.of(new UserMessage("initial", 1L)));
        var context = new AgentContext(state);

        var loop = new AgentLoop(new AgentLoopConfig(
            piAiService(model, new ScenarioProvider()),
            model,
            new DefaultMessageConverter(),
            null,
            new ToolExecutionPipeline(),
            ToolExecutionMode.SEQUENTIAL,
            steeringQueue,
            followUpQueue,
            SimpleStreamOptions.empty()
        ));

        var events = new ArrayList<AgentEvent>();
        var result = loop.continueLoop(context, events::add, new CancellationToken());

        assertEquals(4, result.size());
        assertEquals("initial", text((UserMessage) result.get(0)));
        assertEquals("first reply", text((AssistantMessage) result.get(1)));
        assertEquals("follow-up", text((UserMessage) result.get(2)));
        assertEquals("follow-up reply", text((AssistantMessage) result.get(3)));

        assertInstanceOf(AgentStartEvent.class, events.getFirst());
        assertInstanceOf(AgentEndEvent.class, events.getLast());
        assertEquals(2, events.stream().filter(TurnEndEvent.class::isInstance).count());
        assertTrue(events.stream().anyMatch(MessageEndEvent.class::isInstance));
    }

    private PiAiService piAiService(Model model, ApiProvider provider) {
        var providerRegistry = new ApiProviderRegistry(List.of(provider));
        var modelRegistry = new ModelRegistry();
        modelRegistry.register(model);
        return new PiAiService(providerRegistry, modelRegistry);
    }

    private Model sampleModel() {
        return new Model(
            "test-model",
            "Test Model",
            Api.ANTHROPIC_MESSAGES,
            Provider.ANTHROPIC,
            "https://example.com",
            true,
            List.of(InputModality.TEXT),
            new ModelCost(1.0, 2.0, 0.5, 0.25),
            200_000,
            4_096,
            null,
            null,
            null
        );
    }

    private String text(UserMessage message) {
        return ((TextContent) message.content().getFirst()).text();
    }

    private String text(AssistantMessage message) {
        return ((TextContent) message.content().getFirst()).text();
    }

    private static final class SteeringTool implements AgentTool {

        private final MessageQueue steeringQueue;
        private final ObjectMapper mapper = new ObjectMapper();

        private SteeringTool(MessageQueue steeringQueue) {
            this.steeringQueue = steeringQueue;
        }

        @Override
        public String name() {
            return "search";
        }

        @Override
        public String label() {
            return "search";
        }

        @Override
        public String description() {
            return "Search tool";
        }

        @Override
        public com.fasterxml.jackson.databind.JsonNode parameters() {
            return mapper.createObjectNode()
                .put("type", "object")
                .<com.fasterxml.jackson.databind.node.ObjectNode>set("properties",
                    mapper.createObjectNode().set("query", mapper.createObjectNode().put("type", "string")))
                .set("required", mapper.createArrayNode().add("query"));
        }

        @Override
        public AgentToolResult execute(
            String toolCallId,
            Map<String, Object> params,
            CancellationToken signal,
            AgentToolUpdateCallback onUpdate
        ) {
            steeringQueue.enqueue(new UserMessage("steer", 3L));
            onUpdate.onUpdate(new AgentToolResult(List.of(new TextContent("partial tool")), null));
            return new AgentToolResult(List.of(new TextContent("tool result")), Map.of("query", params.get("query")));
        }
    }

    private static final class ScenarioProvider implements ApiProvider {

        @Override
        public Api getApi() {
            return Api.ANTHROPIC_MESSAGES;
        }

        @Override
        public AssistantMessageEventStream stream(Model model, Context context, com.mariozechner.pi.ai.types.StreamOptions options) {
            throw new UnsupportedOperationException("AgentLoop uses streamSimple");
        }

        @Override
        public AssistantMessageEventStream streamSimple(Model model, Context context, SimpleStreamOptions options) {
            var lastMessage = context.messages().getLast();
            if (lastMessage instanceof UserMessage userMessage) {
                var text = ((TextContent) userMessage.content().getFirst()).text();
                return switch (text) {
                    case "prompt" -> toolCallStream(model, "search", Map.of("query", "java"));
                    case "steer" -> textStream(model, "steered done");
                    case "initial" -> textStream(model, "first reply");
                    case "follow-up" -> textStream(model, "follow-up reply");
                    default -> textStream(model, "unknown");
                };
            }
            if (lastMessage instanceof ToolResultMessage) {
                return textStream(model, "tool result reply");
            }
            throw new IllegalStateException("Unexpected last message: " + lastMessage.getClass().getSimpleName());
        }

        private AssistantMessageEventStream toolCallStream(Model model, String toolName, Map<String, Object> args) {
            var stream = new AssistantMessageEventStream();
            var toolCall = new ToolCall("tool-call-1", toolName, args);
            var partial = new AssistantMessage(
                List.of(toolCall),
                model.api().value(),
                model.provider().value(),
                model.id(),
                null,
                Usage.empty(),
                StopReason.TOOL_USE,
                null,
                10L
            );
            var done = new AssistantMessage(
                List.of(toolCall),
                model.api().value(),
                model.provider().value(),
                model.id(),
                null,
                Usage.empty(),
                StopReason.TOOL_USE,
                null,
                11L
            );
            stream.push(new AssistantMessageEvent.StartEvent(partial));
            stream.push(new AssistantMessageEvent.ToolCallEndEvent(0, toolCall, partial));
            stream.push(new AssistantMessageEvent.DoneEvent(StopReason.TOOL_USE, done));
            return stream;
        }

        private AssistantMessageEventStream textStream(Model model, String text) {
            var stream = new AssistantMessageEventStream();
            var partial = new AssistantMessage(
                List.of(new TextContent(text)),
                model.api().value(),
                model.provider().value(),
                model.id(),
                null,
                Usage.empty(),
                StopReason.STOP,
                null,
                20L
            );
            var done = new AssistantMessage(
                List.of(new TextContent(text)),
                model.api().value(),
                model.provider().value(),
                model.id(),
                null,
                Usage.empty(),
                StopReason.STOP,
                null,
                21L
            );
            stream.push(new AssistantMessageEvent.StartEvent(partial));
            stream.push(new AssistantMessageEvent.TextDeltaEvent(0, text, partial));
            stream.push(new AssistantMessageEvent.DoneEvent(StopReason.STOP, done));
            return stream;
        }
    }
}
