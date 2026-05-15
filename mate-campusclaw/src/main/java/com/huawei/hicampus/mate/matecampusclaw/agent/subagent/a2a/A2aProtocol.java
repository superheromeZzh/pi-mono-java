/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.hicampus.mate.matecampusclaw.agent.subagent.a2a;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.huawei.hicampus.mate.matecampusclaw.agent.subagent.SubAgentEvent;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import reactor.core.publisher.Sinks;

/**
 * Wire helpers for the A2A JSON-RPC 2.0 envelope as accepted by Huawei mate-service.
 *
 * <p>Request shape (matches the
 * {@code POST /mate-service/v1/a2a/request/{agentName}} contract):
 *
 * <pre>{@code
 * {
 *   "id": "<uuid>",
 *   "jsonrpc": "2.0",
 *   "method": "SendMessage",
 *   "params": {
 *     "message": {
 *       "messageId": "<uuid>",
 *       "kind": "message",
 *       "role": "user",
 *       "parts": [{"text": "..."}]
 *     },
 *     "metadata": {"model": "..."}
 *   }
 * }
 * }</pre>
 *
 * <p>Response result extraction follows the A2A {@code SendMessageResponse} oneof:
 * {@code result.task} (Task payload) or {@code result.message} (Message payload). Within Task,
 * text is pulled from artifacts → status.message → top-level parts in that order. As a
 * permissive fallback the same paths are also tried directly off {@code result} for
 * implementations that omit the oneof discriminator.
 *
 * @version [br_eCampusCore 25.1.0_Next, 2026/05/15]
 * @since [br_eCampusCore 25.1.0_Next]
 */
public final class A2aProtocol {

    public static final String METHOD_SEND_MESSAGE = "SendMessage";
    public static final String JSONRPC_VERSION = "2.0";

    private static final Logger log = LoggerFactory.getLogger(A2aProtocol.class);

    private A2aProtocol() {}

    /**
     * Build the JSON-RPC envelope body sent to mate-service. Returned as a tree to keep field order
     * stable and avoid pulling Jackson annotations into the protocol record types.
     *
     * @param requestId JSON-RPC top-level id (UUID recommended)
     * @param messageId A2A message id (UUID recommended, distinct from {@code requestId})
     * @param userText user-supplied text content
     * @param model optional model identifier put into {@code params.metadata.model}; ignored when blank
     * @return JSON-RPC request body as a nested {@link Map}/{@link List} structure
     */
    public static Map<String, Object> buildSendMessageRequest(
            String requestId, String messageId, String userText, String model) {
        Map<String, Object> textPart = new LinkedHashMap<>();
        textPart.put("text", userText == null ? "" : userText);

        Map<String, Object> message = new LinkedHashMap<>();
        message.put("messageId", messageId);
        message.put("kind", "message");
        message.put("role", "user");
        message.put("parts", List.of(textPart));

        Map<String, Object> params = new LinkedHashMap<>();
        params.put("message", message);
        if (model != null && !model.isBlank()) {
            Map<String, Object> metadata = new LinkedHashMap<>();
            metadata.put("model", model);
            params.put("metadata", metadata);
        }

        Map<String, Object> envelope = new LinkedHashMap<>();
        envelope.put("id", requestId);
        envelope.put("jsonrpc", JSONRPC_VERSION);
        envelope.put("method", METHOD_SEND_MESSAGE);
        envelope.put("params", params);
        return envelope;
    }

    /**
     * Parse a JSON-RPC 2.0 response body and emit equivalent {@link SubAgentEvent}s into
     * {@code sink}. Emits one or more {@link SubAgentEvent.TextDelta} followed by a single
     * {@link SubAgentEvent.Done}, or a single {@link SubAgentEvent.Error} when the response carries
     * a JSON-RPC error or cannot be parsed.
     *
     * @param body raw response body
     * @param mapper Jackson mapper used to decode the body
     * @param sink reactive sink to publish events into
     */
    public static void parseAndEmit(String body, ObjectMapper mapper, Sinks.Many<SubAgentEvent> sink) {
        if (body == null || body.isBlank()) {
            sink.tryEmitNext(new SubAgentEvent.Error("A2A_EMPTY_RESPONSE", "empty response body", true));
            sink.tryEmitComplete();
            return;
        }
        JsonNode root;
        try {
            root = mapper.readTree(body);
        } catch (com.fasterxml.jackson.core.JsonProcessingException ex) {
            sink.tryEmitNext(new SubAgentEvent.Error(
                    "A2A_BAD_RESPONSE", "malformed JSON-RPC response: " + ex.getMessage(), false));
            sink.tryEmitComplete();
            return;
        }

        JsonNode errorNode = root.get("error");
        if (errorNode != null && !errorNode.isNull()) {
            String code = "A2A_RPC_" + errorNode.path("code").asText("UNKNOWN");
            String message = errorNode.path("message").asText("upstream error");
            sink.tryEmitNext(new SubAgentEvent.Error(code, message, false));
            sink.tryEmitComplete();
            return;
        }

        JsonNode result = root.get("result");
        if (result == null || result.isNull()) {
            sink.tryEmitNext(
                    new SubAgentEvent.Error("A2A_NO_RESULT", "response missing both result and error fields", false));
            sink.tryEmitComplete();
            return;
        }

        List<String> texts = extractTexts(result);
        if (texts.isEmpty()) {
            // Schema mismatch — surface the raw result so the user can read it AND so we have a
            // sample to extend extractTexts() against. Better than silently returning empty.
            String fallback = renderFallback(result, mapper);
            log.warn(
                    "a2a response did not match any known text-extraction path "
                            + "(tried result.task.*, result.message.parts, and bare result.* shapes); "
                            + "raw result: {}",
                    fallback);
            sink.tryEmitNext(new SubAgentEvent.TextDelta(SubAgentEvent.Stream.OUTPUT, fallback));
        } else {
            for (String text : texts) {
                sink.tryEmitNext(new SubAgentEvent.TextDelta(SubAgentEvent.Stream.OUTPUT, text));
            }
        }
        sink.tryEmitNext(new SubAgentEvent.Done(SubAgentEvent.StopReason.END_TURN));
        sink.tryEmitComplete();
    }

    private static String renderFallback(JsonNode result, ObjectMapper mapper) {
        try {
            return "[a2a: unrecognized response shape — raw result follows]\n"
                    + mapper.writerWithDefaultPrettyPrinter().writeValueAsString(result);
        } catch (com.fasterxml.jackson.core.JsonProcessingException ex) {
            return "[a2a: unrecognized response shape — failed to render raw result: " + ex.getMessage() + "]";
        }
    }

    private static List<String> extractTexts(JsonNode result) {
        List<String> out = new ArrayList<>();
        JsonNode taskNode = result.get("task");
        if (taskNode != null && !taskNode.isNull()) {
            collectFromTask(taskNode, out);
        }
        if (out.isEmpty()) {
            JsonNode messageNode = result.get("message");
            if (messageNode != null && !messageNode.isNull()) {
                collectPartTexts(messageNode.get("parts"), out);
            }
        }
        if (out.isEmpty()) {
            collectFromTask(result, out);
        }
        return out;
    }

    private static void collectFromTask(JsonNode task, List<String> out) {
        JsonNode artifacts = task.get("artifacts");
        if (artifacts != null && artifacts.isArray()) {
            for (JsonNode artifact : artifacts) {
                collectPartTexts(artifact.get("parts"), out);
            }
        }
        if (out.isEmpty()) {
            collectPartTexts(task.path("status").path("message").get("parts"), out);
        }
        if (out.isEmpty()) {
            collectPartTexts(task.get("parts"), out);
        }
    }

    private static void collectPartTexts(JsonNode parts, List<String> out) {
        if (parts == null || !parts.isArray()) {
            return;
        }
        for (JsonNode part : parts) {
            JsonNode textNode = part.get("text");
            if (textNode != null && textNode.isTextual()) {
                String text = textNode.asText();
                if (!text.isEmpty()) {
                    out.add(text);
                }
            }
        }
    }
}
