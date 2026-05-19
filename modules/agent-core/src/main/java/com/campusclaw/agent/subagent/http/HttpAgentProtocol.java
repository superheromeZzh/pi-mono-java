/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.campusclaw.agent.subagent.http;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

import com.campusclaw.agent.subagent.SubAgentEvent;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Wire helpers for the HTTP sub-agent backend. Defines the request shapes for {@code POST
 * /sessions}, the streaming ndJSON event line format, and the decoder back to
 * {@link SubAgentEvent}.
 *
 * <p>Event line shape:
 *
 * <pre>{@code
 * {"type":"text_delta","stream":"output","text":"..."}
 * {"type":"tool_call","toolCallId":"t1","name":"read","title":"...","status":"started"}
 * {"type":"status","summary":"...","details":{}}
 * {"type":"permission_request","requestId":"r1","toolName":"write","params":{}}
 * {"type":"done","stopReason":"end_turn"}
 * {"type":"error","code":"X","message":"...","retryable":false}
 * }</pre>
 *
 * @version [br_eCampusCore 25.1.0_Next, 2026/05/12]
 * @since [br_eCampusCore 25.1.0_Next]
 */
public final class HttpAgentProtocol {

    public static final String EVENT_TEXT_DELTA = "text_delta";
    public static final String EVENT_TOOL_CALL = "tool_call";
    public static final String EVENT_STATUS = "status";
    public static final String EVENT_PERMISSION_REQUEST = "permission_request";
    public static final String EVENT_DONE = "done";
    public static final String EVENT_ERROR = "error";

    private HttpAgentProtocol() {}

    /**
     * Body of {@code POST /sessions}.
     */
    public record NewSessionRequest(String parentAgentId, String cwd, String model, String thinking) {}

    /**
     * Reply to {@code POST /sessions}.
     */
    public record NewSessionResponse(String sessionId) {}

    /**
     * Body of {@code POST /sessions/{id}/prompt}.
     */
    public record PromptRequest(String task) {}

    /**
     * Body of {@code POST /sessions/{id}/cancel}.
     */
    public record CancelRequest(String reason) {}

    /**
     * Decode a single ndJSON event line.
     *
     * @param line one ndJSON-encoded event payload
     * @param mapper Jackson mapper used for decoding
     * @return the decoded {@link SubAgentEvent}, or {@code null} when the line is empty / unparseable
     */
    public static SubAgentEvent decodeEvent(String line, ObjectMapper mapper) {
        if (line == null) {
            return null;
        }
        String trimmed = line.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        try {
            JsonNode node = mapper.readTree(trimmed);
            String type = node.path("type").asText("");
            return switch (type) {
                case EVENT_TEXT_DELTA -> textDelta(node);
                case EVENT_TOOL_CALL -> toolCall(node);
                case EVENT_STATUS -> status(node, mapper);
                case EVENT_PERMISSION_REQUEST -> permission(node, mapper);
                case EVENT_DONE -> done(node);
                case EVENT_ERROR -> error(node);
                default -> null;
            };
        } catch (RuntimeException | java.io.IOException ex) {
            return new SubAgentEvent.Error("HTTP_BAD_EVENT", "malformed event line: " + ex.getMessage(), false);
        }
    }

    private static SubAgentEvent.TextDelta textDelta(JsonNode node) {
        SubAgentEvent.Stream stream =
                "thought".equalsIgnoreCase(node.path("stream").asText("output"))
                        ? SubAgentEvent.Stream.THOUGHT
                        : SubAgentEvent.Stream.OUTPUT;
        return new SubAgentEvent.TextDelta(stream, node.path("text").asText(""));
    }

    private static SubAgentEvent.ToolCall toolCall(JsonNode node) {
        SubAgentEvent.ToolCallStatus status =
                switch (node.path("status").asText("").toLowerCase(Locale.ROOT)) {
                    case "completed", "success" -> SubAgentEvent.ToolCallStatus.COMPLETED;
                    case "failed", "error" -> SubAgentEvent.ToolCallStatus.FAILED;
                    case "started", "pending" -> SubAgentEvent.ToolCallStatus.STARTED;
                    default -> SubAgentEvent.ToolCallStatus.IN_PROGRESS;
                };
        String name = node.path("name").asText("");
        return new SubAgentEvent.ToolCall(
                node.path("toolCallId").asText(""), name, node.path("title").asText(name), status);
    }

    private static SubAgentEvent.Status status(JsonNode node, ObjectMapper mapper) {
        Map<String, Object> details = node.has("details")
                ? mapper.convertValue(node.get("details"), new com.fasterxml.jackson.core.type.TypeReference<>() {})
                : new LinkedHashMap<>();
        return new SubAgentEvent.Status(node.path("summary").asText(""), details);
    }

    private static SubAgentEvent.PermissionRequest permission(JsonNode node, ObjectMapper mapper) {
        Map<String, Object> params = node.has("params")
                ? mapper.convertValue(node.get("params"), new com.fasterxml.jackson.core.type.TypeReference<>() {})
                : new LinkedHashMap<>();
        return new SubAgentEvent.PermissionRequest(
                node.path("requestId").asText(""), node.path("toolName").asText(""), params);
    }

    private static SubAgentEvent.Done done(JsonNode node) {
        String raw = node.path("stopReason").asText("end_turn");
        SubAgentEvent.StopReason reason =
                switch (raw.toLowerCase(Locale.ROOT)) {
                    case "max_tokens" -> SubAgentEvent.StopReason.MAX_TOKENS;
                    case "refusal" -> SubAgentEvent.StopReason.REFUSAL;
                    case "cancelled", "canceled" -> SubAgentEvent.StopReason.CANCELLED;
                    case "error" -> SubAgentEvent.StopReason.ERROR;
                    default -> SubAgentEvent.StopReason.END_TURN;
                };
        return new SubAgentEvent.Done(reason);
    }

    private static SubAgentEvent.Error error(JsonNode node) {
        return new SubAgentEvent.Error(
                node.path("code").asText("HTTP_ERROR"),
                node.path("message").asText(""),
                node.path("retryable").asBoolean(false));
    }
}
