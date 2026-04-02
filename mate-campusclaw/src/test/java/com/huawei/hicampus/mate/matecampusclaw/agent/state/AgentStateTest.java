package com.huawei.hicampus.mate.matecampusclaw.agent.state;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import com.huawei.hicampus.mate.matecampusclaw.agent.tool.AgentTool;
import com.huawei.hicampus.mate.matecampusclaw.agent.tool.AgentToolResult;
import com.huawei.hicampus.mate.matecampusclaw.agent.tool.AgentToolUpdateCallback;
import com.huawei.hicampus.mate.matecampusclaw.agent.tool.CancellationToken;
import com.huawei.hicampus.mate.matecampusclaw.ai.types.Api;
import com.huawei.hicampus.mate.matecampusclaw.ai.types.ContentBlock;
import com.huawei.hicampus.mate.matecampusclaw.ai.types.InputModality;
import com.huawei.hicampus.mate.matecampusclaw.ai.types.Message;
import com.huawei.hicampus.mate.matecampusclaw.ai.types.Model;
import com.huawei.hicampus.mate.matecampusclaw.ai.types.ModelCost;
import com.huawei.hicampus.mate.matecampusclaw.ai.types.Provider;
import com.huawei.hicampus.mate.matecampusclaw.ai.types.TextContent;
import com.huawei.hicampus.mate.matecampusclaw.ai.types.ThinkingLevel;
import com.huawei.hicampus.mate.matecampusclaw.ai.types.UserMessage;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.junit.jupiter.api.Test;

class AgentStateTest {

    @Test
    void startsWithExpectedDefaults() {
        var state = new AgentState();

        assertNull(state.getSystemPrompt());
        assertNull(state.getModel());
        assertEquals(ThinkingLevel.OFF, state.getThinkingLevel());
        assertTrue(state.getTools().isEmpty());
        assertTrue(state.getMessages().isEmpty());
        assertFalse(state.isStreaming());
        assertNull(state.getStreamMessage());
        assertTrue(state.getPendingToolCalls().isEmpty());
        assertNull(state.getError());
    }

    @Test
    void storesAndReturnsMutableStateSafely() {
        var state = new AgentState();
        var model = sampleModel();
        var tool = new MockAgentTool("search");
        var messages = List.<Message>of(new UserMessage("hello", 1L));

        state.setSystemPrompt("You are helpful.");
        state.setModel(model);
        state.setThinkingLevel(ThinkingLevel.HIGH);
        state.setTools(List.of(tool));
        state.setMessages(messages);
        state.setStreaming(true);
        state.setStreamMessage(messages.getFirst());
        state.addPendingToolCall("call-1");
        state.setError("boom");

        assertEquals("You are helpful.", state.getSystemPrompt());
        assertSame(model, state.getModel());
        assertEquals(ThinkingLevel.HIGH, state.getThinkingLevel());
        assertEquals(List.of(tool), state.getTools());
        assertEquals(messages, state.getMessages());
        assertTrue(state.isStreaming());
        assertEquals(messages.getFirst(), state.getStreamMessage());
        assertEquals(Set.of("call-1"), state.getPendingToolCalls());
        assertEquals("boom", state.getError());
    }

    @Test
    void snapshotIsImmutableAndDetachedFromSubsequentStateChanges() {
        var state = new AgentState();
        var tool = new MockAgentTool("read");
        var firstMessage = new UserMessage("first", 1L);

        state.setSystemPrompt("system");
        state.setModel(sampleModel());
        state.setThinkingLevel(ThinkingLevel.MEDIUM);
        state.setTools(List.of(tool));
        state.setMessages(List.of(firstMessage));
        state.setStreaming(true);
        state.setStreamMessage(firstMessage);
        state.addPendingToolCall("call-1");
        state.setError("initial");

        var snapshot = state.snapshot();

        state.setSystemPrompt("changed");
        state.setTools(List.of(new MockAgentTool("write")));
        state.appendMessage(new UserMessage("second", 2L));
        state.setStreaming(false);
        state.setStreamMessage(null);
        state.addPendingToolCall("call-2");
        state.setError(null);

        assertEquals("system", snapshot.systemPrompt());
        assertEquals(1, snapshot.tools().size());
        assertEquals("read", snapshot.tools().getFirst().name());
        assertEquals(1, snapshot.messages().size());
        assertTrue(snapshot.streaming());
        assertEquals(firstMessage, snapshot.streamMessage());
        assertEquals(Set.of("call-1"), snapshot.pendingToolCalls());
        assertEquals("initial", snapshot.error());

        assertThrows(UnsupportedOperationException.class,
            () -> snapshot.tools().add(new MockAgentTool("fail")));
        assertThrows(UnsupportedOperationException.class,
            () -> snapshot.messages().add(new UserMessage("fail", 3L)));
        assertThrows(UnsupportedOperationException.class,
            () -> snapshot.pendingToolCalls().add("fail"));
    }

    @Test
    void collectionGettersReturnDefensiveCopies() {
        var state = new AgentState();
        var tool = new MockAgentTool("grep");
        var message = new UserMessage("hello", 1L);

        state.setTools(List.of(tool));
        state.setMessages(List.of(message));
        state.addPendingToolCall("call-1");

        assertThrows(UnsupportedOperationException.class, () -> state.getTools().add(tool));
        assertThrows(UnsupportedOperationException.class, () -> state.getMessages().add(message));
        assertThrows(UnsupportedOperationException.class, () -> state.getPendingToolCalls().add("call-2"));
    }

    @Test
    void canReplacePendingToolCallsAsASnapshot() {
        var state = new AgentState();

        state.setPendingToolCalls(Set.of("call-1", "call-2"));

        assertEquals(Set.of("call-1", "call-2"), state.getPendingToolCalls());
    }

    @Test
    void supportsConcurrentMessageAndPendingToolCallMutation() throws Exception {
        var state = new AgentState();
        var taskCount = 120;
        var start = new CountDownLatch(1);
        List<Future<?>> futures = new ArrayList<>();

        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            for (var i = 0; i < taskCount; i++) {
                final var index = i;
                futures.add(executor.submit(() -> {
                    start.await();
                    state.appendMessage(new UserMessage("message-" + index, index));
                    state.addPendingToolCall("call-" + index);
                    if ((index & 1) == 0) {
                        state.setStreaming(true);
                        state.setStreamMessage(new UserMessage("stream-" + index, index));
                    }
                    state.snapshot();
                    return null;
                }));
            }

            start.countDown();

            for (var future : futures) {
                future.get();
            }
        }

        assertEquals(taskCount, state.getMessages().size());
        assertEquals(taskCount, state.getPendingToolCalls().size());
        assertTrue(state.isStreaming());
        assertNotNull(state.getStreamMessage());
    }

    @Test
    void pendingToolCallHelpersAreThreadSafe() throws Exception {
        var state = new AgentState();
        var taskCount = 80;
        var start = new CountDownLatch(1);
        List<Future<?>> futures = new ArrayList<>();

        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            for (var i = 0; i < taskCount; i++) {
                final var index = i;
                futures.add(executor.submit(() -> {
                    start.await();
                    state.addPendingToolCall("call-" + index);
                    if ((index & 1) == 0) {
                        state.removePendingToolCall("call-" + index);
                    }
                    return null;
                }));
            }

            start.countDown();

            for (var future : futures) {
                future.get();
            }
        }

        assertEquals(taskCount / 2, state.getPendingToolCalls().size());
        assertFalse(state.hasPendingToolCall("call-0"));
        assertTrue(state.hasPendingToolCall("call-1"));

        state.clearPendingToolCalls();

        assertTrue(state.getPendingToolCalls().isEmpty());
    }

    @Test
    void messageHelpersReplaceAppendAndClearAtomically() {
        var state = new AgentState();
        var first = new UserMessage("first", 1L);
        var second = new UserMessage("second", 2L);

        state.replaceMessages(List.of(first));
        state.appendMessage(second);

        assertEquals(List.of(first, second), state.getMessages());

        state.clearMessages();

        assertTrue(state.getMessages().isEmpty());
    }

    private Model sampleModel() {
        return new Model(
            "model-1",
            "Model 1",
            Api.ANTHROPIC_MESSAGES,
            Provider.ANTHROPIC,
            "https://example.com",
            true,
            List.of(InputModality.TEXT),
            new ModelCost(1.0, 2.0, 0.5, 0.25),
            200_000,
            4_096,
            Map.of("x-test", "1"),
            null,
            null
        );
    }

    private static final class MockAgentTool implements AgentTool {

        private final String name;
        private final ObjectMapper mapper = new ObjectMapper();

        private MockAgentTool(String name) {
            this.name = name;
        }

        @Override
        public String name() {
            return name;
        }

        @Override
        public String label() {
            return name;
        }

        @Override
        public String description() {
            return "Mock tool " + name;
        }

        @Override
        public com.fasterxml.jackson.databind.JsonNode parameters() {
            return mapper.createObjectNode().put("type", "object");
        }

        @Override
        public AgentToolResult execute(
            String toolCallId,
            Map<String, Object> params,
            CancellationToken signal,
            AgentToolUpdateCallback onUpdate
        ) {
            return new AgentToolResult(List.<ContentBlock>of(new TextContent("ok")), null);
        }
    }
}
