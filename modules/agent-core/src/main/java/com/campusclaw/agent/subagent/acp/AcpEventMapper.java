/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.campusclaw.agent.subagent.acp;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

import com.campusclaw.agent.subagent.SubAgentEvent;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Translates ACP {@code session/update} payloads into {@link SubAgentEvent} values.
 *
 * @version [br_eCampusCore 25.1.0_Next, 2026/05/12]
 * @since [br_eCampusCore 25.1.0_Next]
 */
final class AcpEventMapper {

    private AcpEventMapper() {}

    static SubAgentEvent toSubAgentEvent(JsonNode update, ObjectMapper mapper) {
        if (update == null || !update.has("sessionUpdate")) {
            return null;
        }
        String tag = update.get("sessionUpdate").asText();
        return switch (tag) {
            case AcpProtocol.UPDATE_AGENT_MESSAGE -> textDelta(update, SubAgentEvent.Stream.OUTPUT);
            case AcpProtocol.UPDATE_AGENT_THOUGHT -> textDelta(update, SubAgentEvent.Stream.THOUGHT);
            case AcpProtocol.UPDATE_TOOL_CALL -> toolCall(update, SubAgentEvent.ToolCallStatus.STARTED);
            case AcpProtocol.UPDATE_TOOL_CALL_UPDATE -> toolCall(update, statusFromUpdate(update));
            case AcpProtocol.UPDATE_PLAN, AcpProtocol.UPDATE_AVAILABLE_COMMANDS, AcpProtocol.UPDATE_CURRENT_MODE ->
                new SubAgentEvent.Status(tag, asMap(update, mapper));
            default -> new SubAgentEvent.Status(tag, asMap(update, mapper));
        };
    }

    private static SubAgentEvent.TextDelta textDelta(JsonNode update, SubAgentEvent.Stream stream) {
        JsonNode content = update.get("content");
        if (content == null || !content.has("text")) {
            return new SubAgentEvent.TextDelta(stream, "");
        }
        return new SubAgentEvent.TextDelta(stream, content.get("text").asText(""));
    }

    private static SubAgentEvent.ToolCall toolCall(JsonNode update, SubAgentEvent.ToolCallStatus status) {
        String id = update.has("toolCallId") ? update.get("toolCallId").asText("") : "";
        String name = update.has("name") ? update.get("name").asText("") : "";
        String title = update.has("title") ? update.get("title").asText("") : name;
        return new SubAgentEvent.ToolCall(id, name, title, status);
    }

    private static SubAgentEvent.ToolCallStatus statusFromUpdate(JsonNode update) {
        if (!update.has("status")) {
            return SubAgentEvent.ToolCallStatus.IN_PROGRESS;
        }
        return switch (update.get("status").asText("").toLowerCase(Locale.ROOT)) {
            case "completed", "success" -> SubAgentEvent.ToolCallStatus.COMPLETED;
            case "failed", "error" -> SubAgentEvent.ToolCallStatus.FAILED;
            case "started", "pending" -> SubAgentEvent.ToolCallStatus.STARTED;
            default -> SubAgentEvent.ToolCallStatus.IN_PROGRESS;
        };
    }

    private static Map<String, Object> asMap(JsonNode update, ObjectMapper mapper) {
        try {
            return mapper.convertValue(update, new com.fasterxml.jackson.core.type.TypeReference<>() {});
        } catch (RuntimeException ex) {
            var fallback = new LinkedHashMap<String, Object>();
            fallback.put("raw", update.toString());
            return fallback;
        }
    }
}
