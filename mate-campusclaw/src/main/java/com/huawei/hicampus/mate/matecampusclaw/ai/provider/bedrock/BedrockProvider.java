package com.huawei.hicampus.mate.matecampusclaw.ai.provider.bedrock;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import com.huawei.hicampus.mate.matecampusclaw.ai.provider.ApiProvider;
import com.huawei.hicampus.mate.matecampusclaw.ai.stream.AssistantMessageEvent;
import com.huawei.hicampus.mate.matecampusclaw.ai.stream.AssistantMessageEventStream;
import com.huawei.hicampus.mate.matecampusclaw.ai.types.Api;
import com.huawei.hicampus.mate.matecampusclaw.ai.types.AssistantMessage;
import com.huawei.hicampus.mate.matecampusclaw.ai.types.ContentBlock;
import com.huawei.hicampus.mate.matecampusclaw.ai.types.Context;
import com.huawei.hicampus.mate.matecampusclaw.ai.types.Cost;
import com.huawei.hicampus.mate.matecampusclaw.ai.types.ImageContent;
import com.huawei.hicampus.mate.matecampusclaw.ai.types.Message;
import com.huawei.hicampus.mate.matecampusclaw.ai.types.Model;
import com.huawei.hicampus.mate.matecampusclaw.ai.types.ModelCost;
import com.huawei.hicampus.mate.matecampusclaw.ai.types.Provider;
import com.huawei.hicampus.mate.matecampusclaw.ai.types.SimpleStreamOptions;
import com.huawei.hicampus.mate.matecampusclaw.ai.types.StopReason;
import com.huawei.hicampus.mate.matecampusclaw.ai.types.StreamOptions;
import com.huawei.hicampus.mate.matecampusclaw.ai.types.TextContent;
import com.huawei.hicampus.mate.matecampusclaw.ai.types.ThinkingContent;
import com.huawei.hicampus.mate.matecampusclaw.ai.types.ToolCall;
import com.huawei.hicampus.mate.matecampusclaw.ai.types.ToolResultMessage;
import com.huawei.hicampus.mate.matecampusclaw.ai.types.Usage;
import com.huawei.hicampus.mate.matecampusclaw.ai.types.UserMessage;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.springframework.stereotype.Component;

import jakarta.annotation.Nullable;
import software.amazon.awssdk.core.document.Document;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeAsyncClient;
import software.amazon.awssdk.services.bedrockruntime.model.ContentBlockDeltaEvent;
import software.amazon.awssdk.services.bedrockruntime.model.ContentBlockStartEvent;
import software.amazon.awssdk.services.bedrockruntime.model.ContentBlockStopEvent;
import software.amazon.awssdk.services.bedrockruntime.model.ConversationRole;
import software.amazon.awssdk.services.bedrockruntime.model.ConverseStreamRequest;
import software.amazon.awssdk.services.bedrockruntime.model.ConverseStreamResponseHandler;
import software.amazon.awssdk.services.bedrockruntime.model.ImageFormat;
import software.amazon.awssdk.services.bedrockruntime.model.InferenceConfiguration;
import software.amazon.awssdk.services.bedrockruntime.model.SystemContentBlock;
import software.amazon.awssdk.services.bedrockruntime.model.TokenUsage;
import software.amazon.awssdk.services.bedrockruntime.model.Tool;
import software.amazon.awssdk.services.bedrockruntime.model.ToolConfiguration;
import software.amazon.awssdk.services.bedrockruntime.model.ToolInputSchema;
import software.amazon.awssdk.services.bedrockruntime.model.ToolResultBlock;
import software.amazon.awssdk.services.bedrockruntime.model.ToolResultContentBlock;
import software.amazon.awssdk.services.bedrockruntime.model.ToolSpecification;
import software.amazon.awssdk.services.bedrockruntime.model.ToolUseBlock;

/**
 * {@link ApiProvider} implementation for the AWS Bedrock Converse Stream API.
 *
 * <p>Uses the official {@code software.amazon.awssdk:bedrockruntime} SDK with
 * {@link BedrockRuntimeAsyncClient#converseStream} for streaming requests,
 * mapping Bedrock SSE events to the unified {@link AssistantMessageEvent} protocol.
 *
 * <p>Supports Anthropic models (Claude on Bedrock) as well as other Bedrock
 * foundation models. AWS credentials are resolved from the default credential
 * chain unless overridden via environment variables.
 */
@Component
public class BedrockProvider implements ApiProvider {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String ENV_AWS_REGION = "AWS_REGION";
    private static final String DEFAULT_REGION = "us-east-1";

    @Override
    public Api getApi() {
        return Api.BEDROCK_CONVERSE_STREAM;
    }

    @Override
    public AssistantMessageEventStream stream(
            Model model, Context context, @Nullable StreamOptions options) {
        return doStream(model, context,
                options != null ? options.maxTokens() : null,
                options != null ? options.temperature() : null,
                null, null);
    }

    @Override
    public AssistantMessageEventStream streamSimple(
            Model model, Context context, @Nullable SimpleStreamOptions options) {
        return doStream(model, context,
                options != null ? options.maxTokens() : null,
                options != null ? options.temperature() : null,
                options != null ? options.reasoning() : null,
                options != null ? options.thinkingBudgets() : null);
    }

    private AssistantMessageEventStream doStream(
            Model model, Context context,
            @Nullable Integer maxTokens,
            @Nullable Double temperature,
            @Nullable com.huawei.hicampus.mate.matecampusclaw.ai.types.ThinkingLevel reasoning,
            @Nullable com.huawei.hicampus.mate.matecampusclaw.ai.types.ThinkingBudgets thinkingBudgets) {

        var eventStream = new AssistantMessageEventStream();

        Thread.ofVirtual().start(() -> {
            try {
                executeStream(model, context, maxTokens, temperature, reasoning, thinkingBudgets, eventStream);
            } catch (Exception e) {
                eventStream.error(e);
            }
        });

        return eventStream;
    }

    void executeStream(
            Model model, Context context,
            @Nullable Integer maxTokens,
            @Nullable Double temperature,
            @Nullable com.huawei.hicampus.mate.matecampusclaw.ai.types.ThinkingLevel reasoning,
            @Nullable com.huawei.hicampus.mate.matecampusclaw.ai.types.ThinkingBudgets thinkingBudgets,
            AssistantMessageEventStream eventStream) {

        BedrockRuntimeAsyncClient client = buildClient();

        try {
            ConverseStreamRequest request = buildRequest(model, context, maxTokens, temperature, reasoning, thinkingBudgets);
            processStream(client, request, model, eventStream);
        } catch (Exception e) {
            eventStream.error(e);
        } finally {
            client.close();
        }
    }

    BedrockRuntimeAsyncClient buildClient() {
        String region = System.getenv(ENV_AWS_REGION);
        if (region == null || region.isBlank()) {
            region = DEFAULT_REGION;
        }
        return BedrockRuntimeAsyncClient.builder()
                .region(Region.of(region))
                .build();
    }

    ConverseStreamRequest buildRequest(
            Model model, Context context,
            @Nullable Integer maxTokens,
            @Nullable Double temperature,
            @Nullable com.huawei.hicampus.mate.matecampusclaw.ai.types.ThinkingLevel reasoning,
            @Nullable com.huawei.hicampus.mate.matecampusclaw.ai.types.ThinkingBudgets thinkingBudgets) {

        int resolvedMaxTokens = maxTokens != null ? maxTokens
                : Math.min(model.maxTokens(), 32000);

        var builder = ConverseStreamRequest.builder()
                .modelId(model.id())
                .messages(convertMessages(context.messages()))
                .inferenceConfig(InferenceConfiguration.builder()
                        .maxTokens(resolvedMaxTokens)
                        .temperature(temperature != null ? temperature.floatValue() : null)
                        .build());

        // System prompt
        if (context.systemPrompt() != null && !context.systemPrompt().isBlank()) {
            builder.system(SystemContentBlock.fromText(context.systemPrompt()));
        }

        // Tools
        if (context.tools() != null && !context.tools().isEmpty()) {
            builder.toolConfig(ToolConfiguration.builder()
                    .tools(convertTools(context.tools()))
                    .build());
        }

        // Thinking / reasoning configuration via additionalModelRequestFields
        if (reasoning != null && reasoning != com.huawei.hicampus.mate.matecampusclaw.ai.types.ThinkingLevel.OFF
                && model.reasoning()) {
            Document thinkingDoc = buildThinkingConfig(model, reasoning, thinkingBudgets, resolvedMaxTokens);
            if (thinkingDoc != null) {
                builder.additionalModelRequestFields(thinkingDoc);
            }
        }

        return builder.build();
    }

    /**
     * Builds the additionalModelRequestFields Document for Bedrock thinking/reasoning.
     * Supports both adaptive thinking (Claude 4.6+) and budget-based thinking (older Claude).
     */
    private static Document buildThinkingConfig(
            Model model, com.huawei.hicampus.mate.matecampusclaw.ai.types.ThinkingLevel reasoning,
            @Nullable com.huawei.hicampus.mate.matecampusclaw.ai.types.ThinkingBudgets thinkingBudgets,
            int maxTokens) {

        boolean isClaude = model.id().contains("anthropic.claude") || model.id().contains("anthropic/claude");
        if (!isClaude) return null;

        boolean adaptive = supportsAdaptiveThinking(model.id());

        if (adaptive) {
            // Adaptive thinking: effort-based (Claude 4.6+)
            String effort = mapBedrockThinkingEffort(reasoning, model.id());
            return Document.fromMap(Map.of(
                "thinking", Document.fromMap(Map.of("type", Document.fromString("adaptive"))),
                "output_config", Document.fromMap(Map.of("effort", Document.fromString(effort)))
            ));
        } else {
            // Budget-based thinking (older Claude models)
            int budget = resolveBedrockBudget(reasoning, thinkingBudgets, maxTokens);
            return Document.fromMap(Map.of(
                "thinking", Document.fromMap(Map.of(
                    "type", Document.fromString("enabled"),
                    "budget_tokens", Document.fromNumber(budget)
                ))
            ));
        }
    }

    private static boolean supportsAdaptiveThinking(String modelId) {
        String lower = modelId.toLowerCase();
        return lower.contains("opus-4") || lower.contains("sonnet-4")
            || lower.contains("opus4") || lower.contains("sonnet4");
    }

    private static String mapBedrockThinkingEffort(com.huawei.hicampus.mate.matecampusclaw.ai.types.ThinkingLevel level, String modelId) {
        boolean isOpus = modelId.toLowerCase().contains("opus");
        return switch (level) {
            case MINIMAL, LOW -> "low";
            case MEDIUM -> "medium";
            case HIGH -> "high";
            case XHIGH -> isOpus ? "max" : "high";
            default -> "medium";
        };
    }

    private static int resolveBedrockBudget(
            com.huawei.hicampus.mate.matecampusclaw.ai.types.ThinkingLevel level,
            @Nullable com.huawei.hicampus.mate.matecampusclaw.ai.types.ThinkingBudgets budgets,
            int maxTokens) {
        if (budgets != null) {
            Integer custom = switch (level) {
                case MINIMAL -> budgets.minimal();
                case LOW -> budgets.low();
                case MEDIUM -> budgets.medium();
                case HIGH, XHIGH -> budgets.high();
                default -> null;
            };
            if (custom != null) return custom;
        }
        return switch (level) {
            case MINIMAL -> 1024;
            case LOW -> 2048;
            case MEDIUM -> 8192;
            case HIGH -> 16384;
            case XHIGH -> Math.max(maxTokens / 2, 16384);
            default -> 8192;
        };
    }

    private void processStream(
            BedrockRuntimeAsyncClient client, ConverseStreamRequest request,
            Model model, AssistantMessageEventStream eventStream) {

        var contentBlocks = new ArrayList<ContentBlock>();
        var accumulatedUsage = new long[]{0, 0, 0, 0}; // input, output, cacheRead, cacheWrite
        StopReason[] stopReason = {null};

        // Track content blocks by their Bedrock contentBlockIndex
        var textAccumulators = new HashMap<Integer, StringBuilder>();
        var thinkingAccumulators = new HashMap<Integer, StringBuilder>();
        var toolAccumulators = new HashMap<Integer, ToolCallAccumulator>();
        var blockIndexToContentIndex = new HashMap<Integer, Integer>();
        // Track block types: "text", "thinking", "tool"
        var blockTypes = new HashMap<Integer, String>();

        var visitor = ConverseStreamResponseHandler.Visitor.builder()
                .onContentBlockStart(e -> handleContentBlockStart(
                        e, model, contentBlocks, accumulatedUsage,
                        textAccumulators, thinkingAccumulators, toolAccumulators,
                        blockIndexToContentIndex, blockTypes, eventStream))
                .onContentBlockDelta(e -> handleContentBlockDelta(
                        e, model, contentBlocks, accumulatedUsage,
                        textAccumulators, thinkingAccumulators, toolAccumulators,
                        blockIndexToContentIndex, blockTypes, eventStream))
                .onContentBlockStop(e -> handleContentBlockStop(
                        e, model, contentBlocks, accumulatedUsage,
                        textAccumulators, thinkingAccumulators, toolAccumulators,
                        blockIndexToContentIndex, blockTypes, eventStream))
                .onMessageStop(e ->
                        stopReason[0] = mapStopReason(e.stopReasonAsString()))
                .onMetadata(e -> {
                    TokenUsage usage = e.usage();
                    if (usage != null) {
                        parseUsage(usage, accumulatedUsage);
                    }
                })
                .build();

        var handler = ConverseStreamResponseHandler.builder()
                .subscriber(visitor)
                .build();

        CompletableFuture<Void> future = client.converseStream(request, handler);

        try {
            future.join();
        } catch (Exception e) {
            Throwable cause = e.getCause() != null ? e.getCause() : e;
            eventStream.error(cause instanceof Exception ex
                    ? ex : new RuntimeException(cause));
            return;
        }

        // Emit final done event
        var finalStopReason = stopReason[0] != null ? stopReason[0] : StopReason.STOP;
        var finalMessage = buildPartialMessage(model, null,
                contentBlocks, accumulatedUsage, finalStopReason);
        eventStream.pushDone(finalStopReason, finalMessage);
    }

    private void handleContentBlockStart(
            ContentBlockStartEvent e, Model model,
            List<ContentBlock> contentBlocks, long[] usage,
            Map<Integer, StringBuilder> textAccumulators,
            Map<Integer, StringBuilder> thinkingAccumulators,
            Map<Integer, ToolCallAccumulator> toolAccumulators,
            Map<Integer, Integer> blockIndexToContentIndex,
            Map<Integer, String> blockTypes,
            AssistantMessageEventStream eventStream) {

        int blockIdx = e.contentBlockIndex();
        var start = e.start();

        if (start != null && start.toolUse() != null) {
            // Tool use block
            var toolStart = start.toolUse();
            var acc = new ToolCallAccumulator();
            acc.id = toolStart.toolUseId();
            acc.name = toolStart.name();
            toolAccumulators.put(blockIdx, acc);
            blockTypes.put(blockIdx, "tool");

            contentBlocks.add(new ToolCall(acc.id, acc.name, Map.of(), null));
            int contentIdx = contentBlocks.size() - 1;
            blockIndexToContentIndex.put(blockIdx, contentIdx);
            eventStream.push(new AssistantMessageEvent.ToolCallStartEvent(contentIdx,
                    buildPartialMessage(model, null, contentBlocks, usage, null)));
        }
        // Text and reasoning blocks don't have a start payload in Bedrock;
        // we detect them on the first delta event.
    }

    private void handleContentBlockDelta(
            ContentBlockDeltaEvent e, Model model,
            List<ContentBlock> contentBlocks, long[] usage,
            Map<Integer, StringBuilder> textAccumulators,
            Map<Integer, StringBuilder> thinkingAccumulators,
            Map<Integer, ToolCallAccumulator> toolAccumulators,
            Map<Integer, Integer> blockIndexToContentIndex,
            Map<Integer, String> blockTypes,
            AssistantMessageEventStream eventStream) {

        int blockIdx = e.contentBlockIndex();
        var delta = e.delta();
        if (delta == null) return;

        // Reasoning content delta
        if (delta.reasoningContent() != null && delta.reasoningContent().text() != null) {
            String thinkingText = delta.reasoningContent().text();
            if (!blockTypes.containsKey(blockIdx)) {
                // First thinking delta — create the block
                blockTypes.put(blockIdx, "thinking");
                thinkingAccumulators.put(blockIdx, new StringBuilder());
                contentBlocks.add(new ThinkingContent("", null, false));
                int contentIdx = contentBlocks.size() - 1;
                blockIndexToContentIndex.put(blockIdx, contentIdx);
                eventStream.push(new AssistantMessageEvent.ThinkingStartEvent(contentIdx,
                        buildPartialMessage(model, null, contentBlocks, usage, null)));
            }
            var acc = thinkingAccumulators.get(blockIdx);
            if (acc != null) {
                acc.append(thinkingText);
                Integer contentIdx = blockIndexToContentIndex.get(blockIdx);
                if (contentIdx != null) {
                    contentBlocks.set(contentIdx,
                            new ThinkingContent(acc.toString(), null, false));
                    eventStream.push(new AssistantMessageEvent.ThinkingDeltaEvent(
                            contentIdx, thinkingText,
                            buildPartialMessage(model, null, contentBlocks, usage, null)));
                }
            }
            return;
        }

        // Text content delta
        if (delta.text() != null) {
            String text = delta.text();
            if (!blockTypes.containsKey(blockIdx)) {
                // First text delta — create the block
                blockTypes.put(blockIdx, "text");
                textAccumulators.put(blockIdx, new StringBuilder());
                contentBlocks.add(new TextContent("", null));
                int contentIdx = contentBlocks.size() - 1;
                blockIndexToContentIndex.put(blockIdx, contentIdx);
                eventStream.push(new AssistantMessageEvent.TextStartEvent(contentIdx,
                        buildPartialMessage(model, null, contentBlocks, usage, null)));
            }
            var acc = textAccumulators.get(blockIdx);
            if (acc != null) {
                acc.append(text);
                Integer contentIdx = blockIndexToContentIndex.get(blockIdx);
                if (contentIdx != null) {
                    contentBlocks.set(contentIdx,
                            new TextContent(acc.toString(), null));
                    eventStream.push(new AssistantMessageEvent.TextDeltaEvent(
                            contentIdx, text,
                            buildPartialMessage(model, null, contentBlocks, usage, null)));
                }
            }
            return;
        }

        // Tool use input delta
        if (delta.toolUse() != null && delta.toolUse().input() != null) {
            var acc = toolAccumulators.get(blockIdx);
            if (acc != null) {
                String inputDelta = delta.toolUse().input();
                acc.arguments.append(inputDelta);
                Integer contentIdx = blockIndexToContentIndex.get(blockIdx);
                if (contentIdx != null) {
                    eventStream.push(new AssistantMessageEvent.ToolCallDeltaEvent(
                            contentIdx, inputDelta,
                            buildPartialMessage(model, null, contentBlocks, usage, null)));
                }
            }
        }
    }

    private void handleContentBlockStop(
            ContentBlockStopEvent e, Model model,
            List<ContentBlock> contentBlocks, long[] usage,
            Map<Integer, StringBuilder> textAccumulators,
            Map<Integer, StringBuilder> thinkingAccumulators,
            Map<Integer, ToolCallAccumulator> toolAccumulators,
            Map<Integer, Integer> blockIndexToContentIndex,
            Map<Integer, String> blockTypes,
            AssistantMessageEventStream eventStream) {

        int blockIdx = e.contentBlockIndex();
        Integer contentIdx = blockIndexToContentIndex.get(blockIdx);
        if (contentIdx == null) return;
        String type = blockTypes.get(blockIdx);
        if (type == null) return;

        switch (type) {
            case "text" -> {
                String text = textAccumulators.containsKey(blockIdx)
                        ? textAccumulators.get(blockIdx).toString() : "";
                contentBlocks.set(contentIdx, new TextContent(text, null));
                eventStream.push(new AssistantMessageEvent.TextEndEvent(contentIdx, text,
                        buildPartialMessage(model, null, contentBlocks, usage, null)));
            }
            case "thinking" -> {
                String thinking = thinkingAccumulators.containsKey(blockIdx)
                        ? thinkingAccumulators.get(blockIdx).toString() : "";
                contentBlocks.set(contentIdx, new ThinkingContent(thinking, null, false));
                eventStream.push(new AssistantMessageEvent.ThinkingEndEvent(contentIdx, thinking,
                        buildPartialMessage(model, null, contentBlocks, usage, null)));
            }
            case "tool" -> {
                var acc = toolAccumulators.get(blockIdx);
                Map<String, Object> args = acc != null
                        ? parseToolArguments(acc.arguments.toString()) : Map.of();
                String id = acc != null && acc.id != null ? acc.id : "";
                String name = acc != null && acc.name != null ? acc.name : "";
                var toolCall = new ToolCall(id, name, args, null);
                contentBlocks.set(contentIdx, toolCall);
                eventStream.push(new AssistantMessageEvent.ToolCallEndEvent(contentIdx, toolCall,
                        buildPartialMessage(model, null, contentBlocks, usage, null)));
            }
        }
    }

    // -- Message conversion --

    static List<software.amazon.awssdk.services.bedrockruntime.model.Message>
    convertMessages(List<Message> messages) {
        List<software.amazon.awssdk.services.bedrockruntime.model.Message> result = new ArrayList<>();
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

    private static software.amazon.awssdk.services.bedrockruntime.model.Message
    convertUserMessage(UserMessage um) {
        var blocks = new ArrayList<software.amazon.awssdk.services.bedrockruntime.model.ContentBlock>();
        for (ContentBlock cb : um.content()) {
            if (cb instanceof TextContent tc) {
                blocks.add(software.amazon.awssdk.services.bedrockruntime.model.ContentBlock
                        .fromText(tc.text()));
            } else if (cb instanceof ImageContent ic) {
                blocks.add(software.amazon.awssdk.services.bedrockruntime.model.ContentBlock
                        .fromImage(software.amazon.awssdk.services.bedrockruntime.model.ImageBlock.builder()
                                .format(mapImageFormat(ic.mimeType()))
                                .source(software.amazon.awssdk.services.bedrockruntime.model.ImageSource.builder()
                                        .bytes(software.amazon.awssdk.core.SdkBytes
                                                .fromByteArray(java.util.Base64.getDecoder().decode(ic.data())))
                                        .build())
                                .build()));
            }
        }
        return software.amazon.awssdk.services.bedrockruntime.model.Message.builder()
                .role(ConversationRole.USER)
                .content(blocks)
                .build();
    }

    private static software.amazon.awssdk.services.bedrockruntime.model.Message
    convertAssistantMessage(AssistantMessage am) {
        var blocks = new ArrayList<software.amazon.awssdk.services.bedrockruntime.model.ContentBlock>();
        boolean isAnthropicModel = am.model() != null
            && (am.model().contains("anthropic.claude") || am.model().contains("anthropic/claude"));

        for (ContentBlock cb : am.content()) {
            if (cb instanceof TextContent tc) {
                if (!tc.text().isBlank()) {
                    blocks.add(software.amazon.awssdk.services.bedrockruntime.model.ContentBlock
                            .fromText(tc.text()));
                }
            } else if (cb instanceof ToolCall tc) {
                blocks.add(software.amazon.awssdk.services.bedrockruntime.model.ContentBlock
                        .fromToolUse(ToolUseBlock.builder()
                                .toolUseId(tc.id())
                                .name(tc.name())
                                .input(mapToDocument(tc.arguments()))
                                .build()));
            } else if (cb instanceof ThinkingContent tc) {
                if (tc.thinking() == null || tc.thinking().isBlank()) continue;
                // Build reasoningContent block
                var reasoningTextBuilder = software.amazon.awssdk.services.bedrockruntime.model
                    .ReasoningTextBlock.builder().text(tc.thinking());
                // Only include signature for Anthropic Claude models
                if (isAnthropicModel && tc.thinkingSignature() != null
                        && !tc.thinkingSignature().isBlank()) {
                    reasoningTextBuilder.signature(tc.thinkingSignature());
                } else if (isAnthropicModel) {
                    // Missing signature on Anthropic model — fall back to plain text
                    blocks.add(software.amazon.awssdk.services.bedrockruntime.model.ContentBlock
                            .fromText(tc.thinking()));
                    continue;
                }
                blocks.add(software.amazon.awssdk.services.bedrockruntime.model.ContentBlock.builder()
                    .reasoningContent(software.amazon.awssdk.services.bedrockruntime.model
                        .ReasoningContentBlock.fromReasoningText(reasoningTextBuilder.build()))
                    .build());
            }
        }
        return software.amazon.awssdk.services.bedrockruntime.model.Message.builder()
                .role(ConversationRole.ASSISTANT)
                .content(blocks)
                .build();
    }

    private static software.amazon.awssdk.services.bedrockruntime.model.Message
    convertToolResult(ToolResultMessage tr) {
        var contentBlocks = new ArrayList<ToolResultContentBlock>();
        for (ContentBlock cb : tr.content()) {
            if (cb instanceof TextContent tc) {
                contentBlocks.add(ToolResultContentBlock.fromText(tc.text()));
            } else if (cb instanceof ImageContent ic) {
                contentBlocks.add(ToolResultContentBlock.fromImage(
                    software.amazon.awssdk.services.bedrockruntime.model.ImageBlock.builder()
                        .format(mapImageFormat(ic.mimeType()))
                        .source(software.amazon.awssdk.services.bedrockruntime.model.ImageSource.builder()
                            .bytes(software.amazon.awssdk.core.SdkBytes
                                .fromByteArray(java.util.Base64.getDecoder().decode(ic.data())))
                            .build())
                        .build()));
            }
        }

        var toolResult = ToolResultBlock.builder()
                .toolUseId(tr.toolCallId())
                .content(contentBlocks)
                .build();

        return software.amazon.awssdk.services.bedrockruntime.model.Message.builder()
                .role(ConversationRole.USER)
                .content(software.amazon.awssdk.services.bedrockruntime.model.ContentBlock
                        .fromToolResult(toolResult))
                .build();
    }

    // -- Tool conversion --

    static List<Tool> convertTools(List<com.huawei.hicampus.mate.matecampusclaw.ai.types.Tool> tools) {
        List<Tool> result = new ArrayList<>();
        for (var tool : tools) {
            Document schemaDoc = tool.parameters() != null
                    ? jsonNodeToDocument(tool.parameters()) : Document.fromMap(Map.of());

            result.add(Tool.builder()
                    .toolSpec(ToolSpecification.builder()
                            .name(tool.name())
                            .description(tool.description())
                            .inputSchema(ToolInputSchema.builder()
                                    .json(schemaDoc)
                                    .build())
                            .build())
                    .build());
        }
        return result;
    }

    // -- Stop reason mapping --

    static StopReason mapStopReason(String reason) {
        if (reason == null) return StopReason.STOP;
        return switch (reason) {
            case "end_turn", "stop_sequence" -> StopReason.STOP;
            case "tool_use" -> StopReason.TOOL_USE;
            case "max_tokens", "model_context_window_exceeded" -> StopReason.LENGTH;
            case "content_filtered", "guardrail_intervened",
                 "malformed_model_output", "malformed_tool_use" -> StopReason.ERROR;
            default -> StopReason.STOP;
        };
    }

    // -- Usage parsing --

    static void parseUsage(TokenUsage usage, long[] accumulated) {
        int inputTokens = usage.inputTokens() != null ? usage.inputTokens() : 0;
        int outputTokens = usage.outputTokens() != null ? usage.outputTokens() : 0;
        int cacheRead = usage.cacheReadInputTokens() != null ? usage.cacheReadInputTokens() : 0;
        int cacheWrite = usage.cacheWriteInputTokens() != null ? usage.cacheWriteInputTokens() : 0;

        // Bedrock includes cached tokens in inputTokens, subtract
        accumulated[0] = Math.max(inputTokens - cacheRead, 0);
        accumulated[1] = outputTokens;
        accumulated[2] = cacheRead;
        accumulated[3] = cacheWrite;
    }

    // -- Utility methods --

    private AssistantMessage buildPartialMessage(
            Model model, @Nullable String responseId,
            List<ContentBlock> contentBlocks, long[] usage,
            @Nullable StopReason stopReason) {

        var piUsage = new Usage(
                (int) usage[0], (int) usage[1],
                (int) usage[2], (int) usage[3],
                (int) (usage[0] + usage[1] + usage[2]),
                computeCost(model.cost(), usage));

        return new AssistantMessage(
                List.copyOf(contentBlocks),
                Api.BEDROCK_CONVERSE_STREAM.value(),
                Provider.AMAZON_BEDROCK.value(),
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
    static Map<String, Object> parseToolArguments(String json) {
        if (json == null || json.isBlank()) return Map.of();
        try {
            return MAPPER.readValue(json, new TypeReference<Map<String, Object>>() {});
        } catch (Exception e) {
            return Map.of();
        }
    }

    @SuppressWarnings("unchecked")
    static Document mapToDocument(Map<String, Object> map) {
        if (map == null || map.isEmpty()) return Document.fromMap(Map.of());
        Map<String, Document> docMap = new LinkedHashMap<>();
        for (var entry : map.entrySet()) {
            docMap.put(entry.getKey(), objectToDocument(entry.getValue()));
        }
        return Document.fromMap(docMap);
    }

    @SuppressWarnings("unchecked")
    static Document objectToDocument(Object value) {
        if (value == null) return Document.fromNull();
        if (value instanceof String s) return Document.fromString(s);
        if (value instanceof Boolean b) return Document.fromBoolean(b);
        if (value instanceof Integer i) return Document.fromNumber(i);
        if (value instanceof Long l) return Document.fromNumber(l);
        if (value instanceof Double d) return Document.fromNumber(d);
        if (value instanceof Float f) return Document.fromNumber(f);
        if (value instanceof Number n) return Document.fromNumber(n.doubleValue());
        if (value instanceof Map<?, ?> m) {
            Map<String, Document> docMap = new LinkedHashMap<>();
            for (var entry : ((Map<String, Object>) m).entrySet()) {
                docMap.put(entry.getKey(), objectToDocument(entry.getValue()));
            }
            return Document.fromMap(docMap);
        }
        if (value instanceof List<?> list) {
            List<Document> docs = new ArrayList<>();
            for (Object item : list) {
                docs.add(objectToDocument(item));
            }
            return Document.fromList(docs);
        }
        return Document.fromString(value.toString());
    }

    @SuppressWarnings("unchecked")
    static Document jsonNodeToDocument(JsonNode node) {
        if (node == null || node.isNull()) return Document.fromNull();
        try {
            Map<String, Object> map = MAPPER.treeToValue(node, Map.class);
            return mapToDocument(map);
        } catch (Exception e) {
            return Document.fromMap(Map.of());
        }
    }

    private static ImageFormat mapImageFormat(String mimeType) {
        return switch (mimeType) {
            case "image/jpeg" -> ImageFormat.JPEG;
            case "image/png" -> ImageFormat.PNG;
            case "image/gif" -> ImageFormat.GIF;
            case "image/webp" -> ImageFormat.WEBP;
            default -> ImageFormat.JPEG; // Fallback
        };
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
