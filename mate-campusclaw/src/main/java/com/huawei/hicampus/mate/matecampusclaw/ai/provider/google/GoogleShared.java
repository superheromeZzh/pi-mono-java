package com.huawei.hicampus.mate.matecampusclaw.ai.provider.google;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.huawei.hicampus.mate.matecampusclaw.ai.types.*;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;

import jakarta.annotation.Nullable;

/**
 * Shared utilities for Google Generative AI and Vertex AI providers.
 * Handles message/tool conversion between the unified types and Google's API format.
 */
public final class GoogleShared {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private GoogleShared() {}

    /**
     * Converts unified messages to Google API content format.
     */
    public static ArrayNode convertMessages(List<Message> messages) {
        var contents = MAPPER.createArrayNode();
        for (var message : messages) {
            switch (message) {
                case UserMessage um -> {
                    var content = MAPPER.createObjectNode();
                    content.put("role", "user");
                    var parts = MAPPER.createArrayNode();
                    for (var block : um.content()) {
                        if (block instanceof TextContent tc) {
                            parts.add(MAPPER.createObjectNode().put("text", tc.text()));
                        } else if (block instanceof ImageContent ic) {
                            var inlineData = MAPPER.createObjectNode();
                            var data = MAPPER.createObjectNode();
                            data.put("mimeType", ic.mimeType());
                            data.put("data", ic.data());
                            inlineData.set("inlineData", data);
                            parts.add(inlineData);
                        }
                    }
                    content.set("parts", parts);
                    contents.add(content);
                }
                case AssistantMessage am -> {
                    var content = MAPPER.createObjectNode();
                    content.put("role", "model");
                    var parts = MAPPER.createArrayNode();
                    for (var block : am.content()) {
                        switch (block) {
                            case TextContent tc -> parts.add(MAPPER.createObjectNode().put("text", tc.text()));
                            case ThinkingContent tc -> {
                                if (tc.thinking() != null && !tc.thinking().isBlank()) {
                                    var part = MAPPER.createObjectNode();
                                    part.put("text", tc.thinking());
                                    part.put("thought", true);
                                    if (tc.thinkingSignature() != null) {
                                        part.put("thoughtSignature", tc.thinkingSignature());
                                    }
                                    parts.add(part);
                                }
                            }
                            case ToolCall tc -> {
                                var fnCall = MAPPER.createObjectNode();
                                var fc = MAPPER.createObjectNode();
                                fc.put("name", tc.name());
                                fc.set("args", MAPPER.valueToTree(tc.arguments()));
                                fnCall.set("functionCall", fc);
                                parts.add(fnCall);
                            }
                            default -> {} // Skip image etc.
                        }
                    }
                    content.set("parts", parts);
                    contents.add(content);
                }
                case ToolResultMessage trm -> {
                    var content = MAPPER.createObjectNode();
                    content.put("role", "user");
                    var parts = MAPPER.createArrayNode();
                    var fnResp = MAPPER.createObjectNode();
                    var fr = MAPPER.createObjectNode();
                    fr.put("name", trm.toolName());
                    var response = MAPPER.createObjectNode();
                    var sb = new StringBuilder();
                    for (var block : trm.content()) {
                        if (block instanceof TextContent tc) sb.append(tc.text());
                    }
                    String resultText = sb.toString();
                    if (trm.isError()) {
                        response.put("error", resultText);
                    } else {
                        response.put("output", resultText);
                    }
                    fr.set("response", response);
                    fnResp.set("functionResponse", fr);
                    parts.add(fnResp);
                    content.set("parts", parts);
                    contents.add(content);
                }
                default -> {} // Skip unknown message types
            }
        }
        return contents;
    }

    /**
     * Converts unified Tool definitions to Google function declarations.
     */
    public static ArrayNode convertTools(@Nullable List<Tool> tools) {
        if (tools == null || tools.isEmpty()) return null;
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
     */
    public static ParsedChunk parseChunk(JsonNode chunk) {
        var candidates = chunk.path("candidates");
        if (candidates.isEmpty() || !candidates.isArray()) {
            return new ParsedChunk(List.of(), null, null);
        }
        var candidate = candidates.get(0);
        var content = candidate.path("content");
        var parts = content.path("parts");
        var blocks = new ArrayList<ContentBlock>();

        if (parts.isArray()) {
            for (var part : parts) {
                if (part.has("text")) {
                    boolean isThinking = part.path("thought").asBoolean(false);
                    if (isThinking) {
                        String thinkingSig = part.has("thoughtSignature")
                            ? part.get("thoughtSignature").asText() : null;
                        blocks.add(new ThinkingContent(part.get("text").asText(), thinkingSig, false));
                    } else {
                        blocks.add(new TextContent(part.get("text").asText()));
                    }
                } else if (part.has("functionCall")) {
                    var fc = part.get("functionCall");
                    String name = fc.get("name").asText();
                    Map<String, Object> args = Map.of();
                    if (fc.has("args")) {
                        try {
                            args = MAPPER.convertValue(fc.get("args"),
                                new TypeReference<>() {});
                        } catch (Exception ignored) {}
                    }
                    blocks.add(new ToolCall(java.util.UUID.randomUUID().toString(), name, args));
                }
            }
        }

        String finishReason = candidate.has("finishReason")
            ? candidate.get("finishReason").asText() : null;

        Usage usage = null;
        var usageNode = chunk.path("usageMetadata");
        if (!usageNode.isMissingNode()) {
            int promptTokens = usageNode.path("promptTokenCount").asInt(0);
            int cachedTokens = usageNode.path("cachedContentTokenCount").asInt(0);
            int candidatesTokens = usageNode.path("candidatesTokenCount").asInt(0);
            int thoughtsTokens = usageNode.path("thoughtsTokenCount").asInt(0);
            int inputTokens = promptTokens - cachedTokens;
            int outputTokens = candidatesTokens + thoughtsTokens;
            int totalTokens = usageNode.path("totalTokenCount").asInt(inputTokens + outputTokens);
            usage = new Usage(inputTokens, outputTokens, cachedTokens, 0, totalTokens, Cost.empty());
        }

        return new ParsedChunk(blocks, finishReason, usage);
    }

    /**
     * Maps Google finish reason to unified StopReason.
     */
    public static StopReason mapFinishReason(@Nullable String reason) {
        if (reason == null) return StopReason.STOP;
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
