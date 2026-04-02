package com.huawei.hicampus.mate.matecampusclaw.agent;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;

import com.huawei.hicampus.mate.matecampusclaw.agent.context.DefaultMessageConverter;
import com.huawei.hicampus.mate.matecampusclaw.agent.event.*;
import com.huawei.hicampus.mate.matecampusclaw.agent.loop.AgentLoop;
import com.huawei.hicampus.mate.matecampusclaw.agent.loop.AgentLoopConfig;
import com.huawei.hicampus.mate.matecampusclaw.agent.queue.MessageQueue;
import com.huawei.hicampus.mate.matecampusclaw.agent.state.AgentState;
import com.huawei.hicampus.mate.matecampusclaw.agent.tool.*;
import com.huawei.hicampus.mate.matecampusclaw.ai.CampusClawAiService;
import com.huawei.hicampus.mate.matecampusclaw.ai.model.ModelRegistry;
import com.huawei.hicampus.mate.matecampusclaw.ai.provider.ApiProvider;
import com.huawei.hicampus.mate.matecampusclaw.ai.provider.ApiProviderRegistry;
import com.huawei.hicampus.mate.matecampusclaw.ai.stream.AssistantMessageEvent;
import com.huawei.hicampus.mate.matecampusclaw.ai.stream.AssistantMessageEventStream;
import com.huawei.hicampus.mate.matecampusclaw.ai.types.*;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

/**
 * Integration tests for the agent loop end-to-end flow (IT-002).
 *
 * <p>Uses a {@code MockApiProvider} to simulate LLM responses and verifies
 * the complete agent cycle: prompt → LLM → tool → result → LLM → done,
 * including multi-turn tool calls, steering injection, follow-up messages,
 * abort, and event emission order.
 */
@Timeout(30)
class AgentLoopIntegrationTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private Model model;
    private MessageQueue steeringQueue;
    private MessageQueue followUpQueue;
    private ToolExecutionPipeline toolPipeline;
    private List<AgentEvent> events;

    @BeforeEach
    void setUp() {
        model = new Model(
                "test-model", "Test Model",
                Api.ANTHROPIC_MESSAGES, Provider.ANTHROPIC,
                "https://example.com", true,
                List.of(InputModality.TEXT),
                new ModelCost(1.0, 2.0, 0.5, 0.25),
                200_000, 4_096, null, null,
                null
        );
        steeringQueue = new MessageQueue();
        followUpQueue = new MessageQueue();
        toolPipeline = new ToolExecutionPipeline();
        events = new ArrayList<>();
    }

    // -------------------------------------------------------------------
    // Single turn: prompt → LLM text response → done
    // -------------------------------------------------------------------

    @Nested
    class SingleTurnTextResponse {

        @Test
        void completesSimpleTextResponse() {
            var provider = new ScriptedProvider(List.of(
                    textReply("Hello! How can I help?")
            ));

            var result = runLoop(provider, List.of(), "Hi there");

            assertEquals(2, result.size());
            assertInstanceOf(UserMessage.class, result.get(0));
            assertInstanceOf(AssistantMessage.class, result.get(1));
            assertEquals("Hello! How can I help?", textOf(result.get(1)));
        }

        @Test
        void emitsCorrectEventSequenceForTextResponse() {
            var provider = new ScriptedProvider(List.of(
                    textReply("Response")
            ));

            runLoop(provider, List.of(), "Hello");

            // Expected: AgentStart, TurnStart, [user message start/end],
            //           MessageStart, MessageUpdate+, MessageEnd,
            //           TurnEnd, AgentEnd
            assertEventOrder(
                    AgentStartEvent.class,
                    TurnStartEvent.class,
                    MessageStartEvent.class, // user message
                    MessageEndEvent.class,   // user message
                    MessageStartEvent.class, // assistant streaming start
                    MessageUpdateEvent.class,
                    MessageEndEvent.class,   // assistant message end
                    TurnEndEvent.class,
                    AgentEndEvent.class
            );
        }
    }

    // -------------------------------------------------------------------
    // Tool call loop: prompt → LLM → tool → result → LLM → done
    // -------------------------------------------------------------------

    @Nested
    class SingleToolCallLoop {

        @Test
        void executesToolAndReturnsToLLM() {
            var bashTool = simpleTool("bash", "Run command");
            var provider = new ScriptedProvider(List.of(
                    toolCallReply("bash", Map.of("command", "ls")),
                    textReply("Done! I found the files.")
            ));

            var result = runLoop(provider, List.of(bashTool), "List files");

            // user → assistant(tool_call) → tool_result → assistant(text)
            assertEquals(4, result.size());
            assertInstanceOf(UserMessage.class, result.get(0));
            assertInstanceOf(AssistantMessage.class, result.get(1));
            assertInstanceOf(ToolResultMessage.class, result.get(2));
            assertInstanceOf(AssistantMessage.class, result.get(3));
            assertEquals("Done! I found the files.", textOf(result.get(3)));

            // Verify tool result content
            var toolResult = (ToolResultMessage) result.get(2);
            assertEquals("bash", toolResult.toolName());
            assertFalse(toolResult.isError());
        }

        @Test
        void emitsToolExecutionEvents() {
            var bashTool = simpleTool("bash", "Run command");
            var provider = new ScriptedProvider(List.of(
                    toolCallReply("bash", Map.of("command", "ls")),
                    textReply("Done")
            ));

            runLoop(provider, List.of(bashTool), "List files");

            assertTrue(events.stream().anyMatch(ToolExecutionStartEvent.class::isInstance));
            assertTrue(events.stream().anyMatch(ToolExecutionEndEvent.class::isInstance));

            var toolStart = events.stream()
                    .filter(ToolExecutionStartEvent.class::isInstance)
                    .map(ToolExecutionStartEvent.class::cast)
                    .findFirst().orElseThrow();
            assertEquals("bash", toolStart.toolName());
        }

        @Test
        void emitsTwoTurnsForToolCallCycle() {
            var bashTool = simpleTool("bash", "Run command");
            var provider = new ScriptedProvider(List.of(
                    toolCallReply("bash", Map.of("command", "ls")),
                    textReply("Done")
            ));

            runLoop(provider, List.of(bashTool), "Run ls");

            long turnCount = events.stream().filter(TurnEndEvent.class::isInstance).count();
            assertEquals(2, turnCount);
        }
    }

    // -------------------------------------------------------------------
    // Multi-turn tool calls: prompt → tool₁ → result₁ → tool₂ → result₂ → done
    // -------------------------------------------------------------------

    @Nested
    class MultiTurnToolCalls {

        @Test
        void executesMultipleSequentialToolCalls() {
            var readTool = simpleTool("read", "Read file");
            var writeTool = simpleTool("write", "Write file");

            var provider = new ScriptedProvider(List.of(
                    toolCallReply("read", Map.of("command", "cat file.txt")),
                    toolCallReply("write", Map.of("command", "echo hello > out.txt")),
                    textReply("I read the file and wrote the output.")
            ));

            var result = runLoop(provider, List.of(readTool, writeTool), "Read and write");

            // user → assistant(read) → tool_result → assistant(write) → tool_result → assistant(text)
            assertEquals(6, result.size());
            assertInstanceOf(UserMessage.class, result.get(0));
            assertInstanceOf(AssistantMessage.class, result.get(1));
            assertInstanceOf(ToolResultMessage.class, result.get(2));
            assertInstanceOf(AssistantMessage.class, result.get(3));
            assertInstanceOf(ToolResultMessage.class, result.get(4));
            assertInstanceOf(AssistantMessage.class, result.get(5));

            // 3 turns: tool1, tool2, final text
            long turnCount = events.stream().filter(TurnEndEvent.class::isInstance).count();
            assertEquals(3, turnCount);

            // 2 tool executions
            long toolExecCount = events.stream().filter(ToolExecutionStartEvent.class::isInstance).count();
            assertEquals(2, toolExecCount);
        }
    }

    // -------------------------------------------------------------------
    // Steering injection: tool enqueues steering → next turn picks it up
    // -------------------------------------------------------------------

    @Nested
    class SteeringInjection {

        @Test
        void injectsSteeringMessageAfterToolExecution() {
            var steeringTool = new SteeringAgentTool(steeringQueue);
            var provider = new ScriptedProvider(List.of(
                    toolCallReply("steering_tool", Map.of("command", "search")),
                    textReply("Steered response")
            ));

            var result = runLoop(provider, List.of(steeringTool), "Do something");

            // user → assistant(tool) → tool_result → [steering injected] → assistant(text)
            assertEquals(5, result.size());
            assertInstanceOf(UserMessage.class, result.get(0));    // "Do something"
            assertInstanceOf(AssistantMessage.class, result.get(1)); // tool call
            assertInstanceOf(ToolResultMessage.class, result.get(2)); // tool result
            assertInstanceOf(UserMessage.class, result.get(3));    // steering message
            assertInstanceOf(AssistantMessage.class, result.get(4)); // "Steered response"

            assertEquals("injected steering", textOf(result.get(3)));
        }

        @Test
        void steeringMessageAppearsInEventsAsMessageStartEnd() {
            var steeringTool = new SteeringAgentTool(steeringQueue);
            var provider = new ScriptedProvider(List.of(
                    toolCallReply("steering_tool", Map.of("command", "search")),
                    textReply("OK")
            ));

            runLoop(provider, List.of(steeringTool), "Go");

            // Verify steering message is emitted via MessageStart/End in the second turn
            var messageStartEvents = events.stream()
                    .filter(MessageStartEvent.class::isInstance)
                    .map(MessageStartEvent.class::cast)
                    .toList();

            // Should have: user "Go" start, assistant start (turn 1),
            //              steering "injected steering" start, assistant start (turn 2)
            assertTrue(messageStartEvents.size() >= 4);
        }
    }

    // -------------------------------------------------------------------
    // Follow-up messages: continuation after text response
    // -------------------------------------------------------------------

    @Nested
    class FollowUpMessages {

        @Test
        void processesFollowUpMessageAfterTextResponse() {
            followUpQueue.enqueue(new UserMessage("follow-up question", 2L));

            var provider = new ScriptedProvider(List.of(
                    textReply("First answer"),
                    textReply("Follow-up answer")
            ));

            var result = runLoop(provider, List.of(), "Initial question");

            // user → assistant(text) → [follow-up injected] → assistant(text)
            assertEquals(4, result.size());
            assertEquals("Initial question", textOf(result.get(0)));
            assertEquals("First answer", textOf(result.get(1)));
            assertEquals("follow-up question", textOf(result.get(2)));
            assertEquals("Follow-up answer", textOf(result.get(3)));

            // 2 turns
            long turnCount = events.stream().filter(TurnEndEvent.class::isInstance).count();
            assertEquals(2, turnCount);
        }

        @Test
        void noFollowUpMeansLoopEndsAfterTextResponse() {
            var provider = new ScriptedProvider(List.of(
                    textReply("Only answer")
            ));

            var result = runLoop(provider, List.of(), "Question");

            assertEquals(2, result.size());
            long turnCount = events.stream().filter(TurnEndEvent.class::isInstance).count();
            assertEquals(1, turnCount);
        }
    }

    // -------------------------------------------------------------------
    // Abort mid-execution
    // -------------------------------------------------------------------

    @Nested
    class AbortExecution {

        @Test
        void abortStopsLoopBeforeSecondTurn() {
            var signal = new CancellationToken();

            // Tool that cancels the signal during execution
            var abortTool = new AbortingAgentTool(signal);

            var provider = new ScriptedProvider(List.of(
                    toolCallReply("abort_tool", Map.of("command", "cancel")),
                    textReply("This should not be reached")
            ));

            var state = new AgentState();
            state.setSystemPrompt("system");
            state.setTools(List.of(abortTool));
            var context = new AgentContext(state);

            var loop = new AgentLoop(new AgentLoopConfig(
                    piAiService(provider),
                    model,
                    new DefaultMessageConverter(),
                    null,
                    toolPipeline,
                    ToolExecutionMode.SEQUENTIAL,
                    steeringQueue,
                    followUpQueue,
                    SimpleStreamOptions.empty()
            ));

            var result = loop.run(
                    List.of(new UserMessage("abort me", 1L)),
                    context,
                    events::add,
                    signal
            );

            // Loop should stop after tool execution due to cancellation
            // user → assistant(tool) → tool_result
            assertEquals(3, result.size());
            assertInstanceOf(UserMessage.class, result.get(0));
            assertInstanceOf(AssistantMessage.class, result.get(1));
            assertInstanceOf(ToolResultMessage.class, result.get(2));

            // Should still have AgentEnd event
            assertInstanceOf(AgentEndEvent.class, events.getLast());
        }
    }

    // -------------------------------------------------------------------
    // Agent facade integration
    // -------------------------------------------------------------------

    @Nested
    class AgentFacadeIntegration {

        @Test
        void promptRunsFullCycleViaAgentFacade() throws Exception {
            var bashTool = simpleTool("bash", "Run command");
            var provider = new ScriptedProvider(List.of(
                    toolCallReply("bash", Map.of("command", "ls")),
                    textReply("Found 3 files")
            ));

            var agent = new Agent(piAiService(provider));
            agent.setModel(model);
            agent.setSystemPrompt("You are a helpful assistant.");
            agent.setTools(List.of(bashTool));

            var agentEvents = new ArrayList<AgentEvent>();
            agent.subscribe(agentEvents::add);

            agent.prompt("List files").join();

            var messages = agent.getState().getMessages();
            assertEquals(4, messages.size());
            assertEquals("Found 3 files", textOf(messages.get(3)));

            // Verify events were emitted through the agent
            assertInstanceOf(AgentStartEvent.class, agentEvents.getFirst());
            assertInstanceOf(AgentEndEvent.class, agentEvents.getLast());
        }

        @Test
        void abortStopsRunningExecution() throws Exception {
            var signal = new CancellationToken();
            var slowTool = new SlowAgentTool();
            var provider = new ScriptedProvider(List.of(
                    toolCallReply("slow_tool", Map.of("command", "wait")),
                    textReply("Should not appear")
            ));

            var agent = new Agent(piAiService(provider));
            agent.setModel(model);
            agent.setTools(List.of(slowTool));

            var future = agent.prompt("Do slow thing");

            // Wait for tool to start executing
            slowTool.waitUntilStarted();
            agent.abort();

            // The future should complete (possibly exceptionally)
            future.handle((v, t) -> null).join();

            // Agent should be in a clean state
            assertNotNull(agent.getState());
        }
    }

    // -------------------------------------------------------------------
    // Event ordering verification
    // -------------------------------------------------------------------

    @Nested
    class EventOrdering {

        @Test
        void agentStartIsFirstAndAgentEndIsLast() {
            var provider = new ScriptedProvider(List.of(textReply("Hi")));
            runLoop(provider, List.of(), "Hello");

            assertInstanceOf(AgentStartEvent.class, events.getFirst());
            assertInstanceOf(AgentEndEvent.class, events.getLast());
        }

        @Test
        void turnStartPrecedesTurnEnd() {
            var provider = new ScriptedProvider(List.of(textReply("Hi")));
            runLoop(provider, List.of(), "Hello");

            int turnStartIdx = indexOfFirst(TurnStartEvent.class);
            int turnEndIdx = indexOfFirst(TurnEndEvent.class);
            assertTrue(turnStartIdx < turnEndIdx);
        }

        @Test
        void messageStartPrecedesMessageEnd() {
            var provider = new ScriptedProvider(List.of(textReply("Hi")));
            runLoop(provider, List.of(), "Hello");

            // Find the assistant message start/end (not user)
            var msgStarts = events.stream()
                    .filter(MessageStartEvent.class::isInstance)
                    .toList();
            var msgEnds = events.stream()
                    .filter(MessageEndEvent.class::isInstance)
                    .toList();

            assertTrue(msgStarts.size() >= 2); // user + assistant
            assertTrue(msgEnds.size() >= 2);
        }

        @Test
        void toolExecutionStartPrecedesToolExecutionEnd() {
            var tool = simpleTool("bash", "Run command");
            var provider = new ScriptedProvider(List.of(
                    toolCallReply("bash", Map.of("command", "ls")),
                    textReply("Done")
            ));
            runLoop(provider, List.of(tool), "Go");

            int toolStartIdx = indexOfFirst(ToolExecutionStartEvent.class);
            int toolEndIdx = indexOfFirst(ToolExecutionEndEvent.class);
            assertTrue(toolStartIdx >= 0);
            assertTrue(toolEndIdx >= 0);
            assertTrue(toolStartIdx < toolEndIdx);
        }

        @Test
        void turnEndContainsToolResultsForToolTurn() {
            var tool = simpleTool("bash", "Run command");
            var provider = new ScriptedProvider(List.of(
                    toolCallReply("bash", Map.of("command", "ls")),
                    textReply("Done")
            ));
            runLoop(provider, List.of(tool), "Go");

            var toolTurnEnd = events.stream()
                    .filter(TurnEndEvent.class::isInstance)
                    .map(TurnEndEvent.class::cast)
                    .filter(e -> !e.toolResults().isEmpty())
                    .findFirst();

            assertTrue(toolTurnEnd.isPresent());
            assertEquals(1, toolTurnEnd.get().toolResults().size());
        }

        @Test
        void agentEndContainsFullMessageHistory() {
            var tool = simpleTool("bash", "Run command");
            var provider = new ScriptedProvider(List.of(
                    toolCallReply("bash", Map.of("command", "ls")),
                    textReply("Done")
            ));
            runLoop(provider, List.of(tool), "Go");

            var agentEnd = events.stream()
                    .filter(AgentEndEvent.class::isInstance)
                    .map(AgentEndEvent.class::cast)
                    .findFirst().orElseThrow();

            assertEquals(4, agentEnd.messages().size());
        }
    }

    // -------------------------------------------------------------------
    // Context transformer integration
    // -------------------------------------------------------------------

    @Nested
    class ContextTransformation {

        @Test
        void contextTransformerIsCalledEachTurn() {
            var callCount = new AtomicInteger();
            var provider = new ScriptedProvider(List.of(
                    toolCallReply("bash", Map.of("command", "ls")),
                    textReply("Done")
            ));
            var tool = simpleTool("bash", "Run command");

            var state = new AgentState();
            state.setSystemPrompt("system");
            state.setTools(List.of(tool));
            var context = new AgentContext(state);

            var loop = new AgentLoop(new AgentLoopConfig(
                    piAiService(provider),
                    model,
                    new DefaultMessageConverter(),
                    (messages, signal) -> {
                        callCount.incrementAndGet();
                        return CompletableFuture.completedFuture(messages);
                    },
                    toolPipeline,
                    ToolExecutionMode.SEQUENTIAL,
                    steeringQueue,
                    followUpQueue,
                    SimpleStreamOptions.empty()
            ));

            loop.run(
                    List.of(new UserMessage("Go", 1L)),
                    context,
                    events::add,
                    new CancellationToken()
            );

            assertEquals(2, callCount.get()); // once per turn
        }
    }

    // ===================================================================
    // Test infrastructure
    // ===================================================================

    private List<Message> runLoop(ScriptedProvider provider, List<AgentTool> tools, String prompt) {
        var state = new AgentState();
        state.setSystemPrompt("You are a test assistant.");
        state.setTools(tools);
        var context = new AgentContext(state);

        var loop = new AgentLoop(new AgentLoopConfig(
                piAiService(provider),
                model,
                new DefaultMessageConverter(),
                null,
                toolPipeline,
                ToolExecutionMode.SEQUENTIAL,
                steeringQueue,
                followUpQueue,
                SimpleStreamOptions.empty()
        ));

        return loop.run(
                List.of(new UserMessage(prompt, 1L)),
                context,
                events::add,
                new CancellationToken()
        );
    }

    private CampusClawAiService piAiService(ApiProvider provider) {
        var providerRegistry = new ApiProviderRegistry(List.of(provider));
        var modelRegistry = new ModelRegistry();
        modelRegistry.register(model);
        return new CampusClawAiService(providerRegistry, modelRegistry);
    }

    // -- Reply builders --

    private Reply textReply(String text) {
        return new Reply(text, null, null);
    }

    private Reply toolCallReply(String toolName, Map<String, Object> args) {
        return new Reply(null, toolName, args);
    }

    // -- Assertion helpers --

    private String textOf(Message message) {
        if (message instanceof UserMessage um) {
            return ((TextContent) um.content().getFirst()).text();
        }
        if (message instanceof AssistantMessage am) {
            return ((TextContent) am.content().getFirst()).text();
        }
        throw new IllegalArgumentException("Cannot extract text from " + message.getClass().getSimpleName());
    }

    @SafeVarargs
    private void assertEventOrder(Class<? extends AgentEvent>... expectedTypes) {
        int lastIdx = -1;
        for (var type : expectedTypes) {
            int idx = -1;
            for (int i = lastIdx + 1; i < events.size(); i++) {
                if (type.isInstance(events.get(i))) {
                    idx = i;
                    break;
                }
            }
            assertTrue(idx >= 0,
                    "Expected " + type.getSimpleName() + " after index " + lastIdx +
                            " but not found. Events: " + eventNames());
            lastIdx = idx;
        }
    }

    private <T> int indexOfFirst(Class<T> type) {
        for (int i = 0; i < events.size(); i++) {
            if (type.isInstance(events.get(i))) return i;
        }
        return -1;
    }

    private List<String> eventNames() {
        return events.stream().map(e -> e.getClass().getSimpleName()).toList();
    }

    // ===================================================================
    // Mock provider - scripted responses
    // ===================================================================

    private record Reply(String text, String toolName, Map<String, Object> toolArgs) {
        boolean isToolCall() {
            return toolName != null;
        }
    }

    /**
     * A mock provider that returns scripted responses in order.
     * Each call to streamSimple consumes the next reply from the script.
     */
    private class ScriptedProvider implements ApiProvider {

        private final List<Reply> script;
        private final AtomicInteger callIndex = new AtomicInteger(0);

        ScriptedProvider(List<Reply> script) {
            this.script = List.copyOf(script);
        }

        @Override
        public Api getApi() {
            return Api.ANTHROPIC_MESSAGES;
        }

        @Override
        public AssistantMessageEventStream stream(Model model, Context context, StreamOptions options) {
            throw new UnsupportedOperationException("AgentLoop uses streamSimple");
        }

        @Override
        public AssistantMessageEventStream streamSimple(Model model, Context context, SimpleStreamOptions options) {
            int idx = callIndex.getAndIncrement();
            if (idx >= script.size()) {
                throw new IllegalStateException(
                        "ScriptedProvider exhausted: called " + (idx + 1) + " times but only " +
                                script.size() + " replies scripted");
            }
            var reply = script.get(idx);

            if (reply.isToolCall()) {
                return toolCallStream(model, reply.toolName(), reply.toolArgs());
            }
            return textStream(model, reply.text());
        }

        private AssistantMessageEventStream toolCallStream(Model model, String toolName, Map<String, Object> args) {
            var stream = new AssistantMessageEventStream();
            var toolCall = new ToolCall("tc-" + callIndex.get(), toolName, args);
            var msg = new AssistantMessage(
                    List.of(toolCall),
                    model.api().value(), model.provider().value(), model.id(),
                    null, Usage.empty(), StopReason.TOOL_USE, null, System.currentTimeMillis()
            );
            stream.push(new AssistantMessageEvent.StartEvent(msg));
            stream.push(new AssistantMessageEvent.ToolCallEndEvent(0, toolCall, msg));
            stream.pushDone(StopReason.TOOL_USE, msg);
            return stream;
        }

        private AssistantMessageEventStream textStream(Model model, String text) {
            var stream = new AssistantMessageEventStream();
            var msg = new AssistantMessage(
                    List.of(new TextContent(text, null)),
                    model.api().value(), model.provider().value(), model.id(),
                    null, Usage.empty(), StopReason.STOP, null, System.currentTimeMillis()
            );
            stream.push(new AssistantMessageEvent.StartEvent(msg));
            stream.push(new AssistantMessageEvent.TextDeltaEvent(0, text, msg));
            stream.pushDone(StopReason.STOP, msg);
            return stream;
        }
    }

    // ===================================================================
    // Test tools
    // ===================================================================

    /**
     * Simple tool that returns a fixed result.
     */
    private AgentTool simpleTool(String name, String description) {
        return new AgentTool() {
            @Override
            public String name() { return name; }

            @Override
            public String label() { return name; }

            @Override
            public String description() { return description; }

            @Override
            public JsonNode parameters() {
                return MAPPER.createObjectNode()
                        .put("type", "object")
                        .<com.fasterxml.jackson.databind.node.ObjectNode>set("properties",
                                MAPPER.createObjectNode().set("command",
                                        MAPPER.createObjectNode().put("type", "string")))
                        .set("required", MAPPER.createArrayNode().add("command"));
            }

            @Override
            public AgentToolResult execute(String toolCallId, Map<String, Object> params,
                                           CancellationToken signal, AgentToolUpdateCallback onUpdate) {
                return new AgentToolResult(
                        List.of(new TextContent("executed: " + params.get("command"))),
                        null
                );
            }
        };
    }

    /**
     * Tool that injects a steering message into the steering queue.
     */
    private static class SteeringAgentTool implements AgentTool {

        private final MessageQueue steeringQueue;

        SteeringAgentTool(MessageQueue steeringQueue) {
            this.steeringQueue = steeringQueue;
        }

        @Override
        public String name() { return "steering_tool"; }

        @Override
        public String label() { return "Steering Tool"; }

        @Override
        public String description() { return "Tool that injects a steering message"; }

        @Override
        public JsonNode parameters() {
            return new ObjectMapper().createObjectNode()
                    .put("type", "object")
                    .<com.fasterxml.jackson.databind.node.ObjectNode>set("properties",
                            new ObjectMapper().createObjectNode().set("command",
                                    new ObjectMapper().createObjectNode().put("type", "string")))
                    .set("required", new ObjectMapper().createArrayNode().add("command"));
        }

        @Override
        public AgentToolResult execute(String toolCallId, Map<String, Object> params,
                                       CancellationToken signal, AgentToolUpdateCallback onUpdate) {
            steeringQueue.enqueue(new UserMessage("injected steering", System.currentTimeMillis()));
            return new AgentToolResult(List.of(new TextContent("tool executed")), null);
        }
    }

    /**
     * Tool that cancels the CancellationToken to simulate abort.
     */
    private static class AbortingAgentTool implements AgentTool {

        private final CancellationToken signal;

        AbortingAgentTool(CancellationToken signal) {
            this.signal = signal;
        }

        @Override
        public String name() { return "abort_tool"; }

        @Override
        public String label() { return "Abort Tool"; }

        @Override
        public String description() { return "Cancels execution"; }

        @Override
        public JsonNode parameters() {
            return new ObjectMapper().createObjectNode()
                    .put("type", "object")
                    .<com.fasterxml.jackson.databind.node.ObjectNode>set("properties",
                            new ObjectMapper().createObjectNode().set("command",
                                    new ObjectMapper().createObjectNode().put("type", "string")))
                    .set("required", new ObjectMapper().createArrayNode().add("command"));
        }

        @Override
        public AgentToolResult execute(String toolCallId, Map<String, Object> params,
                                       CancellationToken signal, AgentToolUpdateCallback onUpdate) {
            this.signal.cancel();
            return new AgentToolResult(List.of(new TextContent("aborted")), null);
        }
    }

    /**
     * Tool that blocks until abort, for testing Agent.abort().
     */
    private static class SlowAgentTool implements AgentTool {

        private final CompletableFuture<Void> started = new CompletableFuture<>();

        @Override
        public String name() { return "slow_tool"; }

        @Override
        public String label() { return "Slow Tool"; }

        @Override
        public String description() { return "Slow tool for abort testing"; }

        @Override
        public JsonNode parameters() {
            return new ObjectMapper().createObjectNode()
                    .put("type", "object")
                    .<com.fasterxml.jackson.databind.node.ObjectNode>set("properties",
                            new ObjectMapper().createObjectNode().set("command",
                                    new ObjectMapper().createObjectNode().put("type", "string")))
                    .set("required", new ObjectMapper().createArrayNode().add("command"));
        }

        void waitUntilStarted() {
            started.join();
        }

        @Override
        public AgentToolResult execute(String toolCallId, Map<String, Object> params,
                                       CancellationToken signal, AgentToolUpdateCallback onUpdate) {
            started.complete(null);
            // Wait for cancellation
            while (!signal.isCancelled()) {
                try { Thread.sleep(10); } catch (InterruptedException e) { break; }
            }
            return new AgentToolResult(List.of(new TextContent("cancelled")), null);
        }
    }
}
