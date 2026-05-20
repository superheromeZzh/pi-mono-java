/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.hicampus.mate.matecampusclaw.ai.provider.mistral;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.huawei.hicampus.mate.matecampusclaw.ai.provider.ApiProvider;
import com.huawei.hicampus.mate.matecampusclaw.ai.stream.AssistantMessageEventStream;
import com.huawei.hicampus.mate.matecampusclaw.ai.types.Api;
import com.huawei.hicampus.mate.matecampusclaw.ai.types.AssistantMessage;
import com.huawei.hicampus.mate.matecampusclaw.ai.types.ContentBlock;
import com.huawei.hicampus.mate.matecampusclaw.ai.types.Context;
import com.huawei.hicampus.mate.matecampusclaw.ai.types.Cost;
import com.huawei.hicampus.mate.matecampusclaw.ai.types.Model;
import com.huawei.hicampus.mate.matecampusclaw.ai.types.SimpleStreamOptions;
import com.huawei.hicampus.mate.matecampusclaw.ai.types.StopReason;
import com.huawei.hicampus.mate.matecampusclaw.ai.types.StreamOptions;
import com.huawei.hicampus.mate.matecampusclaw.ai.types.TextContent;
import com.huawei.hicampus.mate.matecampusclaw.ai.types.ThinkingContent;
import com.huawei.hicampus.mate.matecampusclaw.ai.types.ThinkingLevel;
import com.huawei.hicampus.mate.matecampusclaw.ai.types.ToolCall;
import com.huawei.hicampus.mate.matecampusclaw.ai.types.ToolResultMessage;
import com.huawei.hicampus.mate.matecampusclaw.ai.types.Usage;
import com.huawei.hicampus.mate.matecampusclaw.ai.types.UserMessage;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import jakarta.annotation.Nullable;

/**
 * {@link ApiProvider} for the Mistral Conversations API.
 * Uses java.net.http.HttpClient for SSE streaming.
 *
 * @version [br_eCampusCore 25.1.0_Next, 2026/05/06]
 * @since [br_eCampusCore 25.1.0_Next]
 */
@Component
public class MistralProvider implements ApiProvider {

    private static final Logger log = LoggerFactory.getLogger(MistralProvider.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String DEFAULT_BASE_URL = "https://api.mistral.ai/v1";

    private final com.huawei.hicampus.mate.matecampusclaw.ai.env.ProviderConfigResolver providerConfigResolver;

    public MistralProvider(com.huawei.hicampus.mate.matecampusclaw.ai.env.ProviderConfigResolver providerConfigResolver) {
        this.providerConfigResolver = providerConfigResolver;
    }

    @Override
    public Api getApi() {
        return Api.MISTRAL_CONVERSATIONS;
    }

    @Override
    public AssistantMessageEventStream stream(Model model, Context context, @Nullable StreamOptions options) {
        return streamSimple(model, context, options != null ? SimpleStreamOptions.from(options) : null);
    }

    @Override
    public AssistantMessageEventStream streamSimple(
            Model model, Context context, @Nullable SimpleStreamOptions options) {
        var eventStream = new AssistantMessageEventStream();

        Thread.ofVirtual().start(() -> {
            try {
                executeStream(model, context, options, eventStream);
            } catch (Exception e) {
                eventStream.error(e);
            }
        });

        return eventStream;
    }

    /**
     * Mutable state shared across the Mistral SSE chunk handlers.
     */
    private static final class MistralStreamState {
        final List<ContentBlock> accumulatedBlocks = new ArrayList<>();
        final StringBuilder currentText = new StringBuilder();
        final StringBuilder currentThinking = new StringBuilder();
        final Map<Integer, ToolCallAccumulator> toolCallAccs = new HashMap<>();
        Usage usage = Usage.empty();
        StopReason stop = StopReason.STOP;
        boolean thinkingStarted;
    }

    private void executeStream(
            Model model,
            Context context,
            @Nullable SimpleStreamOptions options,
            AssistantMessageEventStream eventStream) {
        var providerConfig = providerConfigResolver.resolve(model.provider(), model);
        String apiKey = resolveApiKey(providerConfig, options);
        if (apiKey == null || apiKey.isBlank()) {
            eventStream.error(
                    new IllegalStateException(
                            "Mistral API key not found. Set MISTRAL_API_KEY, configure provider.mistral.apiKey in settings.json, or run /auth login."));
            return;
        }
        String overrideBaseUrl = providerConfig.resolveBaseUrl(model);
        String baseUrl = overrideBaseUrl != null ? overrideBaseUrl : DEFAULT_BASE_URL;
        ObjectNode requestBody = buildRequestBody(model, context, options);
        try {
            var response = sendStreamRequest(baseUrl + "/chat/completions", apiKey, requestBody);
            var state = new MistralStreamState();
            try (var reader = new BufferedReader(new InputStreamReader(response.body(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (consumeSseLine(line, state, model, eventStream)) {
                        break;
                    }
                }
            }
            finishMistralStream(state, model, eventStream);
        } catch (Exception e) {
            eventStream.error(e);
        }
    }

    private static HttpResponse<java.io.InputStream> sendStreamRequest(String url, String apiKey, ObjectNode body)
            throws Exception {
        var client = HttpClient.newBuilder()
                .connectTimeout(java.time.Duration.ofSeconds(15))
                .build();
        var request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + apiKey)
                .POST(HttpRequest.BodyPublishers.ofString(body.toString()))
                .build();
        return client.send(request, HttpResponse.BodyHandlers.ofInputStream());
    }

    // Returns true when the stream's terminator [DONE] is seen.
    private boolean consumeSseLine(
            String line, MistralStreamState state, Model model, AssistantMessageEventStream eventStream)
            throws Exception {
        if (line.isBlank() || !line.startsWith("data: ")) {
            return false;
        }
        String data = line.substring(6).trim();
        if (data.equals("[DONE]")) {
            return true;
        }
        JsonNode chunk = MAPPER.readTree(data);
        var choices = chunk.path("choices");
        if (!choices.isArray() || choices.isEmpty()) {
            return false;
        }
        var choice = choices.get(0);
        var delta = choice.path("delta");
        if (delta.has("content") && !delta.get("content").isNull()) {
            handleContentDelta(delta.get("content"), state, model, eventStream);
        }
        if (delta.has("tool_calls") && delta.get("tool_calls").isArray()) {
            accumulateToolCallDelta(delta.get("tool_calls"), state);
        }
        if (choice.has("finish_reason") && !choice.get("finish_reason").isNull()) {
            state.stop = mapFinishReason(choice.get("finish_reason").asText());
        }
        if (chunk.has("usage") && !chunk.get("usage").isNull()) {
            var u = chunk.get("usage");
            int input = u.path("prompt_tokens").asInt(0);
            int output = u.path("completion_tokens").asInt(0);
            state.usage = new Usage(input, output, 0, 0, input + output, Cost.empty());
        }
        return false;
    }

    // Mistral content can be a plain string or an array of typed items (thinking/text/...).
    private void handleContentDelta(
            JsonNode contentNode, MistralStreamState state, Model model, AssistantMessageEventStream eventStream) {
        if (contentNode.isTextual()) {
            String text = contentNode.asText();
            if (!text.isEmpty()) {
                emitTextDelta(text, state, model, eventStream);
            }
            return;
        }
        if (!contentNode.isArray()) {
            return;
        }
        for (var item : contentNode) {
            String itemType = item.path("type").asText("");
            if ("thinking".equals(itemType)) {
                String thinkText = extractThinkingText(item.path("thinking"));
                if (!thinkText.isEmpty()) {
                    emitThinkingDelta(thinkText, state, model, eventStream);
                }
            } else if ("text".equals(itemType)) {
                String text = item.path("text").asText("");
                if (!text.isEmpty()) {
                    emitTextDelta(text, state, model, eventStream);
                }
            }
        }
    }

    private static String extractThinkingText(JsonNode thinkingArr) {
        var sb = new StringBuilder();
        if (thinkingArr.isArray()) {
            for (var part : thinkingArr) {
                if ("text".equals(part.path("type").asText(""))) {
                    sb.append(part.path("text").asText(""));
                }
            }
        }
        return sb.toString();
    }

    private void emitTextDelta(
            String text, MistralStreamState state, Model model, AssistantMessageEventStream eventStream) {
        state.currentText.append(text);
        eventStream.pushTextDelta(0, text, partialFrom(state, model));
    }

    private void emitThinkingDelta(
            String thinkText, MistralStreamState state, Model model, AssistantMessageEventStream eventStream) {
        if (!state.thinkingStarted) {
            state.thinkingStarted = true;
            state.accumulatedBlocks.add(new ThinkingContent("", null, false));
            int idx = state.accumulatedBlocks.size() - 1;
            eventStream.push(new com.huawei.hicampus.mate.matecampusclaw.ai.stream.AssistantMessageEvent.ThinkingStartEvent(
                    idx, partialFrom(state, model)));
        }
        state.currentThinking.append(thinkText);
        int idx = state.accumulatedBlocks.size() - 1;
        state.accumulatedBlocks.set(idx, new ThinkingContent(state.currentThinking.toString(), null, false));
        eventStream.push(new com.huawei.hicampus.mate.matecampusclaw.ai.stream.AssistantMessageEvent.ThinkingDeltaEvent(
                idx, thinkText, partialFrom(state, model)));
    }

    private static void accumulateToolCallDelta(JsonNode toolCalls, MistralStreamState state) {
        for (var tc : toolCalls) {
            int idx = tc.path("index").asInt(0);
            var acc = state.toolCallAccs.computeIfAbsent(idx, k -> new ToolCallAccumulator());
            if (tc.has("id")) {
                acc.id = tc.get("id").asText();
            }
            var fn = tc.path("function");
            if (fn.has("name")) {
                acc.name = fn.get("name").asText();
            }
            if (fn.has("arguments")) {
                acc.arguments.append(fn.get("arguments").asText());
            }
        }
    }

    private void finishMistralStream(MistralStreamState state, Model model, AssistantMessageEventStream eventStream) {
        if (state.thinkingStarted && !state.currentThinking.isEmpty()) {
            int thinkIdx = state.accumulatedBlocks.size() - 1;
            state.accumulatedBlocks.set(thinkIdx, new ThinkingContent(state.currentThinking.toString(), null, false));
            eventStream.push(new com.huawei.hicampus.mate.matecampusclaw.ai.stream.AssistantMessageEvent.ThinkingEndEvent(
                    thinkIdx, state.currentThinking.toString(), partialFrom(state, model)));
        }
        var finalBlocks = new ArrayList<>(state.accumulatedBlocks);
        if (!state.currentText.isEmpty()) {
            finalBlocks.add(new TextContent(state.currentText.toString()));
        }
        for (var acc : state.toolCallAccs.values()) {
            finalBlocks.add(acc.toToolCall());
        }
        var cost = computeCost(model, state.usage);
        var finalUsage = new Usage(
                state.usage.input(), state.usage.output(), 0, 0, state.usage.input() + state.usage.output(), cost);
        var finalMessage = new AssistantMessage(
                List.copyOf(finalBlocks),
                Api.MISTRAL_CONVERSATIONS.value(),
                model.provider().value(),
                model.id(),
                null,
                finalUsage,
                state.stop,
                null,
                System.currentTimeMillis());
        eventStream.pushDone(state.stop, finalMessage);
    }

    private AssistantMessage partialFrom(MistralStreamState state, Model model) {
        return buildPartial(
                model,
                state.accumulatedBlocks,
                state.currentText.toString(),
                state.currentThinking.toString(),
                state.toolCallAccs,
                state.stop,
                state.usage);
    }

    private AssistantMessage buildPartial(
            Model model,
            List<ContentBlock> accumulatedBlocks,
            String currentText,
            String currentThinking,
            Map<Integer, ToolCallAccumulator> toolCallAccs,
            StopReason stop,
            Usage usage) {
        var blocks = new ArrayList<ContentBlock>(accumulatedBlocks);
        if (!currentText.isEmpty()) {
            blocks.add(new TextContent(currentText));
        }
        for (var acc : toolCallAccs.values()) {
            if (acc.name != null) {
                blocks.add(acc.toToolCall());
            }
        }
        return new AssistantMessage(
                blocks,
                Api.MISTRAL_CONVERSATIONS.value(),
                model.provider().value(),
                model.id(),
                null,
                usage,
                stop,
                null,
                System.currentTimeMillis());
    }

    private ObjectNode buildRequestBody(Model model, Context context, @Nullable SimpleStreamOptions options) {
        var body = MAPPER.createObjectNode();
        body.put("model", model.id());
        body.put("stream", true);
        body.set("stream_options", MAPPER.createObjectNode().put("include_usage", true));
        body.set("messages", buildMessagesArray(context));
        if (context.tools() != null && !context.tools().isEmpty()) {
            body.set("tools", buildToolsArray(context));
        }
        applySamplingOptions(body, model, options);
        return body;
    }

    private com.fasterxml.jackson.databind.node.ArrayNode buildMessagesArray(Context context) {
        var messages = MAPPER.createArrayNode();
        if (context.systemPrompt() != null && !context.systemPrompt().isBlank()) {
            messages.add(MAPPER.createObjectNode().put("role", "system").put("content", context.systemPrompt()));
        }
        for (var msg : context.messages()) {
            switch (msg) {
                case UserMessage um ->
                    messages.add(
                            MAPPER.createObjectNode().put("role", "user").put("content", extractText(um.content())));
                case AssistantMessage am -> messages.add(buildAssistantMessageNode(am));
                case ToolResultMessage trm ->
                    messages.add(MAPPER.createObjectNode()
                            .put("role", "tool")
                            .put("tool_call_id", trm.toolCallId())
                            .put("content", extractText(trm.content())));
                default -> {
                    // ignored
                }
            }
        }
        return messages;
    }

    private ObjectNode buildAssistantMessageNode(AssistantMessage am) {
        var m = MAPPER.createObjectNode();
        m.put("role", "assistant");
        StringBuilder text = new StringBuilder();
        var toolCalls = MAPPER.createArrayNode();
        var contentArray = MAPPER.createArrayNode();
        boolean hasThinking = false;
        for (var block : am.content()) {
            if (block instanceof ThinkingContent tc
                    && tc.thinking() != null
                    && !tc.thinking().isBlank()) {
                hasThinking = true;
                contentArray.add(buildThinkingNode(tc.thinking()));
            } else if (block instanceof TextContent tc) {
                text.append(tc.text());
            } else if (block instanceof ToolCall tc) {
                toolCalls.add(buildToolCallNode(tc));
            }
        }
        if (hasThinking) {
            if (!text.isEmpty()) {
                contentArray.add(MAPPER.createObjectNode().put("type", "text").put("text", text.toString()));
            }
            m.set("content", contentArray);
        } else if (!text.isEmpty()) {
            m.put("content", text.toString());
        }
        if (!toolCalls.isEmpty()) {
            m.set("tool_calls", toolCalls);
        }
        return m;
    }

    private ObjectNode buildThinkingNode(String thinking) {
        var item = MAPPER.createObjectNode();
        item.put("type", "thinking");
        var parts = MAPPER.createArrayNode();
        parts.add(MAPPER.createObjectNode().put("type", "text").put("text", thinking));
        item.set("thinking", parts);
        return item;
    }

    private ObjectNode buildToolCallNode(ToolCall tc) {
        var node = MAPPER.createObjectNode();
        node.put("id", tc.id());
        node.put("type", "function");
        var fn = MAPPER.createObjectNode();
        fn.put("name", tc.name());
        try {
            fn.put("arguments", MAPPER.writeValueAsString(tc.arguments()));
        } catch (Exception e) {
            fn.put("arguments", "{}");
        }
        node.set("function", fn);
        return node;
    }

    private com.fasterxml.jackson.databind.node.ArrayNode buildToolsArray(Context context) {
        var tools = MAPPER.createArrayNode();
        for (var tool : context.tools()) {
            var t = MAPPER.createObjectNode();
            t.put("type", "function");
            var fn = MAPPER.createObjectNode();
            fn.put("name", tool.name());
            fn.put("description", tool.description());
            if (tool.parameters() != null) {
                fn.set("parameters", MAPPER.valueToTree(tool.parameters()));
            }
            t.set("function", fn);
            tools.add(t);
        }
        return tools;
    }

    private static void applySamplingOptions(ObjectNode body, Model model, @Nullable SimpleStreamOptions options) {
        if (options == null) {
            body.put("max_tokens", model.maxTokens());
            return;
        }
        if (options.maxTokens() != null) {
            body.put("max_tokens", options.maxTokens());
        }
        if (options.temperature() != null) {
            body.put("temperature", options.temperature());
        }
        if (options.reasoning() != null && options.reasoning() != ThinkingLevel.OFF && model.reasoning()) {
            body.put("promptMode", "reasoning");
        }
    }

    private String resolveApiKey(
            com.huawei.hicampus.mate.matecampusclaw.ai.env.ResolvedProviderConfig providerConfig, @Nullable SimpleStreamOptions options) {
        if (options != null && options.apiKey() != null) {
            return options.apiKey();
        }
        return providerConfig.apiKey();
    }

    private StopReason mapFinishReason(String reason) {
        return switch (reason) {
            case "stop" -> StopReason.STOP;
            case "length" -> StopReason.LENGTH;
            case "tool_calls" -> StopReason.TOOL_USE;
            default -> StopReason.STOP;
        };
    }

    private Cost computeCost(Model model, Usage usage) {
        if (model.cost() == null) {
            return Cost.empty();
        }
        var mc = model.cost();
        double input = usage.input() * mc.input() / 1_000_000.0;
        double output = usage.output() * mc.output() / 1_000_000.0;
        return new Cost(input, output, 0, 0, input + output);
    }

    private static String extractText(List<ContentBlock> content) {
        var sb = new StringBuilder();
        for (var block : content) {
            if (block instanceof TextContent tc) {
                sb.append(tc.text());
            }
        }
        return sb.toString();
    }

    private static class ToolCallAccumulator {
        String id;
        String name;
        StringBuilder arguments = new StringBuilder();

        ToolCall toToolCall() {
            Map<String, Object> args = Map.of();
            if (!arguments.isEmpty()) {
                try {
                    args = MAPPER.readValue(arguments.toString(), new TypeReference<>() {});
                } catch (Exception e) {
                    // malformed tool-call JSON from upstream — fall back to empty args
                    log.warn("Mistral tool-call args could not be parsed (name={}); using empty args", name, e);
                }
            }
            return new ToolCall(id != null ? id : UUID.randomUUID().toString(), name != null ? name : "", args);
        }

        private static final ObjectMapper MAPPER = new ObjectMapper();
    }
}
