package com.campusclaw.ai.provider.openai;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.campusclaw.ai.provider.ApiProvider;
import com.campusclaw.ai.stream.AssistantMessageEvent;
import com.campusclaw.ai.stream.AssistantMessageEventStream;
import com.campusclaw.ai.types.Api;
import com.campusclaw.ai.types.AssistantMessage;
import com.campusclaw.ai.types.ContentBlock;
import com.campusclaw.ai.types.Context;
import com.campusclaw.ai.types.Cost;
import com.campusclaw.ai.types.ImageContent;
import com.campusclaw.ai.types.Message;
import com.campusclaw.ai.types.Model;
import com.campusclaw.ai.types.ModelCost;
import com.campusclaw.ai.types.Provider;
import com.campusclaw.ai.types.SimpleStreamOptions;
import com.campusclaw.ai.types.StopReason;
import com.campusclaw.ai.types.StreamOptions;
import com.campusclaw.ai.types.TextContent;
import com.campusclaw.ai.types.ThinkingContent;
import com.campusclaw.ai.types.ThinkingLevel;
import com.campusclaw.ai.types.Tool;
import com.campusclaw.ai.types.ToolCall;
import com.campusclaw.ai.types.ToolResultMessage;
import com.campusclaw.ai.types.Usage;
import com.campusclaw.ai.types.UserMessage;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.openai.client.OpenAIClient;
import com.openai.client.okhttp.OpenAIOkHttpClient;
import com.openai.core.http.StreamResponse;
import com.openai.models.FunctionDefinition;
import com.openai.models.FunctionParameters;
import com.openai.models.chat.completions.ChatCompletionAssistantMessageParam;
import com.openai.models.chat.completions.ChatCompletionChunk;
import com.openai.models.chat.completions.ChatCompletionContentPart;
import com.openai.models.chat.completions.ChatCompletionContentPartImage;
import com.openai.models.chat.completions.ChatCompletionContentPartText;
import com.openai.models.chat.completions.ChatCompletionCreateParams;
import com.openai.models.chat.completions.ChatCompletionFunctionTool;
import com.openai.models.chat.completions.ChatCompletionMessageFunctionToolCall;
import com.openai.models.chat.completions.ChatCompletionMessageParam;
import com.openai.models.chat.completions.ChatCompletionMessageToolCall;
import com.openai.models.chat.completions.ChatCompletionStreamOptions;
import com.openai.models.chat.completions.ChatCompletionSystemMessageParam;
import com.openai.models.chat.completions.ChatCompletionTool;
import com.openai.models.chat.completions.ChatCompletionToolMessageParam;
import com.openai.models.chat.completions.ChatCompletionUserMessageParam;
import com.openai.models.completions.CompletionUsage;

import org.springframework.stereotype.Component;

import jakarta.annotation.Nullable;

/**
 * {@link ApiProvider} implementation for the OpenAI Chat Completions API.
 *
 * <p>Uses the official {@code com.openai:openai-java} SDK for streaming
 * requests, mapping OpenAI SSE chunks to the unified
 * {@link AssistantMessageEvent} protocol.
 */
@Component
public class OpenAICompletionsProvider implements ApiProvider {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String ENV_API_KEY = "OPENAI_API_KEY";

    /** Resolve API key based on the model's provider. */
    private static String resolveApiKeyForProvider(Model model) {
        // Check model-embedded API key first (for custom models)
        if (model.apiKey() != null && !model.apiKey().isBlank()) return model.apiKey();
        // Check provider-specific env var first
        String providerKey = switch (model.provider()) {
            case ZAI -> System.getenv("ZAI_API_KEY");
            case XAI -> System.getenv("XAI_API_KEY");
            case GROQ -> System.getenv("GROQ_API_KEY");
            case CEREBRAS -> System.getenv("CEREBRAS_API_KEY");
            case OPENROUTER -> System.getenv("OPENROUTER_API_KEY");
            case HUGGINGFACE -> System.getenv("HF_TOKEN");
            case GITHUB_COPILOT -> System.getenv("COPILOT_GITHUB_TOKEN");
            default -> null;
        };
        if (providerKey != null && !providerKey.isBlank()) return providerKey;
        // Fallback to OPENAI_API_KEY
        return System.getenv(ENV_API_KEY);
    }

    @Override
    public Api getApi() {
        return Api.OPENAI_COMPLETIONS;
    }

    @Override
    public AssistantMessageEventStream stream(
            Model model, Context context, @Nullable StreamOptions options) {
        return doStream(model, context,
                options != null ? options.apiKey() : null,
                options != null ? options.maxTokens() : null,
                options != null ? options.temperature() : null,
                null);
    }

    @Override
    public AssistantMessageEventStream streamSimple(
            Model model, Context context, @Nullable SimpleStreamOptions options) {
        return doStream(model, context,
                options != null ? options.apiKey() : null,
                options != null ? options.maxTokens() : null,
                options != null ? options.temperature() : null,
                options != null ? options.reasoning() : null);
    }

    private AssistantMessageEventStream doStream(
            Model model, Context context,
            @Nullable String apiKey,
            @Nullable Integer maxTokens,
            @Nullable Double temperature,
            @Nullable ThinkingLevel reasoning) {

        var eventStream = new AssistantMessageEventStream();

        Thread.ofVirtual().start(() -> {
            try {
                executeStream(model, context, apiKey, maxTokens, temperature, reasoning, eventStream);
            } catch (Exception e) {
                eventStream.error(e);
            }
        });

        return eventStream;
    }

    void executeStream(
            Model model, Context context,
            @Nullable String apiKey,
            @Nullable Integer maxTokens,
            @Nullable Double temperature,
            @Nullable ThinkingLevel reasoning,
            AssistantMessageEventStream eventStream) {

        String resolvedApiKey = apiKey != null ? apiKey : resolveApiKeyForProvider(model);
        if (resolvedApiKey == null || resolvedApiKey.isBlank()) {
            String envHint = switch (model.provider()) {
                case ZAI -> "ZAI_API_KEY";
                case XAI -> "XAI_API_KEY";
                case GROQ -> "GROQ_API_KEY";
                case CEREBRAS -> "CEREBRAS_API_KEY";
                case OPENROUTER -> "OPENROUTER_API_KEY";
                case HUGGINGFACE -> "HF_TOKEN";
                case GITHUB_COPILOT -> "COPILOT_GITHUB_TOKEN";
                default -> "OPENAI_API_KEY";
            };
            eventStream.error(new IllegalStateException(
                    "API key not found for " + model.provider().value() + ". Set " + envHint + " or pass via StreamOptions."));
            return;
        }

        OpenAIClient client = buildClient(resolvedApiKey, model.baseUrl());

        try {
            ChatCompletionCreateParams params = buildParams(model, context, maxTokens, temperature, reasoning);
            processStream(client, params, model, eventStream);
        } catch (Exception e) {
            eventStream.error(e);
        } finally {
            client.close();
        }
    }

    OpenAIClient buildClient(String apiKey, String baseUrl) {
        var builder = OpenAIOkHttpClient.builder()
                .apiKey(apiKey)
                .timeout(com.openai.core.Timeout.builder()
                        .connect(java.time.Duration.ofSeconds(15))
                        .read(java.time.Duration.ofMinutes(10))
                        .write(java.time.Duration.ofSeconds(30))
                        .build());
        if (baseUrl != null && !baseUrl.isBlank()) {
            builder.baseUrl(baseUrl);
        }
        return builder.build();
    }

    ChatCompletionCreateParams buildParams(
            Model model, Context context,
            @Nullable Integer maxTokens,
            @Nullable Double temperature,
            @Nullable ThinkingLevel reasoning) {

        int resolvedMaxTokens = maxTokens != null ? maxTokens
                : Math.min(model.maxTokens(), 32000);

        // Build messages list with system prompt first
        var messages = new ArrayList<ChatCompletionMessageParam>();
        if (context.systemPrompt() != null && !context.systemPrompt().isBlank()) {
            messages.add(ChatCompletionMessageParam.ofSystem(
                    ChatCompletionSystemMessageParam.builder()
                            .content(context.systemPrompt())
                            .build()));
        }
        messages.addAll(convertMessages(context.messages()));

        var builder = ChatCompletionCreateParams.builder()
                .model(model.id())
                .maxCompletionTokens((long) resolvedMaxTokens)
                .messages(messages)
                .streamOptions(ChatCompletionStreamOptions.builder()
                        .includeUsage(true).build());

        // Tools
        if (context.tools() != null && !context.tools().isEmpty()) {
            builder.tools(convertTools(context.tools()));
        }

        // Temperature
        if (temperature != null) {
            builder.temperature(temperature);
        }

        // Provider-specific reasoning/thinking support
        if (reasoning != null && reasoning != ThinkingLevel.OFF && model.reasoning()) {
            if ("zai".equals(model.thinkingFormat())) {
                // ZAI uses enable_thinking boolean
                builder.putAdditionalBodyProperty("enable_thinking",
                        com.openai.core.JsonValue.from(true));
            } else if (!isProviderWithCustomThinking(model.provider())) {
                // Standard OpenAI-compatible reasoning_effort (OpenAI, xAI, Groq, etc.)
                String effort = mapReasoningEffort(reasoning);
                builder.putAdditionalBodyProperty("reasoning_effort",
                        com.openai.core.JsonValue.from(effort));
            }
        }

        return builder.build();
    }

    private void processStream(
            OpenAIClient client, ChatCompletionCreateParams params,
            Model model, AssistantMessageEventStream eventStream) {

        var contentBlocks = new ArrayList<ContentBlock>();
        var accumulatedUsage = new long[]{0, 0, 0, 0}; // input, output, cacheRead, cacheWrite
        String[] responseId = {null};
        StopReason[] stopReason = {null};

        // Accumulators for streaming text and tool call arguments
        var textAccumulator = new StringBuilder();
        var thinkingAccumulator = new StringBuilder();
        var toolAccumulators = new HashMap<Integer, ToolCallAccumulator>();
        boolean[] textBlockStarted = {false};
        boolean[] thinkingBlockStarted = {false};

        try (StreamResponse<ChatCompletionChunk> response =
                     client.chat().completions().createStreaming(params)) {

            response.stream().forEach(chunk -> {
                responseId[0] = chunk.id();

                // Extract usage from the final chunk
                chunk.usage().ifPresent(usage ->
                        parseUsage(usage, accumulatedUsage));

                if (chunk.choices().isEmpty()) {
                    return;
                }

                var choice = chunk.choices().get(0);

                // Check finish reason
                choice.finishReason().ifPresent(fr ->
                        stopReason[0] = mapFinishReason(fr.asString()));

                var delta = choice.delta();

                // Handle text content
                delta.content().ifPresent(text -> {
                    if (!text.isEmpty()) {
                        if (!textBlockStarted[0]) {
                            textBlockStarted[0] = true;
                            contentBlocks.add(new TextContent("", null));
                            int idx = contentBlocks.size() - 1;
                            eventStream.push(new AssistantMessageEvent.TextStartEvent(idx,
                                    buildPartialMessage(model, responseId[0],
                                            contentBlocks, accumulatedUsage, null)));
                        }
                        textAccumulator.append(text);
                        int idx = contentBlocks.size() - 1;
                        contentBlocks.set(idx, new TextContent(textAccumulator.toString(), null));
                        eventStream.push(new AssistantMessageEvent.TextDeltaEvent(idx, text,
                                buildPartialMessage(model, responseId[0],
                                        contentBlocks, accumulatedUsage, null)));
                    }
                });

                // Handle reasoning/thinking content from additional properties
                // Various providers use different fields: reasoning_content, reasoning, reasoning_text
                String reasoningDelta = extractReasoningDelta(delta);
                if (reasoningDelta != null && !reasoningDelta.isEmpty()) {
                    if (!thinkingBlockStarted[0]) {
                        thinkingBlockStarted[0] = true;
                        contentBlocks.add(new ThinkingContent("", null, false));
                        int idx = contentBlocks.size() - 1;
                        eventStream.push(new AssistantMessageEvent.ThinkingStartEvent(idx,
                                buildPartialMessage(model, responseId[0],
                                        contentBlocks, accumulatedUsage, null)));
                    }
                    thinkingAccumulator.append(reasoningDelta);
                    int idx = contentBlocks.size() - 1;
                    contentBlocks.set(idx, new ThinkingContent(thinkingAccumulator.toString(), null, false));
                    eventStream.push(new AssistantMessageEvent.ThinkingDeltaEvent(idx, reasoningDelta,
                            buildPartialMessage(model, responseId[0],
                                    contentBlocks, accumulatedUsage, null)));
                }

                // Handle tool calls
                delta.toolCalls().ifPresent(toolCalls -> {
                    for (var tc : toolCalls) {
                        int toolIndex = (int) tc.index();
                        var acc = toolAccumulators.computeIfAbsent(toolIndex,
                                k -> new ToolCallAccumulator());

                        // Capture id and name from the first chunk
                        tc.id().ifPresent(id -> acc.id = id);
                        tc.function().ifPresent(fn ->
                                fn.name().ifPresent(name -> acc.name = name));

                        // Extract arguments delta before appending
                        String[] argsDelta = {null};
                        tc.function().ifPresent(fn ->
                                fn.arguments().ifPresent(a -> {
                                    if (!a.isEmpty()) {
                                        argsDelta[0] = a;
                                        acc.arguments.append(a);
                                    }
                                }));

                        // If this is the first time we see this tool index, start a block
                        if (!acc.started) {
                            acc.started = true;
                            // Finish any open text block first
                            if (textBlockStarted[0]) {
                                int textIdx = findTextBlockIndex(contentBlocks);
                                if (textIdx >= 0) {
                                    eventStream.push(new AssistantMessageEvent.TextEndEvent(
                                            textIdx, textAccumulator.toString(),
                                            buildPartialMessage(model, responseId[0],
                                                    contentBlocks, accumulatedUsage, null)));
                                }
                                textBlockStarted[0] = false;
                            }
                            contentBlocks.add(new ToolCall(
                                    acc.id != null ? acc.id : "",
                                    acc.name != null ? acc.name : "",
                                    Map.of(), null));
                            acc.contentIndex = contentBlocks.size() - 1;
                            eventStream.push(new AssistantMessageEvent.ToolCallStartEvent(
                                    acc.contentIndex,
                                    buildPartialMessage(model, responseId[0],
                                            contentBlocks, accumulatedUsage, null)));
                        }

                        // Emit argument delta (for both first and subsequent chunks)
                        if (argsDelta[0] != null) {
                            eventStream.push(new AssistantMessageEvent.ToolCallDeltaEvent(
                                    acc.contentIndex, argsDelta[0],
                                    buildPartialMessage(model, responseId[0],
                                            contentBlocks, accumulatedUsage, null)));
                        }
                    }
                });
            });
        }

        // Finish open thinking block
        if (thinkingBlockStarted[0]) {
            int thinkIdx = findBlockIndex(contentBlocks, ThinkingContent.class);
            if (thinkIdx >= 0) {
                contentBlocks.set(thinkIdx, new ThinkingContent(thinkingAccumulator.toString(), null, false));
                eventStream.push(new AssistantMessageEvent.ThinkingEndEvent(
                        thinkIdx, thinkingAccumulator.toString(),
                        buildPartialMessage(model, responseId[0],
                                contentBlocks, accumulatedUsage, null)));
            }
        }

        // Finish open text block
        if (textBlockStarted[0]) {
            int textIdx = findTextBlockIndex(contentBlocks);
            if (textIdx >= 0) {
                contentBlocks.set(textIdx, new TextContent(textAccumulator.toString(), null));
                eventStream.push(new AssistantMessageEvent.TextEndEvent(
                        textIdx, textAccumulator.toString(),
                        buildPartialMessage(model, responseId[0],
                                contentBlocks, accumulatedUsage, null)));
            }
        }

        // Finish open tool call blocks
        for (var entry : toolAccumulators.entrySet()) {
            var acc = entry.getValue();
            if (acc.started && acc.contentIndex >= 0 && acc.contentIndex < contentBlocks.size()) {
                Map<String, Object> args = parseToolArguments(acc.arguments.toString());
                var toolCall = new ToolCall(
                        acc.id != null ? acc.id : "",
                        acc.name != null ? acc.name : "",
                        args, null);
                contentBlocks.set(acc.contentIndex, toolCall);
                eventStream.push(new AssistantMessageEvent.ToolCallEndEvent(
                        acc.contentIndex, toolCall,
                        buildPartialMessage(model, responseId[0],
                                contentBlocks, accumulatedUsage, null)));
            }
        }

        // Emit final done event
        var finalStopReason = stopReason[0] != null ? stopReason[0] : StopReason.STOP;
        var finalMessage = buildPartialMessage(model, responseId[0],
                contentBlocks, accumulatedUsage, finalStopReason);
        eventStream.pushDone(finalStopReason, finalMessage);
    }

    // -- Message conversion --

    static List<ChatCompletionMessageParam> convertMessages(List<Message> messages) {
        List<ChatCompletionMessageParam> result = new ArrayList<>();
        for (Message message : messages) {
            if (message instanceof UserMessage um) {
                result.add(convertUserMessage(um));
            } else if (message instanceof AssistantMessage am) {
                result.add(convertAssistantMessage(am));
            } else if (message instanceof ToolResultMessage tr) {
                result.add(convertToolResult(tr));
            }
        }
        return result;
    }

    private static ChatCompletionMessageParam convertUserMessage(UserMessage um) {
        List<ChatCompletionContentPart> parts = new ArrayList<>();
        for (ContentBlock cb : um.content()) {
            if (cb instanceof TextContent tc) {
                parts.add(ChatCompletionContentPart.ofText(
                        ChatCompletionContentPartText.builder()
                                .text(tc.text()).build()));
            } else if (cb instanceof ImageContent ic) {
                parts.add(ChatCompletionContentPart.ofImageUrl(
                        ChatCompletionContentPartImage.builder()
                                .imageUrl(ChatCompletionContentPartImage.ImageUrl.builder()
                                        .url("data:" + ic.mimeType() + ";base64," + ic.data())
                                        .build())
                                .build()));
            }
        }
        return ChatCompletionMessageParam.ofUser(
                ChatCompletionUserMessageParam.builder()
                        .content(ChatCompletionUserMessageParam.Content
                                .ofArrayOfContentParts(parts))
                        .build());
    }

    private static ChatCompletionMessageParam convertAssistantMessage(AssistantMessage am) {
        // Build text content as a plain string (OpenAI expects string, not array)
        StringBuilder textContent = new StringBuilder();
        StringBuilder thinkingText = new StringBuilder();
        List<ChatCompletionMessageToolCall> toolCalls = new ArrayList<>();

        for (ContentBlock cb : am.content()) {
            if (cb instanceof TextContent tc) {
                textContent.append(tc.text());
            } else if (cb instanceof ThinkingContent tc) {
                // Preserve thinking as plain text (no tags to avoid model mimicking)
                if (tc.thinking() != null && !tc.thinking().isBlank()) {
                    thinkingText.append(tc.thinking());
                }
            } else if (cb instanceof ToolCall tc) {
                toolCalls.add(ChatCompletionMessageToolCall.ofFunction(
                        ChatCompletionMessageFunctionToolCall.builder()
                                .id(tc.id())
                                .function(ChatCompletionMessageFunctionToolCall.Function.builder()
                                        .name(tc.name())
                                        .arguments(serializeArguments(tc.arguments()))
                                        .build())
                                .build()));
            }
        }

        // Prepend thinking to text content for replay
        if (!thinkingText.isEmpty()) {
            textContent.insert(0, thinkingText.toString() + "\n\n");
        }

        var builder = ChatCompletionAssistantMessageParam.builder();
        if (!textContent.isEmpty()) {
            builder.content(textContent.toString());
        }
        if (!toolCalls.isEmpty()) {
            builder.toolCalls(toolCalls);
        }

        return ChatCompletionMessageParam.ofAssistant(builder.build());
    }

    private static ChatCompletionMessageParam convertToolResult(ToolResultMessage tr) {
        StringBuilder textContent = new StringBuilder();
        for (ContentBlock cb : tr.content()) {
            if (cb instanceof TextContent tc) {
                textContent.append(tc.text());
            }
        }

        return ChatCompletionMessageParam.ofTool(
                ChatCompletionToolMessageParam.builder()
                        .toolCallId(tr.toolCallId())
                        .content(textContent.toString())
                        .build());
    }

    // -- Tool conversion --

    static List<ChatCompletionTool> convertTools(List<Tool> tools) {
        List<ChatCompletionTool> result = new ArrayList<>();
        for (Tool tool : tools) {
            var paramsBuilder = FunctionParameters.builder();
            if (tool.parameters() != null) {
                Map<String, Object> schemaMap = nodeToMap(tool.parameters());
                for (var entry : schemaMap.entrySet()) {
                    paramsBuilder.putAdditionalProperty(entry.getKey(),
                            com.openai.core.JsonValue.from(entry.getValue()));
                }
            }

            var functionDef = FunctionDefinition.builder()
                    .name(tool.name())
                    .description(tool.description())
                    .parameters(paramsBuilder.build())
                    .strict(false)
                    .build();

            result.add(ChatCompletionTool.ofFunction(
                    ChatCompletionFunctionTool.builder()
                            .function(functionDef)
                            .build()));
        }
        return result;
    }

    // -- Finish reason mapping --

    static StopReason mapFinishReason(String reason) {
        if (reason == null) return StopReason.STOP;
        return switch (reason) {
            case "stop", "end" -> StopReason.STOP;
            case "length" -> StopReason.LENGTH;
            case "tool_calls", "function_call" -> StopReason.TOOL_USE;
            case "content_filter" -> StopReason.ERROR;
            default -> StopReason.STOP;
        };
    }

    // -- Usage parsing --

    static void parseUsage(CompletionUsage usage, long[] accumulated) {
        long cachedTokens = usage.promptTokensDetails()
                .flatMap(d -> d.cachedTokens())
                .orElse(0L);

        // OpenAI includes cached tokens in prompt_tokens, so subtract
        long inputTokens = usage.promptTokens() - cachedTokens;
        accumulated[0] = Math.max(inputTokens, 0);
        accumulated[1] = usage.completionTokens();
        accumulated[2] = cachedTokens;
        // cacheWrite is 0 for OpenAI
        accumulated[3] = 0;
    }

    // -- Utility methods --

    private AssistantMessage buildPartialMessage(
            Model model, String responseId,
            List<ContentBlock> contentBlocks, long[] usage,
            @Nullable StopReason stopReason) {

        var piUsage = new Usage(
                (int) usage[0], (int) usage[1],
                (int) usage[2], (int) usage[3],
                (int) (usage[0] + usage[1] + usage[2]),
                computeCost(model.cost(), usage));

        return new AssistantMessage(
                List.copyOf(contentBlocks),
                Api.OPENAI_COMPLETIONS.value(),
                model.provider().value(),
                model.id(),
                responseId,
                piUsage,
                stopReason != null ? stopReason : StopReason.STOP,
                null,
                System.currentTimeMillis());
    }

    static Cost computeCost(ModelCost modelCost, long[] usage) {
        double inputCost = usage[0] * modelCost.input() / 1_000_000.0;
        double outputCost = usage[1] * modelCost.output() / 1_000_000.0;
        double cacheReadCost = usage[2] * modelCost.cacheRead() / 1_000_000.0;
        double cacheWriteCost = usage[3] * modelCost.cacheWrite() / 1_000_000.0;
        return new Cost(inputCost, outputCost, cacheReadCost, cacheWriteCost,
                inputCost + outputCost + cacheReadCost + cacheWriteCost);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> parseToolArguments(String json) {
        if (json == null || json.isBlank()) return Map.of();
        try {
            return MAPPER.readValue(json, new TypeReference<Map<String, Object>>() {});
        } catch (Exception e) {
            return Map.of();
        }
    }

    private static String serializeArguments(Map<String, Object> args) {
        if (args == null || args.isEmpty()) return "{}";
        try {
            return MAPPER.writeValueAsString(args);
        } catch (Exception e) {
            return "{}";
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> nodeToMap(JsonNode node) {
        try {
            return MAPPER.treeToValue(node, Map.class);
        } catch (Exception e) {
            return Map.of();
        }
    }

    /**
     * Maps ThinkingLevel to OpenAI reasoning_effort string.
     */
    private static String mapReasoningEffort(ThinkingLevel level) {
        return switch (level) {
            case MINIMAL -> "low";
            case LOW -> "low";
            case MEDIUM -> "medium";
            case HIGH -> "high";
            case XHIGH -> "high";
            default -> "medium";
        };
    }

    /**
     * Returns true for providers that use custom thinking formats (not standard reasoning_effort).
     */
    private static boolean isProviderWithCustomThinking(Provider provider) {
        return provider == Provider.ZAI;
    }

    /**
     * Extracts reasoning/thinking delta from OpenAI-compatible provider delta.
     * Different providers use different fields: reasoning_content, reasoning, reasoning_text.
     */
    private static String extractReasoningDelta(
            com.openai.models.chat.completions.ChatCompletionChunk.Choice.Delta delta) {
        var additionalProps = delta._additionalProperties();
        for (String field : new String[]{"reasoning_content", "reasoning", "reasoning_text"}) {
            var value = additionalProps.get(field);
            if (value != null) {
                // JsonValue could be a string
                try {
                    String str = value.toString();
                    // Remove surrounding quotes if present
                    if (str.startsWith("\"") && str.endsWith("\"")) {
                        str = str.substring(1, str.length() - 1)
                            .replace("\\n", "\n")
                            .replace("\\\"", "\"")
                            .replace("\\\\", "\\");
                    }
                    if (!str.isEmpty() && !"null".equals(str)) return str;
                } catch (Exception ignored) {}
            }
        }
        return null;
    }

    private static <T> int findBlockIndex(List<ContentBlock> blocks, Class<T> type) {
        for (int i = blocks.size() - 1; i >= 0; i--) {
            if (type.isInstance(blocks.get(i))) return i;
        }
        return -1;
    }

    private static int findTextBlockIndex(List<ContentBlock> blocks) {
        for (int i = blocks.size() - 1; i >= 0; i--) {
            if (blocks.get(i) instanceof TextContent) return i;
        }
        return -1;
    }

    /**
     * Mutable accumulator for streaming tool call deltas.
     */
    private static class ToolCallAccumulator {
        String id;
        String name;
        final StringBuilder arguments = new StringBuilder();
        boolean started;
        int contentIndex = -1;
    }
}
