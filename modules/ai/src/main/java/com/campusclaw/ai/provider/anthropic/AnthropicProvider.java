package com.campusclaw.ai.provider.anthropic;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import com.anthropic.core.http.StreamResponse;
import com.anthropic.models.messages.Base64ImageSource;
import com.anthropic.models.messages.CacheControlEphemeral;
import com.anthropic.models.messages.ContentBlockParam;
import com.anthropic.models.messages.ImageBlockParam;
import com.anthropic.models.messages.MessageCreateParams;
import com.anthropic.models.messages.MessageParam;
import com.anthropic.models.messages.OutputConfig;
import com.anthropic.models.messages.RawContentBlockDelta;
import com.anthropic.models.messages.RawContentBlockDeltaEvent;
import com.anthropic.models.messages.RawContentBlockStartEvent;
import com.anthropic.models.messages.RawMessageStreamEvent;
import com.anthropic.models.messages.RedactedThinkingBlockParam;
import com.anthropic.models.messages.TextBlockParam;
import com.anthropic.models.messages.ThinkingBlockParam;
import com.anthropic.models.messages.ThinkingConfigAdaptive;
import com.anthropic.models.messages.ThinkingConfigEnabled;
import com.anthropic.models.messages.ThinkingConfigParam;
import com.anthropic.models.messages.ToolResultBlockParam;
import com.anthropic.models.messages.ToolUnion;
import com.anthropic.models.messages.ToolUseBlockParam;
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
import com.campusclaw.ai.types.SimpleStreamOptions;
import com.campusclaw.ai.types.StopReason;
import com.campusclaw.ai.types.StreamOptions;
import com.campusclaw.ai.types.TextContent;
import com.campusclaw.ai.types.ThinkingBudgets;
import com.campusclaw.ai.types.ThinkingContent;
import com.campusclaw.ai.types.ThinkingLevel;
import com.campusclaw.ai.types.Tool;
import com.campusclaw.ai.types.ToolCall;
import com.campusclaw.ai.types.Usage;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.springframework.stereotype.Component;

import jakarta.annotation.Nullable;

/**
 * {@link ApiProvider} implementation for the Anthropic Messages API.
 *
 * <p>Uses the official {@code com.anthropic:anthropic-java} SDK for streaming
 * requests, mapping Anthropic SSE events to the unified
 * {@link AssistantMessageEvent} protocol.
 */
@Component
public class AnthropicProvider implements ApiProvider {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String ENV_API_KEY = "ANTHROPIC_API_KEY";

    /** Resolve API key based on the model's provider. */
    private static String resolveApiKeyForProvider(Model model) {
        // Check model-embedded API key first (for custom models)
        if (model.apiKey() != null && !model.apiKey().isBlank()) return model.apiKey();
        String providerKey = switch (model.provider()) {
            case KIMI_CODING -> System.getenv("KIMI_API_KEY");
            case MINIMAX -> System.getenv("MINIMAX_API_KEY");
            case MINIMAX_CN -> System.getenv("MINIMAX_CN_API_KEY");
            case GITHUB_COPILOT -> System.getenv("COPILOT_GITHUB_TOKEN");
            case AZURE_OPENAI -> System.getenv("AZURE_OPENAI_API_KEY");
            default -> null;
        };
        if (providerKey != null && !providerKey.isBlank()) return providerKey;
        return System.getenv(ENV_API_KEY);
    }

    @Override
    public Api getApi() {
        return Api.ANTHROPIC_MESSAGES;
    }

    @Override
    public AssistantMessageEventStream stream(
            Model model, Context context, @Nullable StreamOptions options) {
        return doStream(model, context,
                options != null ? options.apiKey() : null,
                options != null ? options.maxTokens() : null,
                options != null ? options.temperature() : null,
                null, null);
    }

    @Override
    public AssistantMessageEventStream streamSimple(
            Model model, Context context, @Nullable SimpleStreamOptions options) {
        return doStream(model, context,
                options != null ? options.apiKey() : null,
                options != null ? options.maxTokens() : null,
                options != null ? options.temperature() : null,
                options != null ? options.reasoning() : null,
                options != null ? options.thinkingBudgets() : null);
    }

    private AssistantMessageEventStream doStream(
            Model model, Context context,
            @Nullable String apiKey,
            @Nullable Integer maxTokens,
            @Nullable Double temperature,
            @Nullable ThinkingLevel reasoning,
            @Nullable ThinkingBudgets thinkingBudgets) {

        var eventStream = new AssistantMessageEventStream();

        Thread.ofVirtual().start(() -> {
            try {
                executeStream(model, context, apiKey, maxTokens, temperature,
                        reasoning, thinkingBudgets, eventStream);
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
            @Nullable ThinkingBudgets thinkingBudgets,
            AssistantMessageEventStream eventStream) {

        String resolvedApiKey = apiKey != null ? apiKey : resolveApiKeyForProvider(model);
        if (resolvedApiKey == null || resolvedApiKey.isBlank()) {
            String envHint = switch (model.provider()) {
                case KIMI_CODING -> "KIMI_API_KEY";
                case MINIMAX -> "MINIMAX_API_KEY";
                case MINIMAX_CN -> "MINIMAX_CN_API_KEY";
                case GITHUB_COPILOT -> "COPILOT_GITHUB_TOKEN";
                case AZURE_OPENAI -> "AZURE_OPENAI_API_KEY";
                default -> "ANTHROPIC_API_KEY";
            };
            eventStream.error(new IllegalStateException(
                    "API key not found for " + model.provider().value() + ". Set " + envHint + " or pass via StreamOptions."));
            return;
        }

        AnthropicClient client = buildClient(resolvedApiKey, model.baseUrl());

        try {
            MessageCreateParams params = buildParams(model, context, maxTokens, temperature,
                    reasoning, thinkingBudgets);

            processStream(client, params, model, eventStream);
        } catch (Exception e) {
            eventStream.error(e);
        } finally {
            client.close();
        }
    }

    AnthropicClient buildClient(String apiKey, String baseUrl) {
        var builder = AnthropicOkHttpClient.builder()
                .apiKey(apiKey)
                .timeout(com.anthropic.core.Timeout.builder()
                        .connect(java.time.Duration.ofSeconds(15))
                        .read(java.time.Duration.ofMinutes(10))
                        .write(java.time.Duration.ofSeconds(30))
                        .build());
        if (baseUrl != null && !baseUrl.isBlank()) {
            builder.baseUrl(baseUrl);
        }
        return builder.build();
    }

    MessageCreateParams buildParams(
            Model model, Context context,
            @Nullable Integer maxTokens,
            @Nullable Double temperature,
            @Nullable ThinkingLevel reasoning,
            @Nullable ThinkingBudgets thinkingBudgets) {

        int resolvedMaxTokens = maxTokens != null ? maxTokens : model.maxTokens();

        boolean enableCaching = shouldEnableCaching(model.baseUrl());
        var builder = MessageCreateParams.builder()
                .model(com.anthropic.models.messages.Model.of(model.id()))
                .maxTokens(resolvedMaxTokens)
                .messages(convertMessages(context.messages(), enableCaching));

        // System prompt (with cache control for Anthropic API)
        if (context.systemPrompt() != null && !context.systemPrompt().isBlank()) {
            var sysBlock = TextBlockParam.builder().text(context.systemPrompt());
            if (shouldEnableCaching(model.baseUrl())) {
                sysBlock.cacheControl(CacheControlEphemeral.builder().build());
            }
            builder.system(MessageCreateParams.System.ofTextBlockParams(
                    List.of(sysBlock.build())));
        }

        // Tools
        if (context.tools() != null && !context.tools().isEmpty()) {
            builder.tools(convertTools(context.tools()));
        }

        // Extended thinking — adaptive for Opus 4.6/Sonnet 4.6, budget-based for older
        boolean thinkingEnabled = reasoning != null && reasoning != ThinkingLevel.OFF && model.reasoning();
        if (thinkingEnabled) {
            if (supportsAdaptiveThinking(model.id())) {
                builder.thinking(ThinkingConfigParam.ofAdaptive(
                        ThinkingConfigAdaptive.builder().build()));
                String effort = mapToAnthropicEffort(reasoning, model.id());
                builder.outputConfig(OutputConfig.builder()
                        .effort(OutputConfig.Effort.of(effort))
                        .build());
            } else {
                long budget = resolveBudget(reasoning, thinkingBudgets, resolvedMaxTokens);
                builder.thinking(ThinkingConfigParam.ofEnabled(
                        ThinkingConfigEnabled.builder().budgetTokens(budget).build()));
            }
        }

        // Temperature is incompatible with extended thinking
        if (temperature != null && !thinkingEnabled) {
            builder.temperature(temperature);
        }

        return builder.build();
    }

    private void processStream(
            AnthropicClient client, MessageCreateParams params,
            Model model, AssistantMessageEventStream eventStream) {

        // Mutable state for tracking partial message
        var contentBlocks = new ArrayList<ContentBlock>();
        var accumulatedUsage = new long[]{0, 0, 0, 0}; // input, output, cacheRead, cacheWrite
        String[] responseId = {null};
        var textAccumulator = new HashMap<Integer, StringBuilder>();
        var thinkingAccumulator = new HashMap<Integer, StringBuilder>();
        var toolJsonAccumulator = new HashMap<Integer, StringBuilder>();
        var blockTypes = new HashMap<Integer, String>();
        var toolMeta = new HashMap<Integer, String[]>(); // [id, name]
        var signatureAcc = new HashMap<Integer, StringBuilder>();
        StopReason[] stopReason = {null};

        try (StreamResponse<RawMessageStreamEvent> response = client.messages().createStreaming(params)) {
            response.stream().forEach(event -> {
                if (event.isMessageStart()) {
                    var msg = event.asMessageStart().message();
                    responseId[0] = msg.id();
                    var usage = msg.usage();
                    accumulatedUsage[0] = usage.inputTokens();
                    accumulatedUsage[1] = usage.outputTokens();
                    accumulatedUsage[2] = usage.cacheReadInputTokens().orElse(0L);
                    accumulatedUsage[3] = usage.cacheCreationInputTokens().orElse(0L);

                    var partial = buildPartialMessage(model, responseId[0],
                            contentBlocks, accumulatedUsage, null);
                    eventStream.push(new AssistantMessageEvent.StartEvent(partial));

                } else if (event.isContentBlockStart()) {
                    handleContentBlockStart(event.asContentBlockStart(), model, responseId[0],
                            contentBlocks, accumulatedUsage, blockTypes, textAccumulator,
                            thinkingAccumulator, toolJsonAccumulator, toolMeta, eventStream);

                } else if (event.isContentBlockDelta()) {
                    handleContentBlockDelta(event.asContentBlockDelta(), model, responseId[0],
                            contentBlocks, accumulatedUsage, blockTypes, textAccumulator,
                            thinkingAccumulator, toolJsonAccumulator, signatureAcc, eventStream);

                } else if (event.isContentBlockStop()) {
                    int idx = (int) event.asContentBlockStop().index();
                    handleContentBlockStop(idx, model, responseId[0],
                            contentBlocks, accumulatedUsage, blockTypes, textAccumulator,
                            thinkingAccumulator, toolJsonAccumulator, toolMeta, signatureAcc, eventStream);

                } else if (event.isMessageDelta()) {
                    var e = event.asMessageDelta();
                    accumulatedUsage[1] = e.usage().outputTokens();
                    e.delta().stopReason().ifPresent(sr -> stopReason[0] = mapStopReason(sr));

                } else if (event.isMessageStop()) {
                    var finalStopReason = stopReason[0] != null ? stopReason[0] : StopReason.STOP;
                    var finalMessage = buildPartialMessage(model, responseId[0],
                            contentBlocks, accumulatedUsage, finalStopReason);
                    eventStream.pushDone(finalStopReason, finalMessage);
                }
            });
        }
    }

    private void handleContentBlockStart(
            RawContentBlockStartEvent e, Model model, String responseId,
            List<ContentBlock> contentBlocks, long[] usage,
            Map<Integer, String> blockTypes,
            Map<Integer, StringBuilder> textAcc,
            Map<Integer, StringBuilder> thinkingAcc,
            Map<Integer, StringBuilder> toolJsonAcc,
            Map<Integer, String[]> toolMeta,
            AssistantMessageEventStream eventStream) {

        int idx = (int) e.index();
        var cb = e.contentBlock();

        if (cb.isText()) {
            blockTypes.put(idx, "text");
            textAcc.put(idx, new StringBuilder());
            contentBlocks.add(new TextContent("", null));
            eventStream.push(new AssistantMessageEvent.TextStartEvent(idx,
                    buildPartialMessage(model, responseId, contentBlocks, usage, null)));

        } else if (cb.isThinking()) {
            blockTypes.put(idx, "thinking");
            thinkingAcc.put(idx, new StringBuilder());
            contentBlocks.add(new ThinkingContent("", null, false));
            eventStream.push(new AssistantMessageEvent.ThinkingStartEvent(idx,
                    buildPartialMessage(model, responseId, contentBlocks, usage, null)));

        } else if (cb.isRedactedThinking()) {
            // Redacted thinking: encrypted payload stored as signature
            var rt = cb.asRedactedThinking();
            blockTypes.put(idx, "redacted_thinking");
            contentBlocks.add(new ThinkingContent("[Reasoning redacted]", rt.data(), true));
            eventStream.push(new AssistantMessageEvent.ThinkingStartEvent(idx,
                    buildPartialMessage(model, responseId, contentBlocks, usage, null)));

        } else if (cb.isToolUse()) {
            var tu = cb.asToolUse();
            blockTypes.put(idx, "tool_use");
            toolJsonAcc.put(idx, new StringBuilder());
            toolMeta.put(idx, new String[]{tu.id(), tu.name()});
            contentBlocks.add(new ToolCall(tu.id(), tu.name(), Map.of(), null));
            eventStream.push(new AssistantMessageEvent.ToolCallStartEvent(idx,
                    buildPartialMessage(model, responseId, contentBlocks, usage, null)));
        }
    }

    private void handleContentBlockDelta(
            RawContentBlockDeltaEvent e, Model model, String responseId,
            List<ContentBlock> contentBlocks, long[] usage,
            Map<Integer, String> blockTypes,
            Map<Integer, StringBuilder> textAcc,
            Map<Integer, StringBuilder> thinkingAcc,
            Map<Integer, StringBuilder> toolJsonAcc,
            Map<Integer, StringBuilder> signatureAcc,
            AssistantMessageEventStream eventStream) {

        int idx = (int) e.index();
        RawContentBlockDelta delta = e.delta();

        if (delta.isText()) {
            String text = delta.asText().text();
            var acc = textAcc.get(idx);
            if (acc != null) acc.append(text);
            if (idx < contentBlocks.size()) {
                contentBlocks.set(idx, new TextContent(acc != null ? acc.toString() : text, null));
            }
            eventStream.push(new AssistantMessageEvent.TextDeltaEvent(idx, text,
                    buildPartialMessage(model, responseId, contentBlocks, usage, null)));

        } else if (delta.isThinking()) {
            String text = delta.asThinking().thinking();
            var acc = thinkingAcc.get(idx);
            if (acc != null) acc.append(text);
            if (idx < contentBlocks.size()) {
                String sig = signatureAcc.containsKey(idx) ? signatureAcc.get(idx).toString() : null;
                contentBlocks.set(idx, new ThinkingContent(acc != null ? acc.toString() : text, sig, false));
            }
            eventStream.push(new AssistantMessageEvent.ThinkingDeltaEvent(idx, text,
                    buildPartialMessage(model, responseId, contentBlocks, usage, null)));

        } else if (delta.isSignature()) {
            // Accumulate thinking signature
            String sig = delta.asSignature().signature();
            signatureAcc.computeIfAbsent(idx, k -> new StringBuilder()).append(sig);
            // Update the ThinkingContent block with the accumulated signature
            if (idx < contentBlocks.size() && contentBlocks.get(idx) instanceof ThinkingContent tc) {
                String fullSig = signatureAcc.get(idx).toString();
                contentBlocks.set(idx, new ThinkingContent(tc.thinking(), fullSig, tc.redacted()));
            }

        } else if (delta.isInputJson()) {
            String json = delta.asInputJson().partialJson();
            var acc = toolJsonAcc.get(idx);
            if (acc != null) acc.append(json);
            eventStream.push(new AssistantMessageEvent.ToolCallDeltaEvent(idx, json,
                    buildPartialMessage(model, responseId, contentBlocks, usage, null)));
        }
    }

    private void handleContentBlockStop(
            int idx, Model model, String responseId,
            List<ContentBlock> contentBlocks, long[] usage,
            Map<Integer, String> blockTypes,
            Map<Integer, StringBuilder> textAcc,
            Map<Integer, StringBuilder> thinkingAcc,
            Map<Integer, StringBuilder> toolJsonAcc,
            Map<Integer, String[]> toolMeta,
            Map<Integer, StringBuilder> signatureAcc,
            AssistantMessageEventStream eventStream) {

        String type = blockTypes.get(idx);

        if ("text".equals(type)) {
            String content = textAcc.containsKey(idx) ? textAcc.get(idx).toString() : "";
            contentBlocks.set(idx, new TextContent(content, null));
            eventStream.push(new AssistantMessageEvent.TextEndEvent(idx, content,
                    buildPartialMessage(model, responseId, contentBlocks, usage, null)));

        } else if ("thinking".equals(type)) {
            String content = thinkingAcc.containsKey(idx) ? thinkingAcc.get(idx).toString() : "";
            String sig = signatureAcc.containsKey(idx) ? signatureAcc.get(idx).toString() : null;
            contentBlocks.set(idx, new ThinkingContent(content, sig, false));
            eventStream.push(new AssistantMessageEvent.ThinkingEndEvent(idx, content,
                    buildPartialMessage(model, responseId, contentBlocks, usage, null)));

        } else if ("redacted_thinking".equals(type)) {
            // Redacted thinking block is already complete from start event
            String content = contentBlocks.get(idx) instanceof ThinkingContent tc ? tc.thinking() : "";
            eventStream.push(new AssistantMessageEvent.ThinkingEndEvent(idx, content,
                    buildPartialMessage(model, responseId, contentBlocks, usage, null)));

        } else if ("tool_use".equals(type)) {
            String jsonStr = toolJsonAcc.containsKey(idx) ? toolJsonAcc.get(idx).toString() : "{}";
            Map<String, Object> args = parseToolArguments(jsonStr);
            String[] meta = toolMeta.get(idx);
            String id = meta != null ? meta[0] : "";
            String name = meta != null ? meta[1] : "";
            var toolCall = new ToolCall(id, name, args, null);
            contentBlocks.set(idx, toolCall);
            eventStream.push(new AssistantMessageEvent.ToolCallEndEvent(idx, toolCall,
                    buildPartialMessage(model, responseId, contentBlocks, usage, null)));
        }
    }

    // -- Message conversion --

    static List<MessageParam> convertMessages(List<Message> messages, boolean enableCaching) {
        List<MessageParam> result = new ArrayList<>();
        for (Message message : messages) {
            if (message instanceof com.campusclaw.ai.types.UserMessage um) {
                result.add(convertUserMessage(um));
            } else if (message instanceof AssistantMessage am) {
                result.add(convertAssistantMessage(am));
            } else if (message instanceof com.campusclaw.ai.types.ToolResultMessage tr) {
                result.add(convertToolResult(tr));
            }
        }

        // Add cache_control to the last user message for conversation history caching
        if (enableCaching && !result.isEmpty()) {
            for (int i = result.size() - 1; i >= 0; i--) {
                var msg = result.get(i);
                if (msg.role() == MessageParam.Role.USER) {
                    result.set(i, addCacheControlToLastBlock(msg));
                    break;
                }
            }
        }

        return result;
    }

    /**
     * Adds cache_control ephemeral to the last content block of a user message.
     */
    private static MessageParam addCacheControlToLastBlock(MessageParam msg) {
        var blockParams = msg.content().blockParams();
        if (blockParams.isEmpty() || blockParams.get().isEmpty()) return msg;

        var blocks = new ArrayList<>(blockParams.get());
        int lastIdx = blocks.size() - 1;
        var lastBlock = blocks.get(lastIdx);

        // Rebuild the last text block with cache_control
        if (lastBlock.isText()) {
            var tb = lastBlock.asText();
            blocks.set(lastIdx, ContentBlockParam.ofText(
                TextBlockParam.builder()
                    .text(tb.text())
                    .cacheControl(CacheControlEphemeral.builder().build())
                    .build()));
        } else if (lastBlock.isToolResult()) {
            // Tool result blocks - add cache_control via additional properties
            // The SDK doesn't expose cacheControl builder on ToolResultBlockParam,
            // so we keep it as-is (system prompt caching is the primary benefit)
            return msg;
        }

        return MessageParam.builder()
            .role(msg.role())
            .content(MessageParam.Content.ofBlockParams(blocks))
            .build();
    }

    private static MessageParam convertUserMessage(com.campusclaw.ai.types.UserMessage um) {
        List<ContentBlockParam> blocks = new ArrayList<>();
        for (ContentBlock cb : um.content()) {
            if (cb instanceof TextContent tc) {
                blocks.add(ContentBlockParam.ofText(
                        TextBlockParam.builder().text(tc.text()).build()));
            } else if (cb instanceof ImageContent ic) {
                blocks.add(ContentBlockParam.ofImage(
                        ImageBlockParam.builder()
                                .source(Base64ImageSource.builder()
                                        .data(ic.data())
                                        .mediaType(Base64ImageSource.MediaType.of(ic.mimeType()))
                                        .build())
                                .build()));
            }
        }
        return MessageParam.builder()
                .role(MessageParam.Role.USER)
                .content(MessageParam.Content.ofBlockParams(blocks))
                .build();
    }

    private static MessageParam convertAssistantMessage(AssistantMessage am) {
        List<ContentBlockParam> blocks = new ArrayList<>();
        for (ContentBlock cb : am.content()) {
            if (cb instanceof TextContent tc) {
                blocks.add(ContentBlockParam.ofText(
                        TextBlockParam.builder().text(tc.text()).build()));
            } else if (cb instanceof ThinkingContent tc) {
                if (tc.redacted()) {
                    // Replay redacted thinking as redacted_thinking block
                    blocks.add(ContentBlockParam.ofRedactedThinking(
                            RedactedThinkingBlockParam.builder()
                                    .data(tc.thinkingSignature() != null ? tc.thinkingSignature() : "")
                                    .build()));
                } else if (tc.thinkingSignature() != null && !tc.thinkingSignature().isBlank()) {
                    // Replay thinking with signature for multi-turn continuity
                    blocks.add(ContentBlockParam.ofThinking(
                            ThinkingBlockParam.builder()
                                    .thinking(tc.thinking())
                                    .signature(tc.thinkingSignature())
                                    .build()));
                } else if (!tc.thinking().isBlank()) {
                    // No signature (e.g., aborted stream) — convert to plain text
                    blocks.add(ContentBlockParam.ofText(
                            TextBlockParam.builder().text(tc.thinking()).build()));
                }
            } else if (cb instanceof ToolCall tc) {
                blocks.add(ContentBlockParam.ofToolUse(
                        ToolUseBlockParam.builder()
                                .id(tc.id())
                                .name(tc.name())
                                .input(ToolUseBlockParam.Input.builder()
                                        .putAllAdditionalProperties(toJsonValueMap(tc.arguments()))
                                        .build())
                                .build()));
            }
        }
        return MessageParam.builder()
                .role(MessageParam.Role.ASSISTANT)
                .content(MessageParam.Content.ofBlockParams(blocks))
                .build();
    }

    private static MessageParam convertToolResult(com.campusclaw.ai.types.ToolResultMessage tr) {
        // Collect text content for the tool result
        List<ContentBlockParam> resultBlocks = new ArrayList<>();
        for (ContentBlock cb : tr.content()) {
            if (cb instanceof TextContent tc) {
                resultBlocks.add(ContentBlockParam.ofText(
                        TextBlockParam.builder().text(tc.text()).build()));
            }
        }

        var toolResultBuilder = ToolResultBlockParam.builder()
                .toolUseId(tr.toolCallId())
                .isError(tr.isError());

        if (!resultBlocks.isEmpty()) {
            // Use string content for simplicity
            StringBuilder sb = new StringBuilder();
            for (ContentBlock cb : tr.content()) {
                if (cb instanceof TextContent tc) {
                    sb.append(tc.text());
                }
            }
            toolResultBuilder.content(ToolResultBlockParam.Content.ofString(sb.toString()));
        }

        // Tool results are sent as user messages in the Anthropic API
        return MessageParam.builder()
                .role(MessageParam.Role.USER)
                .content(MessageParam.Content.ofBlockParams(
                        List.of(ContentBlockParam.ofToolResult(toolResultBuilder.build()))))
                .build();
    }

    // -- Tool conversion --

    static List<ToolUnion> convertTools(List<Tool> tools) {
        List<ToolUnion> result = new ArrayList<>();
        for (Tool tool : tools) {
            var sdkTool = com.anthropic.models.messages.Tool.builder()
                    .name(tool.name())
                    .description(tool.description())
                    .inputSchema(buildInputSchema(tool.parameters()))
                    .build();
            result.add(ToolUnion.ofTool(sdkTool));
        }
        return result;
    }

    private static com.anthropic.models.messages.Tool.InputSchema buildInputSchema(JsonNode parameters) {
        var schemaBuilder = com.anthropic.models.messages.Tool.InputSchema.builder();
        if (parameters != null) {
            if (parameters.has("properties")) {
                var propsNode = parameters.get("properties");
                var propsBuilder = com.anthropic.models.messages.Tool.InputSchema.Properties.builder();
                propsNode.fieldNames().forEachRemaining(fieldName ->
                        propsBuilder.putAdditionalProperty(fieldName,
                                com.anthropic.core.JsonValue.from(nodeToObject(propsNode.get(fieldName)))));
                schemaBuilder.properties(propsBuilder.build());
            }
            if (parameters.has("required")) {
                List<String> required = new ArrayList<>();
                parameters.get("required").forEach(n -> required.add(n.asText()));
                schemaBuilder.required(required);
            }
        }
        return schemaBuilder.build();
    }

    // -- Utility methods --

    private AssistantMessage buildPartialMessage(
            Model model, String responseId,
            List<ContentBlock> contentBlocks, long[] usage,
            @Nullable StopReason stopReason) {

        var piUsage = new Usage(
                (int) usage[0], (int) usage[1],
                (int) usage[2], (int) usage[3],
                (int) (usage[0] + usage[1]),
                computeCost(model.cost(), usage));

        return new AssistantMessage(
                List.copyOf(contentBlocks),
                Api.ANTHROPIC_MESSAGES.value(),
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

    static StopReason mapStopReason(com.anthropic.models.messages.StopReason sdkReason) {
        String val = sdkReason.asString();
        return switch (val) {
            case "end_turn" -> StopReason.STOP;
            case "max_tokens" -> StopReason.LENGTH;
            case "tool_use" -> StopReason.TOOL_USE;
            default -> StopReason.STOP;
        };
    }

    /**
     * Returns true if prompt caching should be enabled.
     * Only enables caching for the official Anthropic API endpoint.
     */
    private static boolean shouldEnableCaching(@Nullable String baseUrl) {
        return baseUrl != null && baseUrl.contains("api.anthropic.com");
    }

    /**
     * Check if a model supports adaptive thinking (Opus 4.6 and Sonnet 4.6).
     */
    private static boolean supportsAdaptiveThinking(String modelId) {
        return modelId.contains("opus-4-6") || modelId.contains("opus-4.6")
            || modelId.contains("sonnet-4-6") || modelId.contains("sonnet-4.6");
    }

    /**
     * Map ThinkingLevel to Anthropic effort levels for adaptive thinking.
     * "max" is only valid on Opus 4.6.
     */
    private static String mapToAnthropicEffort(ThinkingLevel level, String modelId) {
        boolean isOpus = modelId.contains("opus");
        return switch (level) {
            case MINIMAL, LOW -> "low";
            case MEDIUM -> "medium";
            case HIGH -> "high";
            case XHIGH -> isOpus ? "max" : "high";
            default -> "high";
        };
    }

    private static long resolveBudget(ThinkingLevel level, @Nullable ThinkingBudgets budgets, int maxTokens) {
        if (budgets != null) {
            Integer budget = switch (level) {
                case MINIMAL -> budgets.minimal();
                case LOW -> budgets.low();
                case MEDIUM -> budgets.medium();
                case HIGH -> budgets.high();
                default -> null;
            };
            if (budget != null) return budget;
        }
        return switch (level) {
            case MINIMAL -> 1024;
            case LOW -> 2048;
            case MEDIUM -> 4096;
            case HIGH -> 8192;
            case XHIGH -> Math.max(maxTokens / 2, 16384);
            default -> 4096;
        };
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> parseToolArguments(String json) {
        try {
            return MAPPER.readValue(json, new TypeReference<Map<String, Object>>() {});
        } catch (Exception e) {
            return Map.of();
        }
    }

    private static Map<String, com.anthropic.core.JsonValue> toJsonValueMap(Map<String, Object> map) {
        Map<String, com.anthropic.core.JsonValue> result = new HashMap<>();
        if (map != null) {
            map.forEach((k, v) -> result.put(k, com.anthropic.core.JsonValue.from(v)));
        }
        return result;
    }

    private static Object nodeToObject(JsonNode node) {
        try {
            return MAPPER.treeToValue(node, Object.class);
        } catch (Exception e) {
            return node.toString();
        }
    }
}
