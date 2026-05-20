/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.campusclaw.ai.provider.google;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.campusclaw.ai.types.AssistantMessage;
import com.campusclaw.ai.types.ContentBlock;
import com.campusclaw.ai.types.Cost;
import com.campusclaw.ai.types.ImageContent;
import com.campusclaw.ai.types.Message;
import com.campusclaw.ai.types.StopReason;
import com.campusclaw.ai.types.TextContent;
import com.campusclaw.ai.types.ThinkingContent;
import com.campusclaw.ai.types.Tool;
import com.campusclaw.ai.types.ToolCall;
import com.campusclaw.ai.types.ToolResultMessage;
import com.campusclaw.ai.types.Usage;
import com.campusclaw.ai.types.UserMessage;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jakarta.annotation.Nullable;

/**
 * Shared utilities for Google Generative AI and Vertex AI providers.
 * Handles message/tool conversion between the unified types and Google's API format.
 *
 * @version [br_eCampusCore 25.1.0_Next, 2026/05/06]
 * @since [br_eCampusCore 25.1.0_Next]
 */
public final class GoogleShared {

    private static final Logger log = LoggerFactory.getLogger(GoogleShared.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private GoogleShared() {}

    /**
     * Converts unified messages to Google API content format.
     *
     * @param messages messages in the unified representation
     * @return Google {@code contents} array
     */
    public static ArrayNode convertMessages(List<Message> messages) {
        var contents = MAPPER.createArrayNode();
        for (var message : messages) {
            switch (message) {
                case UserMessage um -> contents.add(toUserContent(um));
                case AssistantMessage am -> contents.add(toAssistantContent(am));
                case ToolResultMessage trm -> contents.add(toToolResultContent(trm));
                default -> {
                    // skip unknown message types
                }
            }
        }
        return contents;
    }

    private static ObjectNode toUserContent(UserMessage um) {
        var content = MAPPER.createObjectNode();
        content.put("role", "user");
        var parts = MAPPER.createArrayNode();
        for (var block : um.content()) {
            if (block instanceof TextContent tc) {
                parts.add(MAPPER.createObjectNode().put("text", tc.text()));
            } else if (block instanceof ImageContent ic) {
                var inlineData = MAPPER.createObjectNode();
                inlineData.set(
                        "inlineData",
                        MAPPER.createObjectNode().put("mimeType", ic.mimeType()).put("data", ic.data()));
                parts.add(inlineData);
            }
        }
        content.set("parts", parts);
        return content;
    }

    private static ObjectNode toAssistantContent(AssistantMessage am) {
        var content = MAPPER.createObjectNode();
        content.put("role", "model");
        var parts = MAPPER.createArrayNode();
        for (var block : am.content()) {
            switch (block) {
                case TextContent tc -> parts.add(MAPPER.createObjectNode().put("text", tc.text()));
                case ThinkingContent tc -> {
                    if (tc.thinking() != null && !tc.thinking().isBlank()) {
                        parts.add(toThinkingPart(tc));
                    }
                }
                case ToolCall tc -> parts.add(toFunctionCallPart(tc));
                default -> {
                    // skip image etc.
                }
            }
        }
        content.set("parts", parts);
        return content;
    }

    private static ObjectNode toThinkingPart(ThinkingContent tc) {
        var part = MAPPER.createObjectNode();
        part.put("text", tc.thinking());
        part.put("thought", true);
        if (tc.thinkingSignature() != null) {
            part.put("thoughtSignature", tc.thinkingSignature());
        }
        return part;
    }

    private static ObjectNode toFunctionCallPart(ToolCall tc) {
        var fnCall = MAPPER.createObjectNode();
        var fc = MAPPER.createObjectNode();
        fc.put("name", tc.name());
        fc.set("args", MAPPER.valueToTree(tc.arguments()));
        fnCall.set("functionCall", fc);
        return fnCall;
    }

    private static ObjectNode toToolResultContent(ToolResultMessage trm) {
        var sb = new StringBuilder();
        for (var block : trm.content()) {
            if (block instanceof TextContent tc) {
                sb.append(tc.text());
            }
        }
        String resultText = sb.toString();
        var response = MAPPER.createObjectNode();
        if (trm.isError()) {
            response.put("error", resultText);
        } else {
            response.put("output", resultText);
        }
        var fr = MAPPER.createObjectNode();
        fr.put("name", trm.toolName());
        fr.set("response", response);
        var fnResp = MAPPER.createObjectNode();
        fnResp.set("functionResponse", fr);
        var content = MAPPER.createObjectNode();
        content.put("role", "user");
        content.set("parts", MAPPER.createArrayNode().add(fnResp));
        return content;
    }

    /**
     * Converts unified Tool definitions to Google function declarations.
     *
     * @param tools tool catalog, may be {@code null} or empty
     * @return Google {@code tools} array, or {@code null} when input is empty
     */
    public static ArrayNode convertTools(@Nullable List<Tool> tools) {
        if (tools == null || tools.isEmpty()) {
            return null;
        }
        var toolsArray = MAPPER.createArrayNode();
        var toolObj = MAPPER.createObjectNode();
        var functionDeclarations = MAPPER.createArrayNode();
        for (var tool : tools) {
            var fd = MAPPER.createObjectNode();
            fd.put("name", tool.name());
            fd.put("description", tool.description());
            if (tool.parameters() != null) {
                fd.set("parameters", MAPPER.valueToTree(tool.parameters()));
            }
            functionDeclarations.add(fd);
        }
        toolObj.set("functionDeclarations", functionDeclarations);
        toolsArray.add(toolObj);
        return toolsArray;
    }

    /**
     * Parses a streaming response chunk from Google's API.
     *
     * @param chunk raw streaming JSON chunk
     * @return the parsed blocks plus optional finish reason and usage
     */
    public static ParsedChunk parseChunk(JsonNode chunk) {
        var candidates = chunk.path("candidates");
        if (candidates.isEmpty() || !candidates.isArray()) {
            return new ParsedChunk(List.of(), null, null);
        }
        var candidate = candidates.get(0);
        var blocks = new ArrayList<ContentBlock>();
        var parts = candidate.path("content").path("parts");
        if (parts.isArray()) {
            for (var part : parts) {
                ContentBlock block = parsePart(part);
                if (block != null) {
                    blocks.add(block);
                }
            }
        }
        String finishReason =
                candidate.has("finishReason") ? candidate.get("finishReason").asText() : null;
        return new ParsedChunk(blocks, finishReason, parseUsage(chunk.path("usageMetadata")));
    }

    private static ContentBlock parsePart(JsonNode part) {
        if (part.has("text")) {
            if (part.path("thought").asBoolean(false)) {
                String thinkingSig = part.has("thoughtSignature")
                        ? part.get("thoughtSignature").asText()
                        : null;
                return new ThinkingContent(part.get("text").asText(), thinkingSig, false);
            }
            return new TextContent(part.get("text").asText());
        }
        if (part.has("functionCall")) {
            var fc = part.get("functionCall");
            String name = fc.get("name").asText();
            Map<String, Object> args = Map.of();
            if (fc.has("args")) {
                try {
                    args = MAPPER.convertValue(fc.get("args"), new TypeReference<>() {});
                } catch (Exception e) {
                    // fall back to empty args — server sent malformed JSON
                    log.warn("Google function-call args could not be parsed (name={}); using empty args", name, e);
                }
            }
            return new ToolCall(java.util.UUID.randomUUID().toString(), name, args);
        }
        return null;
    }

    private static Usage parseUsage(JsonNode usageNode) {
        if (usageNode.isMissingNode()) {
            return null;
        }
        int promptTokens = usageNode.path("promptTokenCount").asInt(0);
        int cachedTokens = usageNode.path("cachedContentTokenCount").asInt(0);
        int candidatesTokens = usageNode.path("candidatesTokenCount").asInt(0);
        int thoughtsTokens = usageNode.path("thoughtsTokenCount").asInt(0);
        int inputTokens = promptTokens - cachedTokens;
        int outputTokens = candidatesTokens + thoughtsTokens;
        int totalTokens = usageNode.path("totalTokenCount").asInt(inputTokens + outputTokens);
        return new Usage(inputTokens, outputTokens, cachedTokens, 0, totalTokens, Cost.empty());
    }

    /**
     * Maps Google finish reason to unified StopReason.
     *
     * @param reason the Google-supplied finish reason
     * @return the unified {@link StopReason}; defaults to {@link StopReason#STOP} when {@code null}
     */
    public static StopReason mapFinishReason(@Nullable String reason) {
        if (reason == null) {
            return StopReason.STOP;
        }
        return switch (reason) {
            case "STOP" -> StopReason.STOP;
            case "MAX_TOKENS" -> StopReason.LENGTH;
            case "SAFETY", "RECITATION", "OTHER" -> StopReason.ERROR;
            default -> StopReason.STOP;
        };
    }

    /**
     * Parsed result from a Google API streaming chunk.
     */
    public record ParsedChunk(List<ContentBlock> blocks, @Nullable String finishReason, @Nullable Usage usage) {}
}
