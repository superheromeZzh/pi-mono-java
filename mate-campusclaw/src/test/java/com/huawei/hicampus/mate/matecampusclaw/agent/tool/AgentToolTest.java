package com.huawei.hicampus.mate.matecampusclaw.agent.tool;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import com.huawei.hicampus.mate.matecampusclaw.ai.types.TextContent;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class AgentToolTest {

    private ObjectMapper mapper;

    @BeforeEach
    void setUp() {
        mapper = new ObjectMapper();
    }

    @Nested
    class MockToolTests {

        @Test
        void exposesMetadata() {
            var tool = new MockAgentTool();

            assertEquals("mock_tool", tool.name());
            assertEquals("Mock Tool", tool.label());
            assertEquals("A mock tool used for AgentTool unit tests.", tool.description());
            assertEquals("object", tool.parameters().get("type").asText());
            assertEquals("string", tool.parameters().get("properties").get("query").get("type").asText());
        }

        @Test
        void executeReturnsFinalResultAndPublishesPartialUpdate() throws Exception {
            var tool = new MockAgentTool();
            var updates = new ArrayList<AgentToolResult>();

            var result = tool.execute(
                "call-123",
                Map.of("query", "java"),
                new CancellationToken(),
                updates::add
            );

            assertEquals(1, updates.size());
            assertEquals("Working on java", ((TextContent) updates.getFirst().content().getFirst()).text());
            assertEquals(
                Map.of("stage", "partial", "toolCallId", "call-123", "params", Map.of("query", "java")),
                updates.getFirst().details()
            );

            assertEquals(1, result.content().size());
            assertEquals("Finished java", ((TextContent) result.content().getFirst()).text());
            assertEquals(
                Map.of("stage", "final", "toolCallId", "call-123", "params", Map.of("query", "java")),
                result.details()
            );
        }

        @Test
        void executeCanObserveCancelledSignal() throws Exception {
            var tool = new MockAgentTool();
            var token = new CancellationToken();
            token.cancel();

            var result = tool.execute("call-456", Map.of("query", "java"), token, partial -> fail("No update expected"));

            assertEquals("Cancelled", ((TextContent) result.content().getFirst()).text());
            assertEquals(Map.of("cancelled", true, "toolCallId", "call-456"), result.details());
        }
    }

    @Nested
    class CancellationTokenTests {

        @Test
        void startsNotCancelled() {
            var token = new CancellationToken();

            assertFalse(token.isCancelled());
        }

        @Test
        void cancelMarksTokenCancelled() {
            var token = new CancellationToken();

            token.cancel();

            assertTrue(token.isCancelled());
        }

        @Test
        void onCancelRunsCallbackWhenCancelledLater() {
            var token = new CancellationToken();
            var called = new AtomicBoolean(false);

            token.onCancel(() -> called.set(true));
            token.cancel();

            assertTrue(called.get());
        }

        @Test
        void onCancelRunsImmediatelyWhenAlreadyCancelled() {
            var token = new CancellationToken();
            token.cancel();
            var called = new AtomicBoolean(false);

            token.onCancel(() -> called.set(true));

            assertTrue(called.get());
        }

        @Test
        void cancelIsIdempotentAndInvokesCallbacksOnlyOnce() {
            var token = new CancellationToken();
            var calls = new ArrayList<String>();

            token.onCancel(() -> calls.add("first"));
            token.onCancel(() -> calls.add("second"));

            token.cancel();
            token.cancel();

            assertEquals(List.of("first", "second"), calls);
        }
    }

    private final class MockAgentTool implements AgentTool {

        @Override
        public String name() {
            return "mock_tool";
        }

        @Override
        public String label() {
            return "Mock Tool";
        }

        @Override
        public String description() {
            return "A mock tool used for AgentTool unit tests.";
        }

        @Override
        public com.fasterxml.jackson.databind.JsonNode parameters() {
            return mapper.createObjectNode()
                .put("type", "object")
                .<com.fasterxml.jackson.databind.node.ObjectNode>set("properties",
                    mapper.createObjectNode()
                        .set("query", mapper.createObjectNode().put("type", "string")))
                .set("required", mapper.createArrayNode().add("query"));
        }

        @Override
        public AgentToolResult execute(
            String toolCallId,
            Map<String, Object> params,
            CancellationToken signal,
            AgentToolUpdateCallback onUpdate
        ) {
            if (signal.isCancelled()) {
                return new AgentToolResult(
                    List.of(new TextContent("Cancelled")),
                    Map.of("cancelled", true, "toolCallId", toolCallId)
                );
            }

            onUpdate.onUpdate(new AgentToolResult(
                List.of(new TextContent("Working on " + params.get("query"))),
                Map.of("stage", "partial", "toolCallId", toolCallId, "params", params)
            ));

            return new AgentToolResult(
                List.of(new TextContent("Finished " + params.get("query"))),
                Map.of("stage", "final", "toolCallId", toolCallId, "params", params)
            );
        }
    }
}
