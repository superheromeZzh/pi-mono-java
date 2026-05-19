/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.campusclaw.ai.provider.openai;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.campusclaw.ai.env.ProviderConfigResolver;
import com.campusclaw.ai.env.ResolvedProviderConfig;
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
 *
 * @version [br_eCampusCore 25.1.0_Next, 2026/05/06]
 * @since [br_eCampusCore 25.1.0_Next]
 */
@Component
public class OpenAICompletionsProvider implements ApiProvider {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final ProviderConfigResolver providerConfigResolver;

    public OpenAICompletionsProvider(ProviderConfigResolver providerConfigResolver) {
        this.providerConfigResolver = providerConfigResolver;
    }

    @Override
    public Api getApi() {
        return Api.OPENAI_COMPLETIONS;
    }

    @Override
    public AssistantMessageEventStream stream(Model model, Context context, @Nullable StreamOptions options) {
        return doStream(
                model,
                context,
                options != null ? options.apiKey() : null,
                options != null ? options.maxTokens() : null,
                options != null ? options.temperature() : null,
                null);
    }

    @Override
    public AssistantMessageEventStream streamSimple(
            Model model, Context context, @Nullable SimpleStreamOptions options) {
        return doStream(
                model,
                context,
                options != null ? options.apiKey() : null,
                options != null ? options.maxTokens() : null,
                options != null ? options.temperature() : null,
                options != null ? options.reasoning() : null);
    }

    private AssistantMessageEventStream doStream(
            Model model,
            Context context,
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
            Model model,
            Context context,
            @Nullable String apiKey,
            @Nullable Integer maxTokens,
            @Nullable Double temperature,
            @Nullable ThinkingLevel reasoning,
            AssistantMessageEventStream eventStream) {

        ResolvedProviderConfig providerConfig = providerConfigResolver.resolve(model.provider(), model);
        String resolvedApiKey = apiKey != null ? apiKey : providerConfig.apiKey();
        if (resolvedApiKey == null || resolvedApiKey.isBlank()) {
            String envHint =
                    switch (model.provider()) {
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
                    "API key not found for " + model.provider().value()
                            + ". Set " + envHint + ", configure provider."
                            + model.provider().value()
                            + ".apiKey in settings.json, or run /auth login."));
            return;
        }

        OpenAIClient client = buildClient(resolvedApiKey, providerConfig.resolveBaseUrl(model));

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
            Model model,
            Context context,
            @Nullable Integer maxTokens,
            @Nullable Double temperature,
            @Nullable ThinkingLevel reasoning) {

        int resolvedMaxTokens = maxTokens != null ? maxTokens : Math.min(model.maxTokens(), 32000);

        // Build messages list with system prompt first
        var messages = new ArrayList<ChatCompletionMessageParam>();
        if (context.systemPrompt() != null && !context.systemPrompt().isBlank()) {
            messages.add(ChatCompletionMessageParam.ofSystem(ChatCompletionSystemMessageParam.builder()
                    .content(context.systemPrompt())
                    .build()));
        }
        messages.addAll(convertMessages(context.messages()));

        var builder = ChatCompletionCreateParams.builder()
                .model(model.id())
                .maxCompletionTokens((long) resolvedMaxTokens)
                .messages(messages)
                .streamOptions(
                        ChatCompletionStreamOptions.builder().includeUsage(true).build());

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
                builder.putAdditionalBodyProperty("enable_thinking", com.openai.core.JsonValue.from(true));
            } else if (!isProviderWithCustomThinking(model.provider())) {
                // Standard OpenAI-compatible reasoning_effort (OpenAI, xAI, Groq, etc.)
                String effort = mapReasoningEffort(reasoning);
                builder.putAdditionalBodyProperty("reasoning_effort", com.openai.core.JsonValue.from(effort));
            }
        }

        return builder.build();
    }

    /**
     * Mutable state bundle for the {@link #processStream} chunk handler — lets us
     * extract sub-handlers without juggling many out-parameter arrays.
     */
    private static final class StreamState {
        final ArrayList<ContentBlock> contentBlocks = new ArrayList<>();
        final long[] accumulatedUsage = {0, 0, 0, 0}; // input, output, cacheRead, cacheWrite
        final StringBuilder textAccumulator = new StringBuilder();
        final StringBuilder thinkingAccumulator = new StringBuilder();
        final HashMap<Integer, ToolCallAccumulator> toolAccumulators = new HashMap<>();
        String responseId;
        StopReason stopReason;
        boolean textBlockStarted;
        boolean thinkingBlockStarted;
    }

    private void processStream(
            OpenAIClient client,
            ChatCompletionCreateParams params,
            Model model,
            AssistantMessageEventStream eventStream) {
        var state = new StreamState();
        try (StreamResponse<ChatCompletionChunk> response =
                client.chat().completions().createStreaming(params)) {
            response.stream().forEach(chunk -> handleChunk(chunk, state, model, eventStream));
        }
        finishOpenBlocks(state, model, eventStream);
        emitDone(state, model, eventStream);
    }

    private void handleChunk(
            ChatCompletionChunk chunk, StreamState state, Model model, AssistantMessageEventStream eventStream) {
        state.responseId = chunk.id();
        chunk.usage().ifPresent(usage -> parseUsage(usage, state.accumulatedUsage));
        if (chunk.choices().isEmpty()) {
            return;
        }
        var choice = chunk.choices().get(0);
        choice.finishReason().ifPresent(fr -> state.stopReason = mapFinishReason(fr.asString()));
        var delta = choice.delta();
        delta.content().ifPresent(text -> handleTextDelta(text, state, model, eventStream));
        String reasoningDelta = extractReasoningDelta(delta);
        if (reasoningDelta != null && !reasoningDelta.isEmpty()) {
            handleReasoningDelta(reasoningDelta, state, model, eventStream);
        }
        delta.toolCalls().ifPresent(tcs -> handleToolCallsDelta(tcs, state, model, eventStream));
    }

    private void handleTextDelta(String text, StreamState state, Model model, AssistantMessageEventStream eventStream) {
        if (text.isEmpty()) {
            return;
        }
        if (!state.textBlockStarted) {
            state.textBlockStarted = true;
            state.contentBlocks.add(new TextContent("", null));
            int idx = state.contentBlocks.size() - 1;
            eventStream.push(new AssistantMessageEvent.TextStartEvent(idx, partialFrom(state, model, null)));
        }
        state.textAccumulator.append(text);
        int idx = state.contentBlocks.size() - 1;
        state.contentBlocks.set(idx, new TextContent(state.textAccumulator.toString(), null));
        eventStream.push(new AssistantMessageEvent.TextDeltaEvent(idx, text, partialFrom(state, model, null)));
    }

    private void handleReasoningDelta(
            String reasoningDelta, StreamState state, Model model, AssistantMessageEventStream eventStream) {
        if (!state.thinkingBlockStarted) {
            state.thinkingBlockStarted = true;
            state.contentBlocks.add(new ThinkingContent("", null, false));
            int idx = state.contentBlocks.size() - 1;
            eventStream.push(new AssistantMessageEvent.ThinkingStartEvent(idx, partialFrom(state, model, null)));
        }
        state.thinkingAccumulator.append(reasoningDelta);
        int idx = state.contentBlocks.size() - 1;
        state.contentBlocks.set(idx, new ThinkingContent(state.thinkingAccumulator.toString(), null, false));
        eventStream.push(
                new AssistantMessageEvent.ThinkingDeltaEvent(idx, reasoningDelta, partialFrom(state, model, null)));
    }

    private void handleToolCallsDelta(
            Iterable<? extends ChatCompletionChunk.Choice.Delta.ToolCall> toolCalls,
            StreamState state,
            Model model,
            AssistantMessageEventStream eventStream) {
        for (var tc : toolCalls) {
            int toolIndex = (int) tc.index();
            var acc = state.toolAccumulators.computeIfAbsent(toolIndex, k -> new ToolCallAccumulator());
            tc.id().ifPresent(id -> acc.id = id);
            tc.function().ifPresent(fn -> fn.name().ifPresent(name -> acc.name = name));
            String[] argsDelta = {null};
            tc.function().ifPresent(fn -> fn.arguments().ifPresent(a -> {
                if (!a.isEmpty()) {
                    argsDelta[0] = a;
                    acc.arguments.append(a);
                }
            }));
            if (!acc.started) {
                startToolCallBlock(acc, state, model, eventStream);
            }
            if (argsDelta[0] != null) {
                eventStream.push(new AssistantMessageEvent.ToolCallDeltaEvent(
                        acc.contentIndex, argsDelta[0], partialFrom(state, model, null)));
            }
        }
    }

    private void startToolCallBlock(
            ToolCallAccumulator acc, StreamState state, Model model, AssistantMessageEventStream eventStream) {
        acc.started = true;

        // Finish any open text block first — once a tool call appears, the text run is done.
        if (state.textBlockStarted) {
            int textIdx = findTextBlockIndex(state.contentBlocks);
            if (textIdx >= 0) {
                eventStream.push(new AssistantMessageEvent.TextEndEvent(
                        textIdx, state.textAccumulator.toString(), partialFrom(state, model, null)));
            }
            state.textBlockStarted = false;
        }
        state.contentBlocks.add(
                new ToolCall(acc.id != null ? acc.id : "", acc.name != null ? acc.name : "", Map.of(), null));
        acc.contentIndex = state.contentBlocks.size() - 1;
        eventStream.push(
                new AssistantMessageEvent.ToolCallStartEvent(acc.contentIndex, partialFrom(state, model, null)));
    }

    private void finishOpenBlocks(StreamState state, Model model, AssistantMessageEventStream eventStream) {
        if (state.thinkingBlockStarted) {
            int thinkIdx = findBlockIndex(state.contentBlocks, ThinkingContent.class);
            if (thinkIdx >= 0) {
                state.contentBlocks.set(
                        thinkIdx, new ThinkingContent(state.thinkingAccumulator.toString(), null, false));
                eventStream.push(new AssistantMessageEvent.ThinkingEndEvent(
                        thinkIdx, state.thinkingAccumulator.toString(), partialFrom(state, model, null)));
            }
        }
        if (state.textBlockStarted) {
            int textIdx = findTextBlockIndex(state.contentBlocks);
            if (textIdx >= 0) {
                state.contentBlocks.set(textIdx, new TextContent(state.textAccumulator.toString(), null));
                eventStream.push(new AssistantMessageEvent.TextEndEvent(
                        textIdx, state.textAccumulator.toString(), partialFrom(state, model, null)));
            }
        }
        for (var entry : state.toolAccumulators.entrySet()) {
            var acc = entry.getValue();
            if (acc.started && acc.contentIndex >= 0 && acc.contentIndex < state.contentBlocks.size()) {
                Map<String, Object> args = parseToolArguments(acc.arguments.toString());
                var toolCall = new ToolCall(acc.id != null ? acc.id : "", acc.name != null ? acc.name : "", args, null);
                state.contentBlocks.set(acc.contentIndex, toolCall);
                eventStream.push(new AssistantMessageEvent.ToolCallEndEvent(
                        acc.contentIndex, toolCall, partialFrom(state, model, null)));
            }
        }
    }

    private void emitDone(StreamState state, Model model, AssistantMessageEventStream eventStream) {
        var finalStopReason = state.stopReason != null ? state.stopReason : StopReason.STOP;
        eventStream.pushDone(finalStopReason, partialFrom(state, model, finalStopReason));
    }

    private AssistantMessage partialFrom(StreamState state, Model model, @Nullable StopReason stopReason) {
        return buildPartialMessage(model, state.responseId, state.contentBlocks, state.accumulatedUsage, stopReason);
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
                        ChatCompletionContentPartText.builder().text(tc.text()).build()));
            } else if (cb instanceof ImageContent ic) {
                parts.add(ChatCompletionContentPart.ofImageUrl(ChatCompletionContentPartImage.builder()
                        .imageUrl(ChatCompletionContentPartImage.ImageUrl.builder()
                                .url("data:" + ic.mimeType() + ";base64," + ic.data())
                                .build())
                        .build()));
            }
        }
        return ChatCompletionMessageParam.ofUser(ChatCompletionUserMessageParam.builder()
                .content(ChatCompletionUserMessageParam.Content.ofArrayOfContentParts(parts))
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
                toolCalls.add(ChatCompletionMessageToolCall.ofFunction(ChatCompletionMessageFunctionToolCall.builder()
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

        return ChatCompletionMessageParam.ofTool(ChatCompletionToolMessageParam.builder()
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
                    paramsBuilder.putAdditionalProperty(
                            entry.getKey(), com.openai.core.JsonValue.from(entry.getValue()));
                }
            }

            var functionDef = FunctionDefinition.builder()
                    .name(tool.name())
                    .description(tool.description())
                    .parameters(paramsBuilder.build())
                    .strict(false)
                    .build();

            result.add(ChatCompletionTool.ofFunction(
                    ChatCompletionFunctionTool.builder().function(functionDef).build()));
        }
        return result;
    }

    // -- Finish reason mapping --

    static StopReason mapFinishReason(String reason) {
        if (reason == null) {
            return StopReason.STOP;
        }
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
        long cachedTokens =
                usage.promptTokensDetails().flatMap(d -> d.cachedTokens()).orElse(0L);

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
            Model model,
            String responseId,
            List<ContentBlock> contentBlocks,
            long[] usage,
            @Nullable StopReason stopReason) {

        var piUsage = new Usage(
                (int) usage[0],
                (int) usage[1],
                (int) usage[2],
                (int) usage[3],
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
        return new Cost(
                inputCost,
                outputCost,
                cacheReadCost,
                cacheWriteCost,
                inputCost + outputCost + cacheReadCost + cacheWriteCost);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> parseToolArguments(String json) {
        if (json == null || json.isBlank()) {
            return Map.of();
        }
        try {
            return MAPPER.readValue(json, new TypeReference<Map<String, Object>>() {});
        } catch (Exception e) {
            return Map.of();
        }
    }

    private static String serializeArguments(Map<String, Object> args) {
        if (args == null || args.isEmpty()) {
            return "{}";
        }
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
     *
     * @param level requested thinking level
     * @return the OpenAI {@code reasoning_effort} value
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
     *
     * @param provider the provider variant
     * @return {@code true} when the provider needs its own thinking schema
     */
    private static boolean isProviderWithCustomThinking(Provider provider) {
        return provider == Provider.ZAI;
    }

    /**
     * Extracts reasoning/thinking delta from OpenAI-compatible provider delta.
     * Different providers use different fields: reasoning_content, reasoning, reasoning_text.
     *
     * @param delta the chat completion chunk delta
     * @return the extracted reasoning text, or empty string when none is present
     */
    private static String extractReasoningDelta(
            com.openai.models.chat.completions.ChatCompletionChunk.Choice.Delta delta) {
        var additionalProps = delta._additionalProperties();
        for (String field : new String[] {"reasoning_content", "reasoning", "reasoning_text"}) {
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
                    if (!str.isEmpty() && !"null".equals(str)) {
                        return str;
                    }
                } catch (Exception ignored) {
                    // best-effort string extraction — null return below signals "no usable text"
                }
            }
        }
        return null;
    }

    private static <T> int findBlockIndex(List<ContentBlock> blocks, Class<T> type) {
        for (int i = blocks.size() - 1; i >= 0; i--) {
            if (type.isInstance(blocks.get(i))) {
                return i;
            }
        }
        return -1;
    }

    private static int findTextBlockIndex(List<ContentBlock> blocks) {
        for (int i = blocks.size() - 1; i >= 0; i--) {
            if (blocks.get(i) instanceof TextContent) {
                return i;
            }
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
