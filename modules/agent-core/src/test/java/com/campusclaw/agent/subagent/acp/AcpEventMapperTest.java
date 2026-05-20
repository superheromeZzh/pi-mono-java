/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.campusclaw.agent.subagent.acp;

import static org.assertj.core.api.Assertions.assertThat;

import com.campusclaw.agent.subagent.SubAgentEvent;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@code AcpEventMapper}, the translator from raw ACP JSON
 * {@code session/update} payloads to {@link SubAgentEvent} values. Exercises
 * each {@code sessionUpdate} discriminator value plus the catch-all status path.
 *
 * <p>Use reflection to reach the package-private static {@code toSubAgentEvent}
 * since the mapper is intentionally package-private.
 */
class AcpEventMapperTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static SubAgentEvent map(String json) throws Exception {
        JsonNode node = MAPPER.readTree(json);
        var m = AcpEventMapper.class.getDeclaredMethod("toSubAgentEvent", JsonNode.class, ObjectMapper.class);
        m.setAccessible(true);
        return (SubAgentEvent) m.invoke(null, node, MAPPER);
    }

    @Nested
    class MissingFields {

        @Test
        void nullUpdateReturnsNull() throws Exception {
            assertThat(map("null")).isNull();
        }

        @Test
        void missingSessionUpdateReturnsNull() throws Exception {
            assertThat(map("{\"foo\":1}")).isNull();
        }
    }

    @Nested
    class TextDeltas {

        @Test
        void agentMessageChunkBecomesOutputStream() throws Exception {
            SubAgentEvent ev = map("{\"sessionUpdate\":\"agent_message_chunk\"," + "\"content\":{\"text\":\"hello\"}}");
            assertThat(ev).isInstanceOf(SubAgentEvent.TextDelta.class);
            SubAgentEvent.TextDelta td = (SubAgentEvent.TextDelta) ev;
            assertThat(td.stream()).isEqualTo(SubAgentEvent.Stream.OUTPUT);
            assertThat(td.text()).isEqualTo("hello");
        }

        @Test
        void agentThoughtChunkBecomesThoughtStream() throws Exception {
            SubAgentEvent ev =
                    map("{\"sessionUpdate\":\"agent_thought_chunk\"," + "\"content\":{\"text\":\"thinking\"}}");
            SubAgentEvent.TextDelta td = (SubAgentEvent.TextDelta) ev;
            assertThat(td.stream()).isEqualTo(SubAgentEvent.Stream.THOUGHT);
            assertThat(td.text()).isEqualTo("thinking");
        }

        @Test
        void missingContentYieldsEmptyDelta() throws Exception {
            SubAgentEvent.TextDelta td = (SubAgentEvent.TextDelta) map("{\"sessionUpdate\":\"agent_message_chunk\"}");
            assertThat(td.text()).isEmpty();
        }

        @Test
        void missingTextFieldYieldsEmptyDelta() throws Exception {
            SubAgentEvent.TextDelta td =
                    (SubAgentEvent.TextDelta) map("{\"sessionUpdate\":\"agent_message_chunk\",\"content\":{}}");
            assertThat(td.text()).isEmpty();
        }
    }

    @Nested
    class ToolCalls {

        @Test
        void toolCallStarted() throws Exception {
            SubAgentEvent ev = map("{\"sessionUpdate\":\"tool_call\","
                    + "\"toolCallId\":\"c1\",\"name\":\"search\",\"title\":\"Search the web\"}");
            assertThat(ev).isInstanceOf(SubAgentEvent.ToolCall.class);
            SubAgentEvent.ToolCall tc = (SubAgentEvent.ToolCall) ev;
            assertThat(tc.toolCallId()).isEqualTo("c1");
            assertThat(tc.name()).isEqualTo("search");
            assertThat(tc.title()).isEqualTo("Search the web");
            assertThat(tc.status()).isEqualTo(SubAgentEvent.ToolCallStatus.STARTED);
        }

        @Test
        void toolCallUpdateMapsStatusVariants() throws Exception {
            assertThat(((SubAgentEvent.ToolCall)
                                    map(
                                            "{\"sessionUpdate\":\"tool_call_update\",\"toolCallId\":\"c\",\"status\":\"completed\"}"))
                            .status())
                    .isEqualTo(SubAgentEvent.ToolCallStatus.COMPLETED);
            assertThat(((SubAgentEvent.ToolCall)
                                    map(
                                            "{\"sessionUpdate\":\"tool_call_update\",\"toolCallId\":\"c\",\"status\":\"success\"}"))
                            .status())
                    .isEqualTo(SubAgentEvent.ToolCallStatus.COMPLETED);
            assertThat(((SubAgentEvent.ToolCall)
                                    map(
                                            "{\"sessionUpdate\":\"tool_call_update\",\"toolCallId\":\"c\",\"status\":\"failed\"}"))
                            .status())
                    .isEqualTo(SubAgentEvent.ToolCallStatus.FAILED);
            assertThat(((SubAgentEvent.ToolCall)
                                    map(
                                            "{\"sessionUpdate\":\"tool_call_update\",\"toolCallId\":\"c\",\"status\":\"error\"}"))
                            .status())
                    .isEqualTo(SubAgentEvent.ToolCallStatus.FAILED);
            assertThat(((SubAgentEvent.ToolCall)
                                    map(
                                            "{\"sessionUpdate\":\"tool_call_update\",\"toolCallId\":\"c\",\"status\":\"started\"}"))
                            .status())
                    .isEqualTo(SubAgentEvent.ToolCallStatus.STARTED);
            assertThat(((SubAgentEvent.ToolCall)
                                    map(
                                            "{\"sessionUpdate\":\"tool_call_update\",\"toolCallId\":\"c\",\"status\":\"pending\"}"))
                            .status())
                    .isEqualTo(SubAgentEvent.ToolCallStatus.STARTED);
            assertThat(((SubAgentEvent.ToolCall)
                                    map(
                                            "{\"sessionUpdate\":\"tool_call_update\",\"toolCallId\":\"c\",\"status\":\"working\"}"))
                            .status())
                    .isEqualTo(SubAgentEvent.ToolCallStatus.IN_PROGRESS);
        }

        @Test
        void toolCallUpdateWithoutStatusDefaultsToInProgress() throws Exception {
            SubAgentEvent.ToolCall tc =
                    (SubAgentEvent.ToolCall) map("{\"sessionUpdate\":\"tool_call_update\",\"toolCallId\":\"c\"}");
            assertThat(tc.status()).isEqualTo(SubAgentEvent.ToolCallStatus.IN_PROGRESS);
        }

        @Test
        void toolCallTitleDefaultsToName() throws Exception {
            SubAgentEvent.ToolCall tc = (SubAgentEvent.ToolCall)
                    map("{\"sessionUpdate\":\"tool_call\",\"toolCallId\":\"c1\",\"name\":\"search\"}");
            assertThat(tc.title()).isEqualTo("search");
        }
    }

    @Nested
    class StatusMessages {

        @Test
        void planUpdateBecomesStatus() throws Exception {
            SubAgentEvent ev = map("{\"sessionUpdate\":\"plan\",\"items\":[\"step1\"]}");
            assertThat(ev).isInstanceOf(SubAgentEvent.Status.class);
            SubAgentEvent.Status status = (SubAgentEvent.Status) ev;
            assertThat(status.summary()).isEqualTo("plan");
            assertThat(status.details()).containsKey("items");
        }

        @Test
        void availableCommandsBecomesStatus() throws Exception {
            SubAgentEvent ev = map("{\"sessionUpdate\":\"available_commands_update\",\"commands\":[]}");
            assertThat(((SubAgentEvent.Status) ev).summary()).isEqualTo("available_commands_update");
        }

        @Test
        void unknownTagFallsThroughToStatus() throws Exception {
            SubAgentEvent ev = map("{\"sessionUpdate\":\"mystery_event\",\"data\":42}");
            assertThat(((SubAgentEvent.Status) ev).summary()).isEqualTo("mystery_event");
        }
    }
}
