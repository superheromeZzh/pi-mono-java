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
import com.campusclaw.ai.types.Message;
import com.campusclaw.ai.types.Model;
import com.campusclaw.ai.types.ModelCost;
import com.campusclaw.ai.types.SimpleStreamOptions;
import com.campusclaw.ai.types.StopReason;
import com.campusclaw.ai.types.StreamOptions;
import com.campusclaw.ai.types.TextContent;
import com.campusclaw.ai.types.ThinkingContent;
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
import com.openai.models.responses.EasyInputMessage;
import com.openai.models.responses.FunctionTool;
import com.openai.models.responses.ResponseCreateParams;
import com.openai.models.responses.ResponseFunctionToolCall;
import com.openai.models.responses.ResponseInputItem;
import com.openai.models.responses.ResponseOutputItemAddedEvent;
import com.openai.models.responses.ResponseOutputItemDoneEvent;
import com.openai.models.responses.ResponseStatus;
import com.openai.models.responses.ResponseStreamEvent;
import com.openai.models.responses.ResponseUsage;
import com.openai.models.responses.Tool;

import org.springframework.stereotype.Component;

import jakarta.annotation.Nullable;

/**
 * {@link ApiProvider} implementation for the OpenAI Responses API.
 *
 * <p>Uses the official {@code com.openai:openai-java} SDK for streaming
 * requests via {@code client.responses().createStreaming()}, mapping
 * Responses SSE events to the unified {@link AssistantMessageEvent} protocol.
 *
 * <p>Key differences from the Completions provider:
 * <ul>
 *   <li>Uses {@code input} instead of {@code messages}</li>
 *   <li>Uses {@code instructions} for system prompt</li>
 *   <li>Uses {@code max_output_tokens} instead of {@code max_completion_tokens}</li>
 *   <li>Has native reasoning/thinking support via {@code reasoning} param</li>
 *   <li>Different streaming event types (item-based rather than chunk-based)</li>
 *   <li>Tool results use {@code function_call_output} instead of tool messages</li>
 * </ul>
 */
@Component
public class OpenAIResponsesProvider implements ApiProvider {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String ENV_API_KEY = "OPENAI_API_KEY";

    @Override
    public Api getApi() {
        return Api.OPENAI_RESPONSES;
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
            @Nullable com.campusclaw.ai.types.ThinkingLevel reasoning) {

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
            @Nullable com.campusclaw.ai.types.ThinkingLevel reasoning,
            AssistantMessageEventStream eventStream) {

        String resolvedApiKey = apiKey != null ? apiKey
                : (model.apiKey() != null && !model.apiKey().isBlank()) ? model.apiKey()
                : System.getenv(ENV_API_KEY);
        if (resolvedApiKey == null || resolvedApiKey.isBlank()) {
            eventStream.error(new IllegalStateException(
                    "OpenAI API key not found. Set OPENAI_API_KEY or pass via StreamOptions."));
            return;
        }

        OpenAIClient client = buildClient(resolvedApiKey, model.baseUrl());

        try {
            ResponseCreateParams params = buildParams(model, context, maxTokens, temperature, reasoning);
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

    ResponseCreateParams buildParams(
            Model model, Context context,
            @Nullable Integer maxTokens,
            @Nullable Double temperature,
            @Nullable com.campusclaw.ai.types.ThinkingLevel reasoning) {

        int resolvedMaxTokens = maxTokens != null ? maxTokens
                : Math.min(model.maxTokens(), 32000);

        var builder = ResponseCreateParams.builder()
                .model(model.id())
                .maxOutputTokens((long) resolvedMaxTokens)
                .inputOfResponse(convertInputItems(context.messages()))
                .store(false);

        // System prompt via instructions field
        if (context.systemPrompt() != null && !context.systemPrompt().isBlank()) {
            builder.instructions(context.systemPrompt());
        }

        // Tools
        if (context.tools() != null && !context.tools().isEmpty()) {
            builder.tools(convertTools(context.tools()));
        }

        // Temperature
        if (temperature != null) {
            builder.temperature(temperature);
        }

        // Reasoning configuration via additional body properties
        if (model.reasoning()) {
            if (reasoning != null && reasoning != com.campusclaw.ai.types.ThinkingLevel.OFF) {
                String effort = mapReasoningEffort(reasoning);
                var reasoningObj = Map.of("effort", (Object) effort, "summary", (Object) "auto");
                builder.putAdditionalBodyProperty("reasoning",
                        com.openai.core.JsonValue.from(reasoningObj));
            } else {
                builder.putAdditionalBodyProperty("reasoning",
                        com.openai.core.JsonValue.from(Map.of("effort", "none")));
            }
        }

        return builder.build();
    }

    private static String mapReasoningEffort(com.campusclaw.ai.types.ThinkingLevel level) {
        return switch (level) {
            case MINIMAL -> "low";
            case LOW -> "low";
            case MEDIUM -> "medium";
            case HIGH -> "high";
            case XHIGH -> "high";
            default -> "medium";
        };
    }

    private void processStream(
            OpenAIClient client, ResponseCreateParams params,
            Model model, AssistantMessageEventStream eventStream) {

        var contentBlocks = new ArrayList<ContentBlock>();
        var accumulatedUsage = new long[]{0, 0, 0, 0}; // input, output, cacheRead, cacheWrite
        String[] responseId = {null};
        StopReason[] stopReason = {null};

        // Track output items by their outputIndex
        var textAccumulators = new HashMap<Integer, StringBuilder>();    // outputIndex → text
        var thinkingAccumulators = new HashMap<Integer, StringBuilder>(); // outputIndex → thinking
        var toolAccumulators = new HashMap<Integer, ToolCallAccumulator>(); // outputIndex → tool
        var outputIndexToContentIndex = new HashMap<Integer, Integer>();    // outputIndex → contentIndex

        try (StreamResponse<ResponseStreamEvent> response =
                     client.responses().createStreaming(params)) {

            response.stream().forEach(event -> {

                // response.created — capture response ID
                event.created().ifPresent(e ->
                        responseId[0] = e.response().id());

                // response.output_item.added — start new content block
                event.outputItemAdded().ifPresent(e ->
                        handleOutputItemAdded(e, model, responseId[0],
                                contentBlocks, accumulatedUsage, textAccumulators,
                                thinkingAccumulators, toolAccumulators,
                                outputIndexToContentIndex, eventStream));

                // response.output_text.delta — text streaming
                event.outputTextDelta().ifPresent(e -> {
                    int outputIdx = (int) e.outputIndex();
                    Integer contentIdx = outputIndexToContentIndex.get(outputIdx);
                    if (contentIdx == null) return;
                    var acc = textAccumulators.get(outputIdx);
                    if (acc != null) {
                        acc.append(e.delta());
                        contentBlocks.set(contentIdx,
                                new TextContent(acc.toString(), null));
                    }
                    eventStream.push(new AssistantMessageEvent.TextDeltaEvent(
                            contentIdx, e.delta(),
                            buildPartialMessage(model, responseId[0],
                                    contentBlocks, accumulatedUsage, null)));
                });

                // response.reasoning_summary_text.delta — thinking streaming
                event.reasoningSummaryTextDelta().ifPresent(e -> {
                    int outputIdx = (int) e.outputIndex();
                    Integer contentIdx = outputIndexToContentIndex.get(outputIdx);
                    if (contentIdx == null) return;
                    var acc = thinkingAccumulators.get(outputIdx);
                    if (acc != null) {
                        acc.append(e.delta());
                        contentBlocks.set(contentIdx,
                                new ThinkingContent(acc.toString(), null, false));
                    }
                    eventStream.push(new AssistantMessageEvent.ThinkingDeltaEvent(
                            contentIdx, e.delta(),
                            buildPartialMessage(model, responseId[0],
                                    contentBlocks, accumulatedUsage, null)));
                });

                // response.function_call_arguments.delta — tool arg streaming
                event.functionCallArgumentsDelta().ifPresent(e -> {
                    int outputIdx = (int) e.outputIndex();
                    Integer contentIdx = outputIndexToContentIndex.get(outputIdx);
                    if (contentIdx == null) return;
                    var acc = toolAccumulators.get(outputIdx);
                    if (acc != null) {
                        acc.arguments.append(e.delta());
                    }
                    eventStream.push(new AssistantMessageEvent.ToolCallDeltaEvent(
                            contentIdx, e.delta(),
                            buildPartialMessage(model, responseId[0],
                                    contentBlocks, accumulatedUsage, null)));
                });

                // response.output_item.done — finalize content block
                event.outputItemDone().ifPresent(e ->
                        handleOutputItemDone(e, model, responseId[0],
                                contentBlocks, accumulatedUsage, textAccumulators,
                                thinkingAccumulators, toolAccumulators,
                                outputIndexToContentIndex, eventStream));

                // response.completed — finalize usage and emit done
                event.completed().ifPresent(e -> {
                    var resp = e.response();
                    responseId[0] = resp.id();
                    resp.usage().ifPresent(u -> parseUsage(u, accumulatedUsage));
                    resp.status().ifPresent(s ->
                            stopReason[0] = mapResponseStatus(s, contentBlocks));
                });

                // response.failed — emit error
                event.failed().ifPresent(e -> {
                    stopReason[0] = StopReason.ERROR;
                });

                // response.incomplete — length stop
                event.incomplete().ifPresent(e -> {
                    stopReason[0] = StopReason.LENGTH;
                });

                // response.error — direct error
                event.error().ifPresent(e -> {
                    stopReason[0] = StopReason.ERROR;
                });
            });
        }

        // Emit final done event
        var finalStopReason = stopReason[0] != null ? stopReason[0] : StopReason.STOP;
        var finalMessage = buildPartialMessage(model, responseId[0],
                contentBlocks, accumulatedUsage, finalStopReason);
        eventStream.pushDone(finalStopReason, finalMessage);
    }

    private void handleOutputItemAdded(
            ResponseOutputItemAddedEvent e, Model model, String responseId,
            List<ContentBlock> contentBlocks, long[] usage,
            Map<Integer, StringBuilder> textAccumulators,
            Map<Integer, StringBuilder> thinkingAccumulators,
            Map<Integer, ToolCallAccumulator> toolAccumulators,
            Map<Integer, Integer> outputIndexToContentIndex,
            AssistantMessageEventStream eventStream) {

        int outputIdx = (int) e.outputIndex();
        var item = e.item();

        if (item.isMessage()) {
            // Text message output — start text block
            textAccumulators.put(outputIdx, new StringBuilder());
            contentBlocks.add(new TextContent("", null));
            int contentIdx = contentBlocks.size() - 1;
            outputIndexToContentIndex.put(outputIdx, contentIdx);
            eventStream.push(new AssistantMessageEvent.TextStartEvent(contentIdx,
                    buildPartialMessage(model, responseId, contentBlocks, usage, null)));

        } else if (item.isReasoning()) {
            // Reasoning/thinking output — start thinking block
            thinkingAccumulators.put(outputIdx, new StringBuilder());
            contentBlocks.add(new ThinkingContent("", null, false));
            int contentIdx = contentBlocks.size() - 1;
            outputIndexToContentIndex.put(outputIdx, contentIdx);
            eventStream.push(new AssistantMessageEvent.ThinkingStartEvent(contentIdx,
                    buildPartialMessage(model, responseId, contentBlocks, usage, null)));

        } else if (item.isFunctionCall()) {
            // Tool/function call output — start tool call block
            var fn = item.asFunctionCall();
            var acc = new ToolCallAccumulator();
            acc.id = fn.callId();
            acc.name = fn.name();
            toolAccumulators.put(outputIdx, acc);
            contentBlocks.add(new ToolCall(acc.id, acc.name, Map.of(), null));
            int contentIdx = contentBlocks.size() - 1;
            outputIndexToContentIndex.put(outputIdx, contentIdx);
            eventStream.push(new AssistantMessageEvent.ToolCallStartEvent(contentIdx,
                    buildPartialMessage(model, responseId, contentBlocks, usage, null)));
        }
    }

    private void handleOutputItemDone(
            ResponseOutputItemDoneEvent e, Model model, String responseId,
            List<ContentBlock> contentBlocks, long[] usage,
            Map<Integer, StringBuilder> textAccumulators,
            Map<Integer, StringBuilder> thinkingAccumulators,
            Map<Integer, ToolCallAccumulator> toolAccumulators,
            Map<Integer, Integer> outputIndexToContentIndex,
            AssistantMessageEventStream eventStream) {

        int outputIdx = (int) e.outputIndex();
        Integer contentIdx = outputIndexToContentIndex.get(outputIdx);
        if (contentIdx == null) return;
        var item = e.item();

        if (item.isMessage()) {
            String text = textAccumulators.containsKey(outputIdx)
                    ? textAccumulators.get(outputIdx).toString() : "";
            contentBlocks.set(contentIdx, new TextContent(text, null));
            eventStream.push(new AssistantMessageEvent.TextEndEvent(contentIdx, text,
                    buildPartialMessage(model, responseId, contentBlocks, usage, null)));

        } else if (item.isReasoning()) {
            String thinking = thinkingAccumulators.containsKey(outputIdx)
                    ? thinkingAccumulators.get(outputIdx).toString() : "";
            contentBlocks.set(contentIdx, new ThinkingContent(thinking, null, false));
            eventStream.push(new AssistantMessageEvent.ThinkingEndEvent(contentIdx, thinking,
                    buildPartialMessage(model, responseId, contentBlocks, usage, null)));

        } else if (item.isFunctionCall()) {
            var fn = item.asFunctionCall();
            var acc = toolAccumulators.get(outputIdx);
            String argsJson = acc != null ? acc.arguments.toString() : fn.arguments();
            Map<String, Object> args = parseToolArguments(argsJson);
            var toolCall = new ToolCall(fn.callId(), fn.name(), args, null);
            contentBlocks.set(contentIdx, toolCall);
            eventStream.push(new AssistantMessageEvent.ToolCallEndEvent(contentIdx, toolCall,
                    buildPartialMessage(model, responseId, contentBlocks, usage, null)));
        }
    }

    // -- Input conversion (Responses API uses input items instead of messages) --

    static List<ResponseInputItem> convertInputItems(List<Message> messages) {
        List<ResponseInputItem> result = new ArrayList<>();
        for (Message message : messages) {
            if (message instanceof UserMessage um) {
                result.add(convertUserInput(um));
            } else if (message instanceof AssistantMessage am) {
                convertAssistantInput(am, result);
            } else if (message instanceof ToolResultMessage tr) {
                result.add(convertToolResultInput(tr));
            }
        }
        return result;
    }

    private static ResponseInputItem convertUserInput(UserMessage um) {
        // Use EasyInputMessage with role=user and text content
        StringBuilder text = new StringBuilder();
        for (ContentBlock cb : um.content()) {
            if (cb instanceof TextContent tc) {
                text.append(tc.text());
            }
            // Images in user messages handled via content parts when needed
        }
        return ResponseInputItem.ofEasyInputMessage(
                EasyInputMessage.builder()
                        .role(EasyInputMessage.Role.USER)
                        .content(text.toString())
                        .build());
    }

    private static void convertAssistantInput(AssistantMessage am, List<ResponseInputItem> result) {
        // For assistant messages with text, emit as easy input message
        StringBuilder textContent = new StringBuilder();
        for (ContentBlock cb : am.content()) {
            if (cb instanceof TextContent tc) {
                textContent.append(tc.text());
            } else if (cb instanceof ToolCall tc) {
                // Tool calls become function_call input items
                result.add(ResponseInputItem.ofFunctionCall(
                        ResponseFunctionToolCall.builder()
                                .callId(tc.id())
                                .name(tc.name())
                                .arguments(serializeArguments(tc.arguments()))
                                .build()));
            }
            // ThinkingContent is dropped for cross-provider compatibility
        }
        if (!textContent.isEmpty()) {
            result.add(ResponseInputItem.ofEasyInputMessage(
                    EasyInputMessage.builder()
                            .role(EasyInputMessage.Role.ASSISTANT)
                            .content(textContent.toString())
                            .build()));
        }
    }

    private static ResponseInputItem convertToolResultInput(ToolResultMessage tr) {
        StringBuilder text = new StringBuilder();
        for (ContentBlock cb : tr.content()) {
            if (cb instanceof TextContent tc) {
                text.append(tc.text());
            }
        }
        return ResponseInputItem.ofFunctionCallOutput(
                ResponseInputItem.FunctionCallOutput.builder()
                        .callId(tr.toolCallId())
                        .output(text.toString())
                        .build());
    }

    // -- Tool conversion (Responses API uses its own Tool type) --

    static List<Tool> convertTools(List<com.campusclaw.ai.types.Tool> tools) {
        List<Tool> result = new ArrayList<>();
        for (var tool : tools) {
            var paramsBuilder = FunctionTool.Parameters.builder();
            if (tool.parameters() != null) {
                Map<String, Object> schemaMap = nodeToMap(tool.parameters());
                for (var entry : schemaMap.entrySet()) {
                    paramsBuilder.putAdditionalProperty(entry.getKey(),
                            com.openai.core.JsonValue.from(entry.getValue()));
                }
            }

            result.add(Tool.ofFunction(
                    FunctionTool.builder()
                            .name(tool.name())
                            .description(tool.description())
                            .parameters(paramsBuilder.build())
                            .strict(false)
                            .build()));
        }
        return result;
    }

    // -- Response status mapping --

    static StopReason mapResponseStatus(ResponseStatus status, List<ContentBlock> contentBlocks) {
        String val = status.asString();
        StopReason reason = switch (val) {
            case "completed" -> StopReason.STOP;
            case "incomplete" -> StopReason.LENGTH;
            case "failed", "cancelled" -> StopReason.ERROR;
            default -> StopReason.STOP; // in_progress, queued
        };

        // If the response completed normally but has tool calls, change to TOOL_USE
        if (reason == StopReason.STOP && hasToolCalls(contentBlocks)) {
            return StopReason.TOOL_USE;
        }
        return reason;
    }

    private static boolean hasToolCalls(List<ContentBlock> blocks) {
        for (ContentBlock block : blocks) {
            if (block instanceof ToolCall) return true;
        }
        return false;
    }

    // -- Usage parsing --

    static void parseUsage(ResponseUsage usage, long[] accumulated) {
        long cachedTokens = usage.inputTokensDetails().cachedTokens();
        long inputTokens = usage.inputTokens() - cachedTokens;
        accumulated[0] = Math.max(inputTokens, 0);
        accumulated[1] = usage.outputTokens();
        accumulated[2] = cachedTokens;
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
                Api.OPENAI_RESPONSES.value(),
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
     * Mutable accumulator for streaming tool call deltas.
     */
    private static class ToolCallAccumulator {
        String id;
        String name;
        final StringBuilder arguments = new StringBuilder();
    }
}
