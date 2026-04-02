package com.huawei.hicampus.mate.matecampusclaw.agent.tool;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import com.huawei.hicampus.mate.matecampusclaw.agent.event.AgentEvent;
import com.huawei.hicampus.mate.matecampusclaw.agent.event.ToolExecutionEndEvent;
import com.huawei.hicampus.mate.matecampusclaw.agent.event.ToolExecutionStartEvent;
import com.huawei.hicampus.mate.matecampusclaw.agent.event.ToolExecutionUpdateEvent;
import com.huawei.hicampus.mate.matecampusclaw.ai.types.AssistantMessage;
import com.huawei.hicampus.mate.matecampusclaw.ai.types.ContentBlock;
import com.huawei.hicampus.mate.matecampusclaw.ai.types.StopReason;
import com.huawei.hicampus.mate.matecampusclaw.ai.types.TextContent;
import com.huawei.hicampus.mate.matecampusclaw.ai.types.ToolCall;
import com.huawei.hicampus.mate.matecampusclaw.ai.types.ToolResultMessage;
import com.huawei.hicampus.mate.matecampusclaw.ai.types.Usage;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.junit.jupiter.api.Test;

class ToolExecutionPipelineTest {

    @Test
    void executesToolAndEmitsLifecycleEvents() {
        var pipeline = new ToolExecutionPipeline();
        var tool = new MockAgentTool("search", false, false, 0L);
        var toolCall = new ToolCall("call-1", "search", Map.of("query", "java"));
        var context = sampleContext();
        var events = new ArrayList<AgentEvent>();

        var result = pipeline.execute(
            tool,
            toolCall,
            Map.of("query", "java"),
            context,
            new CancellationToken(),
            events::add
        );

        assertFalse(result.isError());
        assertEquals("call-1", result.toolCallId());
        assertEquals("search", result.toolName());
        assertEquals("final:java", text(result.content().getFirst()));
        assertEquals(Map.of("query", "java", "stage", "final"), result.details());

        assertEquals(3, events.size());
        assertInstanceOf(ToolExecutionStartEvent.class, events.get(0));
        assertInstanceOf(ToolExecutionUpdateEvent.class, events.get(1));
        assertInstanceOf(ToolExecutionEndEvent.class, events.get(2));
        assertEquals("search", ((ToolExecutionStartEvent) events.get(0)).toolName());
        assertInstanceOf(AgentToolResult.class, ((ToolExecutionUpdateEvent) events.get(1)).partialResult());
        assertInstanceOf(AgentToolResult.class, ((ToolExecutionEndEvent) events.get(2)).result());
    }

    @Test
    void beforeToolCallCanBlockExecution() {
        var pipeline = new ToolExecutionPipeline();
        var tool = new MockAgentTool("search", false, false, 0L);
        var toolCall = new ToolCall("call-1", "search", Map.of("query", "java"));
        var beforeCalled = new AtomicBoolean(false);
        var afterCalled = new AtomicBoolean(false);
        var events = new ArrayList<AgentEvent>();

        pipeline.setBeforeToolCall(context -> {
            beforeCalled.set(true);
            return BeforeToolCallResult.block("blocked by policy");
        });
        pipeline.setAfterToolCall(context -> {
            afterCalled.set(true);
            return AfterToolCallResult.noOverride();
        });

        var result = pipeline.execute(
            tool,
            toolCall,
            Map.of("query", "java"),
            sampleContext(),
            new CancellationToken(),
            events::add
        );

        assertTrue(beforeCalled.get());
        assertFalse(afterCalled.get());
        assertFalse(tool.executed.get());
        assertTrue(result.isError());
        assertEquals("blocked by policy", text(result.content().getFirst()));
        assertTrue(events.isEmpty());
    }

    @Test
    void afterToolCallCanOverrideResult() {
        var pipeline = new ToolExecutionPipeline();
        var tool = new MockAgentTool("search", false, false, 0L);
        var toolCall = new ToolCall("call-1", "search", Map.of("query", "java"));
        var seenIsError = new AtomicReference<Boolean>();

        pipeline.setAfterToolCall(context -> {
            seenIsError.set(context.isError());
            return new AfterToolCallResult(
                List.of(new TextContent("overridden")),
                Map.of("source", "after-hook"),
                true
            );
        });

        var result = pipeline.execute(
            tool,
            toolCall,
            Map.of("query", "java"),
            sampleContext(),
            new CancellationToken(),
            null
        );

        assertEquals(Boolean.FALSE, seenIsError.get());
        assertTrue(result.isError());
        assertEquals("overridden", text(result.content().getFirst()));
        assertEquals(Map.of("source", "after-hook"), result.details());
    }

    @Test
    void validatesArgumentsAgainstToolSchema() {
        var pipeline = new ToolExecutionPipeline();
        var tool = new MockAgentTool("search", false, false, 0L);
        var toolCall = new ToolCall("call-1", "search", Map.of("query", 1));
        var events = new ArrayList<AgentEvent>();

        var result = pipeline.execute(
            tool,
            toolCall,
            Map.of("query", 1),
            sampleContext(),
            new CancellationToken(),
            events::add
        );

        assertTrue(result.isError());
        assertTrue(text(result.content().getFirst()).contains("Tool arguments failed validation"));
        assertFalse(tool.executed.get());
        assertEquals(2, events.size());
        assertInstanceOf(ToolExecutionStartEvent.class, events.getFirst());
        assertInstanceOf(ToolExecutionEndEvent.class, events.getLast());
    }

    @Test
    void executesAllInParallelUsingVirtualThreads() {
        var pipeline = new ToolExecutionPipeline();
        var context = sampleContext();
        var signal = new CancellationToken();
        var maxConcurrency = new AtomicInteger();
        var currentConcurrency = new AtomicInteger();
        var ready = new CountDownLatch(3);

        var calls = List.of(
            new ToolCallWithTool(
                new ToolCall("call-1", "search", Map.of("query", "one")),
                new MockAgentTool("search", false, true, 0L, currentConcurrency, maxConcurrency, ready),
                Map.of("query", "one")
            ),
            new ToolCallWithTool(
                new ToolCall("call-2", "search", Map.of("query", "two")),
                new MockAgentTool("search", false, true, 0L, currentConcurrency, maxConcurrency, ready),
                Map.of("query", "two")
            ),
            new ToolCallWithTool(
                new ToolCall("call-3", "search", Map.of("query", "three")),
                new MockAgentTool("search", false, true, 0L, currentConcurrency, maxConcurrency, ready),
                Map.of("query", "three")
            )
        );

        var results = pipeline.executeAll(calls, ToolExecutionMode.PARALLEL, context, signal, null);

        assertEquals(3, results.size());
        assertTrue(maxConcurrency.get() > 1);
        assertEquals(List.of("call-1", "call-2", "call-3"),
            results.stream().map(ToolResultMessage::toolCallId).toList());
    }

    private AgentContext sampleContext() {
        var assistantMessage = new AssistantMessage(
            List.of(new TextContent("assistant")),
            "anthropic-messages",
            "anthropic",
            "claude-opus-4-6",
            null,
            Usage.empty(),
            StopReason.TOOL_USE,
            null,
            1L
        );
        return new AgentContext(assistantMessage, List.of(assistantMessage));
    }

    private String text(ContentBlock block) {
        return ((TextContent) block).text();
    }

    private static final class MockAgentTool implements AgentTool {

        private final String name;
        private final boolean throwOnExecute;
        private final boolean coordinateForParallelism;
        private final long delayMillis;
        private final AtomicBoolean executed = new AtomicBoolean(false);
        private final AtomicInteger currentConcurrency;
        private final AtomicInteger maxConcurrency;
        private final CountDownLatch ready;
        private final ObjectMapper mapper = new ObjectMapper();

        private MockAgentTool(String name, boolean throwOnExecute, boolean coordinateForParallelism, long delayMillis) {
            this(name, throwOnExecute, coordinateForParallelism, delayMillis, null, null, null);
        }

        private MockAgentTool(
            String name,
            boolean throwOnExecute,
            boolean coordinateForParallelism,
            long delayMillis,
            AtomicInteger currentConcurrency,
            AtomicInteger maxConcurrency,
            CountDownLatch ready
        ) {
            this.name = name;
            this.throwOnExecute = throwOnExecute;
            this.coordinateForParallelism = coordinateForParallelism;
            this.delayMillis = delayMillis;
            this.currentConcurrency = currentConcurrency;
            this.maxConcurrency = maxConcurrency;
            this.ready = ready;
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
            executed.set(true);

            if (coordinateForParallelism) {
                var concurrency = currentConcurrency.incrementAndGet();
                maxConcurrency.accumulateAndGet(concurrency, Math::max);
                ready.countDown();
                assertTrue(ready.await(500, TimeUnit.MILLISECONDS));
            }

            try {
                if (delayMillis > 0) {
                    Thread.sleep(delayMillis);
                }
                if (throwOnExecute) {
                    throw new IllegalStateException("tool failed");
                }

                onUpdate.onUpdate(new AgentToolResult(
                    List.of(new TextContent("partial:" + params.get("query"))),
                    Map.of("query", params.get("query"), "stage", "partial")
                ));

                return new AgentToolResult(
                    List.of(new TextContent("final:" + params.get("query"))),
                    Map.of("query", params.get("query"), "stage", "final")
                );
            } finally {
                if (coordinateForParallelism) {
                    currentConcurrency.decrementAndGet();
                }
            }
        }
    }
}
