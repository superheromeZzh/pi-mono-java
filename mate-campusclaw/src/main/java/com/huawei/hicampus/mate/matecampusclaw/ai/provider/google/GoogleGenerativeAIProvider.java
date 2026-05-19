/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.hicampus.mate.matecampusclaw.ai.provider.google;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

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
import com.huawei.hicampus.mate.matecampusclaw.ai.types.ThinkingBudgets;
import com.huawei.hicampus.mate.matecampusclaw.ai.types.ThinkingContent;
import com.huawei.hicampus.mate.matecampusclaw.ai.types.ThinkingLevel;
import com.huawei.hicampus.mate.matecampusclaw.ai.types.ToolCall;
import com.huawei.hicampus.mate.matecampusclaw.ai.types.Usage;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import jakarta.annotation.Nullable;

/**
 * {@link ApiProvider} for the Google Generative AI (Gemini) REST API.
 * Uses java.net.http.HttpClient for SSE streaming.
 *
 * @version [br_eCampusCore 25.1.0_Next, 2026/05/06]
 * @since [br_eCampusCore 25.1.0_Next]
 */
@Component
public class GoogleGenerativeAIProvider implements ApiProvider {

    private static final Logger log = LoggerFactory.getLogger(GoogleGenerativeAIProvider.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String DEFAULT_BASE_URL = "https://generativelanguage.googleapis.com/v1beta";

    private final com.huawei.hicampus.mate.matecampusclaw.ai.env.ProviderConfigResolver providerConfigResolver;

    public GoogleGenerativeAIProvider(com.huawei.hicampus.mate.matecampusclaw.ai.env.ProviderConfigResolver providerConfigResolver) {
        this.providerConfigResolver = providerConfigResolver;
    }

    @Override
    public Api getApi() {
        return Api.GOOGLE_GENERATIVE_AI;
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
     * Mutable bundle for the Google SSE stream loop. Mirrors the TypeScript
     * implementation's "currentBlock" pattern: only one block of each kind is
     * open at a time; a new kind closes the previous one.
     */
    private static final class GoogleStreamState {
        final List<ContentBlock> blocks = new ArrayList<>();
        Usage usage = Usage.empty();
        StopReason stop = StopReason.STOP;
        String currentType; // "text", "thinking", or null
        final StringBuilder textAcc = new StringBuilder();
        final StringBuilder thinkingAcc = new StringBuilder();
        String thinkingSig;
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
                    new IllegalStateException("Google API key not found. Set GOOGLE_API_KEY or GOOGLE_CLOUD_API_KEY, "
                            + "configure provider.google.apiKey in settings.json, or run /auth login."));
            return;
        }
        String overrideBaseUrl = providerConfig.resolveBaseUrl(model);
        String baseUrl = overrideBaseUrl != null ? overrideBaseUrl : DEFAULT_BASE_URL;
        String url = baseUrl + "/models/" + model.id() + ":streamGenerateContent?alt=sse&key=" + apiKey;
        ObjectNode requestBody = buildRequestBody(model, context, options);
        try {
            var response = sendStreamRequest(url, requestBody);
            var state = new GoogleStreamState();
            try (var reader = new BufferedReader(new InputStreamReader(response.body(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (consumeSseLine(line, state, model, eventStream)) {
                        break;
                    }
                }
            }
            finishGoogleStream(state, model, eventStream);
        } catch (Exception e) {
            eventStream.error(e);
        }
    }

    private static HttpResponse<java.io.InputStream> sendStreamRequest(String url, ObjectNode requestBody)
            throws Exception {
        var client = HttpClient.newBuilder()
                .connectTimeout(java.time.Duration.ofSeconds(15))
                .build();
        var request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(requestBody.toString()))
                .build();
        return client.send(request, HttpResponse.BodyHandlers.ofInputStream());
    }

    // Returns true when the stream's terminator [DONE] is seen.
    private boolean consumeSseLine(
            String line, GoogleStreamState state, Model model, AssistantMessageEventStream eventStream)
            throws Exception {
        if (line.isBlank() || !line.startsWith("data: ")) {
            return false;
        }
        String data = line.substring(6).trim();
        if (data.equals("[DONE]")) {
            return true;
        }
        JsonNode chunk = MAPPER.readTree(data);
        var parsed = GoogleShared.parseChunk(chunk);
        for (var block : parsed.blocks()) {
            dispatchBlock(block, state, model, eventStream);
        }
        if (parsed.usage() != null) {
            state.usage = parsed.usage();
        }
        if (parsed.finishReason() != null) {
            state.stop = GoogleShared.mapFinishReason(parsed.finishReason());
        }
        return false;
    }

    private void dispatchBlock(
            ContentBlock block, GoogleStreamState state, Model model, AssistantMessageEventStream eventStream)
            throws com.fasterxml.jackson.core.JsonProcessingException {
        if (block instanceof ThinkingContent tc) {
            appendThinkingDelta(tc, state, model, eventStream);
        } else if (block instanceof TextContent tc) {
            appendTextDelta(tc, state, model, eventStream);
        } else if (block instanceof ToolCall tc) {
            emitToolCall(tc, state, model, eventStream);
        }
    }

    private void appendThinkingDelta(
            ThinkingContent tc, GoogleStreamState state, Model model, AssistantMessageEventStream eventStream) {
        if (!"thinking".equals(state.currentType)) {
            finishCurrentBlock(state, model, eventStream);
            state.currentType = "thinking";
            state.thinkingAcc.setLength(0);
            state.thinkingSig = null;
            state.blocks.add(new ThinkingContent("", null, false));
            eventStream.push(new com.huawei.hicampus.mate.matecampusclaw.ai.stream.AssistantMessageEvent.ThinkingStartEvent(
                    state.blocks.size() - 1, buildMessage(model, state.blocks, state.usage, state.stop)));
        }
        state.thinkingAcc.append(tc.thinking());
        if (tc.thinkingSignature() != null) {
            state.thinkingSig = tc.thinkingSignature();
        }
        state.blocks.set(
                state.blocks.size() - 1, new ThinkingContent(state.thinkingAcc.toString(), state.thinkingSig, false));
        eventStream.push(new com.huawei.hicampus.mate.matecampusclaw.ai.stream.AssistantMessageEvent.ThinkingDeltaEvent(
                state.blocks.size() - 1, tc.thinking(), buildMessage(model, state.blocks, state.usage, state.stop)));
    }

    private void appendTextDelta(
            TextContent tc, GoogleStreamState state, Model model, AssistantMessageEventStream eventStream) {
        if (!"text".equals(state.currentType)) {
            finishCurrentBlock(state, model, eventStream);
            state.currentType = "text";
            state.textAcc.setLength(0);
            state.blocks.add(new TextContent("", null));
            eventStream.push(new com.huawei.hicampus.mate.matecampusclaw.ai.stream.AssistantMessageEvent.TextStartEvent(
                    state.blocks.size() - 1, buildMessage(model, state.blocks, state.usage, state.stop)));
        }
        state.textAcc.append(tc.text());
        state.blocks.set(state.blocks.size() - 1, new TextContent(state.textAcc.toString(), null));
        eventStream.pushTextDelta(
                state.blocks.size() - 1, tc.text(), buildMessage(model, state.blocks, state.usage, state.stop));
    }

    private void emitToolCall(
            ToolCall tc, GoogleStreamState state, Model model, AssistantMessageEventStream eventStream)
            throws com.fasterxml.jackson.core.JsonProcessingException {
        finishCurrentBlock(state, model, eventStream);
        state.currentType = null;
        state.blocks.add(tc);
        int idx = state.blocks.size() - 1;
        eventStream.push(new com.huawei.hicampus.mate.matecampusclaw.ai.stream.AssistantMessageEvent.ToolCallStartEvent(
                idx, buildMessage(model, state.blocks, state.usage, state.stop)));
        eventStream.push(new com.huawei.hicampus.mate.matecampusclaw.ai.stream.AssistantMessageEvent.ToolCallDeltaEvent(
                idx,
                MAPPER.writeValueAsString(tc.arguments()),
                buildMessage(model, state.blocks, state.usage, state.stop)));
        eventStream.push(new com.huawei.hicampus.mate.matecampusclaw.ai.stream.AssistantMessageEvent.ToolCallEndEvent(
                idx, tc, buildMessage(model, state.blocks, state.usage, state.stop)));
    }

    private void finishGoogleStream(GoogleStreamState state, Model model, AssistantMessageEventStream eventStream) {
        finishCurrentBlock(state, model, eventStream);
        if (state.blocks.stream().anyMatch(b -> b instanceof ToolCall)) {
            state.stop = StopReason.TOOL_USE;
        }
        var cost = computeCost(model, state.usage);
        var finalUsage = new Usage(
                state.usage.input(),
                state.usage.output(),
                state.usage.cacheRead(),
                state.usage.cacheWrite(),
                state.usage.totalTokens(),
                cost);
        var finalMessage = new AssistantMessage(
                List.copyOf(state.blocks),
                Api.GOOGLE_GENERATIVE_AI.value(),
                model.provider().value(),
                model.id(),
                null,
                finalUsage,
                state.stop,
                null,
                System.currentTimeMillis());
        eventStream.pushDone(state.stop, finalMessage);
    }

    private void finishCurrentBlock(GoogleStreamState state, Model model, AssistantMessageEventStream eventStream) {
        if (state.currentType == null || state.blocks.isEmpty()) {
            return;
        }
        int idx = state.blocks.size() - 1;
        if ("thinking".equals(state.currentType)) {
            String content = state.thinkingAcc.toString();
            state.blocks.set(idx, new ThinkingContent(content, state.thinkingSig, false));
            eventStream.push(new com.huawei.hicampus.mate.matecampusclaw.ai.stream.AssistantMessageEvent.ThinkingEndEvent(
                    idx, content, buildMessage(model, state.blocks, state.usage, state.stop)));
        } else if ("text".equals(state.currentType)) {
            String content = state.textAcc.toString();
            state.blocks.set(idx, new TextContent(content, null));
            eventStream.push(new com.huawei.hicampus.mate.matecampusclaw.ai.stream.AssistantMessageEvent.TextEndEvent(
                    idx, content, buildMessage(model, state.blocks, state.usage, state.stop)));
        }
    }

    private AssistantMessage buildMessage(Model model, List<ContentBlock> blocks, Usage usage, StopReason stop) {
        return new AssistantMessage(
                List.copyOf(blocks),
                Api.GOOGLE_GENERATIVE_AI.value(),
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

        if (context.systemPrompt() != null && !context.systemPrompt().isBlank()) {
            var sysInstr = MAPPER.createObjectNode();
            var parts = MAPPER.createArrayNode();
            parts.add(MAPPER.createObjectNode().put("text", context.systemPrompt()));
            sysInstr.set("parts", parts);
            body.set("systemInstruction", sysInstr);
        }

        body.set("contents", GoogleShared.convertMessages(context.messages()));

        var tools = GoogleShared.convertTools(context.tools());
        if (tools != null) {
            body.set("tools", tools);
        }

        var genConfig = MAPPER.createObjectNode();
        if (options != null && options.maxTokens() != null) {
            genConfig.put("maxOutputTokens", options.maxTokens());
        } else {
            genConfig.put("maxOutputTokens", model.maxTokens());
        }
        if (options != null && options.temperature() != null) {
            genConfig.put("temperature", options.temperature());
        }

        // Thinking / reasoning configuration
        if (options != null
                && options.reasoning() != null
                && options.reasoning() != ThinkingLevel.OFF
                && model.reasoning()) {
            var thinkingConfig = MAPPER.createObjectNode();
            thinkingConfig.put("includeThoughts", true);
            int budget = resolveGoogleThinkingBudget(model, options.reasoning(), options.thinkingBudgets());
            if (budget >= 0) {
                thinkingConfig.put("thinkingBudget", budget);
            }
            genConfig.set("thinkingConfig", thinkingConfig);
        }

        body.set("generationConfig", genConfig);

        return body;
    }

    private String resolveApiKey(
            com.huawei.hicampus.mate.matecampusclaw.ai.env.ResolvedProviderConfig providerConfig, @Nullable SimpleStreamOptions options) {
        if (options != null && options.apiKey() != null) {
            return options.apiKey();
        }
        return providerConfig.apiKey();
    }

    private Cost computeCost(Model model, Usage usage) {
        if (model.cost() == null) {
            return Cost.empty();
        }
        var mc = model.cost();
        double input = usage.input() * mc.input() / 1_000_000.0;
        double output = usage.output() * mc.output() / 1_000_000.0;
        double cacheRead = usage.cacheRead() * mc.cacheRead() / 1_000_000.0;
        return new Cost(input, output, cacheRead, 0, input + output + cacheRead);
    }

    /**
     * Resolves thinking budget tokens for Google models based on thinking level.
     *
     * @param model the model whose budget table applies
     * @param level requested thinking level
     * @param budgets optional per-level budget overrides
     * @return resolved token budget; {@code -1} signals "let the model decide"
     */
    static int resolveGoogleThinkingBudget(Model model, ThinkingLevel level, @Nullable ThinkingBudgets budgets) {
        if (budgets != null) {
            Integer custom =
                    switch (level) {
                        case MINIMAL -> budgets.minimal();
                        case LOW -> budgets.low();
                        case MEDIUM -> budgets.medium();
                        case HIGH, XHIGH -> budgets.high();
                        default -> null;
                    };
            if (custom != null) {
                return custom;
            }
        }

        ThinkingLevel clamped = level == ThinkingLevel.XHIGH ? ThinkingLevel.HIGH : level;

        if (model.id().contains("2.5-pro")) {
            return switch (clamped) {
                case MINIMAL -> 128;
                case LOW -> 2048;
                case MEDIUM -> 8192;
                case HIGH -> 32768;
                default -> -1;
            };
        }
        if (model.id().contains("2.5-flash")) {
            return switch (clamped) {
                case MINIMAL -> 128;
                case LOW -> 2048;
                case MEDIUM -> 8192;
                case HIGH -> 24576;
                default -> -1;
            };
        }

        return -1; // dynamic
    }
}
