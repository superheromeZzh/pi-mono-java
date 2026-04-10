package com.huawei.hicampus.mate.matecampusclaw.agent;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import com.huawei.hicampus.mate.matecampusclaw.agent.context.DefaultMessageConverter;
import com.huawei.hicampus.mate.matecampusclaw.agent.event.AgentEndEvent;
import com.huawei.hicampus.mate.matecampusclaw.agent.event.AgentEvent;
import com.huawei.hicampus.mate.matecampusclaw.agent.event.AgentStartEvent;
import com.huawei.hicampus.mate.matecampusclaw.agent.event.ToolExecutionStartEvent;
import com.huawei.hicampus.mate.matecampusclaw.agent.event.TurnEndEvent;
import com.huawei.hicampus.mate.matecampusclaw.agent.queue.MessageQueue;
import com.huawei.hicampus.mate.matecampusclaw.agent.state.AgentState;
import com.huawei.hicampus.mate.matecampusclaw.agent.tool.AgentTool;
import com.huawei.hicampus.mate.matecampusclaw.agent.tool.AgentToolResult;
import com.huawei.hicampus.mate.matecampusclaw.agent.tool.AgentToolUpdateCallback;
import com.huawei.hicampus.mate.matecampusclaw.agent.tool.CancellationToken;
import com.huawei.hicampus.mate.matecampusclaw.agent.tool.ToolExecutionMode;
import com.huawei.hicampus.mate.matecampusclaw.agent.tool.ToolExecutionPipeline;
import com.huawei.hicampus.mate.matecampusclaw.ai.CampusClawAiService;
import com.huawei.hicampus.mate.matecampusclaw.ai.model.ModelRegistry;
import com.huawei.hicampus.mate.matecampusclaw.ai.provider.ApiProvider;
import com.huawei.hicampus.mate.matecampusclaw.ai.provider.ApiProviderRegistry;
import com.huawei.hicampus.mate.matecampusclaw.ai.stream.AssistantMessageEvent;
import com.huawei.hicampus.mate.matecampusclaw.ai.stream.AssistantMessageEventStream;
import com.huawei.hicampus.mate.matecampusclaw.ai.types.Api;
import com.huawei.hicampus.mate.matecampusclaw.ai.types.AssistantMessage;
import com.huawei.hicampus.mate.matecampusclaw.ai.types.Context;
import com.huawei.hicampus.mate.matecampusclaw.ai.types.InputModality;
import com.huawei.hicampus.mate.matecampusclaw.ai.types.Model;
import com.huawei.hicampus.mate.matecampusclaw.ai.types.ModelCost;
import com.huawei.hicampus.mate.matecampusclaw.ai.types.Provider;
import com.huawei.hicampus.mate.matecampusclaw.ai.types.SimpleStreamOptions;
import com.huawei.hicampus.mate.matecampusclaw.ai.types.StopReason;
import com.huawei.hicampus.mate.matecampusclaw.ai.types.TextContent;
import com.huawei.hicampus.mate.matecampusclaw.ai.types.ToolCall;
import com.huawei.hicampus.mate.matecampusclaw.ai.types.ToolResultMessage;
import com.huawei.hicampus.mate.matecampusclaw.ai.types.Usage;
import com.huawei.hicampus.mate.matecampusclaw.ai.types.UserMessage;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.junit.jupiter.api.Test;

class AgentTest {

    @Test
    void promptEmitsEventsAndCompletes() throws Exception {
        var agent = new Agent(
            piAiService(sampleModel(), new TextResponseProvider("hello", "world")),
            new AgentState(),
            new DefaultMessageConverter(),
            null,
            new ToolExecutionPipeline(),
            ToolExecutionMode.SEQUENTIAL,
            new MessageQueue(),
            new MessageQueue(),
            SimpleStreamOptions.empty()
        );
        agent.setModel(sampleModel());

        var events = new ArrayList<AgentEvent>();
        var unsubscribe = agent.subscribe(events::add);

        agent.prompt("hello").get(2, TimeUnit.SECONDS);
        agent.waitForIdle().get(2, TimeUnit.SECONDS);
        unsubscribe.run();

        assertEquals(2, agent.getState().getMessages().size());
        assertEquals("hello", text((UserMessage) agent.getState().getMessages().get(0)));
        assertEquals("world", text((AssistantMessage) agent.getState().getMessages().get(1)));
        assertFalse(agent.getState().isStreaming());
        assertNull(agent.getState().getStreamMessage());
        assertNull(agent.getState().getError());

        assertInstanceOf(AgentStartEvent.class, events.getFirst());
        assertInstanceOf(AgentEndEvent.class, events.getLast());
        assertTrue(events.stream().anyMatch(TurnEndEvent.class::isInstance));
    }

    @Test
    void abortCancelsRunningToolExecution() throws Exception {
        var tool = new BlockingAbortTool();
        var agent = new Agent(
            piAiService(sampleModel(), new ToolCallProvider("abort", "blocking_tool", Map.of("query", "abort"))),
            new AgentState(),
            new DefaultMessageConverter(),
            null,
            new ToolExecutionPipeline(),
            ToolExecutionMode.SEQUENTIAL,
            new MessageQueue(),
            new MessageQueue(),
            SimpleStreamOptions.empty()
        );
        agent.setModel(sampleModel());
        agent.setTools(List.of(tool));

        var future = agent.prompt("abort");
        assertTrue(tool.entered.await(2, TimeUnit.SECONDS));

        agent.abort();

        future.get(2, TimeUnit.SECONDS);
        agent.waitForIdle().get(2, TimeUnit.SECONDS);

        assertTrue(tool.cancelled.get());
        assertTrue(agent.getState().getPendingToolCalls().isEmpty());
        assertFalse(agent.getState().isStreaming());
        assertEquals(3, agent.getState().getMessages().size());
        assertInstanceOf(ToolResultMessage.class, agent.getState().getMessages().getLast());
    }

    @Test
    void steeringMessagesAreInjectedIntoNextTurn() throws Exception {
        var steeringQueue = new MessageQueue();
        var agent = new Agent(
            piAiService(sampleModel(), new SteeringProvider()),
            new AgentState(),
            new DefaultMessageConverter(),
            null,
            new ToolExecutionPipeline(),
            ToolExecutionMode.SEQUENTIAL,
            steeringQueue,
            new MessageQueue(),
            SimpleStreamOptions.empty()
        );
        agent.setModel(sampleModel());
        agent.setTools(List.of(new SteeringTool(agent)));

        var events = new ArrayList<AgentEvent>();
        agent.subscribe(events::add);

        agent.prompt("steering").get(2, TimeUnit.SECONDS);
        agent.waitForIdle().get(2, TimeUnit.SECONDS);

        assertEquals(5, agent.getState().getMessages().size());
        assertEquals("steering", text((UserMessage) agent.getState().getMessages().get(0)));
        assertInstanceOf(AssistantMessage.class, agent.getState().getMessages().get(1));
        assertInstanceOf(ToolResultMessage.class, agent.getState().getMessages().get(2));
        assertEquals("steer", text((UserMessage) agent.getState().getMessages().get(3)));
        assertEquals("done after steering", text((AssistantMessage) agent.getState().getMessages().get(4)));
        assertTrue(events.stream().anyMatch(ToolExecutionStartEvent.class::isInstance));
        assertEquals(2, events.stream().filter(TurnEndEvent.class::isInstance).count());
    }

    @Test
    void subscribeReturnsUnsubscribeRunnable() throws Exception {
        var agent = new Agent(
            piAiService(sampleModel(), new TextResponseProvider("hello", "world")),
            new AgentState(),
            new DefaultMessageConverter(),
            null,
            new ToolExecutionPipeline(),
            ToolExecutionMode.SEQUENTIAL,
            new MessageQueue(),
            new MessageQueue(),
            SimpleStreamOptions.empty()
        );
        agent.setModel(sampleModel());

        var events = new ArrayList<AgentEvent>();
        var unsubscribe = agent.subscribe(events::add);
        unsubscribe.run();

        agent.prompt("hello").get(2, TimeUnit.SECONDS);
        agent.waitForIdle().get(2, TimeUnit.SECONDS);

        assertTrue(events.isEmpty());
    }

    private CampusClawAiService piAiService(Model model, ApiProvider provider) {
        var providerRegistry = new ApiProviderRegistry(List.of(provider));
        var modelRegistry = new ModelRegistry();
        modelRegistry.register(model);
        return new CampusClawAiService(providerRegistry, modelRegistry);
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

    private static final class BlockingAbortTool implements AgentTool {

        private final CountDownLatch entered = new CountDownLatch(1);
        private final AtomicBoolean cancelled = new AtomicBoolean(false);
        private final ObjectMapper mapper = new ObjectMapper();

        @Override
        public String name() {
            return "blocking_tool";
        }

        @Override
        public String label() {
            return "blocking_tool";
        }

        @Override
        public String description() {
            return "Blocks until cancelled";
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
        ) throws Exception {
            entered.countDown();
            var cancelledLatch = new CountDownLatch(1);
            signal.onCancel(() -> {
                cancelled.set(true);
                cancelledLatch.countDown();
            });
            assertTrue(cancelledLatch.await(2, TimeUnit.SECONDS));
            return new AgentToolResult(List.of(new TextContent("cancelled")), Map.of("cancelled", true));
        }
    }

    private static final class SteeringTool implements AgentTool {

        private final Agent agent;
        private final ObjectMapper mapper = new ObjectMapper();

        private SteeringTool(Agent agent) {
            this.agent = agent;
        }

        @Override
        public String name() {
            return "steering_tool";
        }

        @Override
        public String label() {
            return "steering_tool";
        }

        @Override
        public String description() {
            return "Injects steering";
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
            agent.steer(new UserMessage("steer", 3L));
            onUpdate.onUpdate(new AgentToolResult(List.of(new TextContent("partial")), null));
            return new AgentToolResult(List.of(new TextContent("tool result")), Map.of("query", params.get("query")));
        }
    }

    private static final class TextResponseProvider implements ApiProvider {

        private final String promptText;
        private final String responseText;

        private TextResponseProvider(String promptText, String responseText) {
            this.promptText = promptText;
            this.responseText = responseText;
        }

        @Override
        public Api getApi() {
            return Api.ANTHROPIC_MESSAGES;
        }

        @Override
        public AssistantMessageEventStream stream(
            Model model,
            Context context,
            com.huawei.hicampus.mate.matecampusclaw.ai.types.StreamOptions options
        ) {
            throw new UnsupportedOperationException("Agent uses streamSimple");
        }

        @Override
        public AssistantMessageEventStream streamSimple(Model model, Context context, SimpleStreamOptions options) {
            var userMessage = (UserMessage) context.messages().getLast();
            assertEquals(promptText, ((TextContent) userMessage.content().getFirst()).text());
            return textStream(model, responseText);
        }
    }

    private static final class ToolCallProvider implements ApiProvider {

        private final String promptText;
        private final String toolName;
        private final Map<String, Object> args;

        private ToolCallProvider(String promptText, String toolName, Map<String, Object> args) {
            this.promptText = promptText;
            this.toolName = toolName;
            this.args = args;
        }

        @Override
        public Api getApi() {
            return Api.ANTHROPIC_MESSAGES;
        }

        @Override
        public AssistantMessageEventStream stream(
            Model model,
            Context context,
            com.huawei.hicampus.mate.matecampusclaw.ai.types.StreamOptions options
        ) {
            throw new UnsupportedOperationException("Agent uses streamSimple");
        }

        @Override
        public AssistantMessageEventStream streamSimple(Model model, Context context, SimpleStreamOptions options) {
            var userMessage = (UserMessage) context.messages().getLast();
            assertEquals(promptText, ((TextContent) userMessage.content().getFirst()).text());
            return toolCallStream(model, toolName, args);
        }
    }

    private static final class SteeringProvider implements ApiProvider {

        @Override
        public Api getApi() {
            return Api.ANTHROPIC_MESSAGES;
        }

        @Override
        public AssistantMessageEventStream stream(
            Model model,
            Context context,
            com.huawei.hicampus.mate.matecampusclaw.ai.types.StreamOptions options
        ) {
            throw new UnsupportedOperationException("Agent uses streamSimple");
        }

        @Override
        public AssistantMessageEventStream streamSimple(Model model, Context context, SimpleStreamOptions options) {
            var lastMessage = context.messages().getLast();
            if (lastMessage instanceof UserMessage userMessage) {
                var text = ((TextContent) userMessage.content().getFirst()).text();
                return switch (text) {
                    case "steering" -> toolCallStream(model, "steering_tool", Map.of("query", "x"));
                    case "steer" -> textStream(model, "done after steering");
                    default -> throw new IllegalStateException("Unexpected prompt: " + text);
                };
            }
            throw new IllegalStateException("Unexpected last message: " + lastMessage.getClass().getSimpleName());
        }
    }

    private static AssistantMessageEventStream toolCallStream(Model model, String toolName, Map<String, Object> args) {
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

    private static AssistantMessageEventStream textStream(Model model, String text) {
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
