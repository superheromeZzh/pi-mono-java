package com.mariozechner.pi.ai.provider.anthropic;

import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import com.anthropic.core.http.StreamResponse;
import com.anthropic.models.messages.Base64ImageSource;
import com.anthropic.models.messages.ContentBlockParam;
import com.anthropic.models.messages.ImageBlockParam;
import com.anthropic.models.messages.MessageCreateParams;
import com.anthropic.models.messages.MessageParam;
import com.anthropic.models.messages.RawContentBlockDelta;
import com.anthropic.models.messages.RawContentBlockDeltaEvent;
import com.anthropic.models.messages.RawContentBlockStartEvent;
import com.anthropic.models.messages.RawMessageStreamEvent;
import com.anthropic.models.messages.TextBlockParam;
import com.anthropic.models.messages.ThinkingBlockParam;
import com.anthropic.models.messages.ThinkingConfigEnabled;
import com.anthropic.models.messages.ThinkingConfigParam;
import com.anthropic.models.messages.ToolResultBlockParam;
import com.anthropic.models.messages.ToolUnion;
import com.anthropic.models.messages.ToolUseBlockParam;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mariozechner.pi.ai.provider.ApiProvider;
import com.mariozechner.pi.ai.stream.AssistantMessageEvent;
import com.mariozechner.pi.ai.stream.AssistantMessageEventStream;
import com.mariozechner.pi.ai.types.Api;
import com.mariozechner.pi.ai.types.AssistantMessage;
import com.mariozechner.pi.ai.types.ContentBlock;
import com.mariozechner.pi.ai.types.Context;
import com.mariozechner.pi.ai.types.Cost;
import com.mariozechner.pi.ai.types.ImageContent;
import com.mariozechner.pi.ai.types.Message;
import com.mariozechner.pi.ai.types.Model;
import com.mariozechner.pi.ai.types.ModelCost;
import com.mariozechner.pi.ai.types.Provider;
import com.mariozechner.pi.ai.types.SimpleStreamOptions;
import com.mariozechner.pi.ai.types.StopReason;
import com.mariozechner.pi.ai.types.StreamOptions;
import com.mariozechner.pi.ai.types.TextContent;
import com.mariozechner.pi.ai.types.ThinkingBudgets;
import com.mariozechner.pi.ai.types.ThinkingContent;
import com.mariozechner.pi.ai.types.ThinkingLevel;
import com.mariozechner.pi.ai.types.Tool;
import com.mariozechner.pi.ai.types.ToolCall;
import com.mariozechner.pi.ai.types.Usage;
import jakarta.annotation.Nullable;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
        String providerKey = switch (model.provider()) {
            case KIMI_CODING -> System.getenv("KIMI_API_KEY");
            case MINIMAX -> System.getenv("MINIMAX_API_KEY");
            case MINIMAX_CN -> System.getenv("MINIMAX_CN_API_KEY");
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
        var builder = AnthropicOkHttpClient.builder().apiKey(apiKey);
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

        var builder = MessageCreateParams.builder()
                .model(com.anthropic.models.messages.Model.of(model.id()))
                .maxTokens(resolvedMaxTokens)
                .messages(convertMessages(context.messages()));

        // System prompt
        if (context.systemPrompt() != null && !context.systemPrompt().isBlank()) {
            builder.system(MessageCreateParams.System.ofTextBlockParams(
                    List.of(TextBlockParam.builder().text(context.systemPrompt()).build())));
        }

        // Tools
        if (context.tools() != null && !context.tools().isEmpty()) {
            builder.tools(convertTools(context.tools()));
        }

        // Temperature
        if (temperature != null) {
            builder.temperature(temperature);
        }

        // Extended thinking
        if (reasoning != null && reasoning != ThinkingLevel.OFF) {
            long budget = resolveBudget(reasoning, thinkingBudgets, resolvedMaxTokens);
            builder.thinking(ThinkingConfigParam.ofEnabled(
                    ThinkingConfigEnabled.builder().budgetTokens(budget).build()));
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
                            thinkingAccumulator, toolJsonAccumulator, eventStream);

                } else if (event.isContentBlockStop()) {
                    int idx = (int) event.asContentBlockStop().index();
                    handleContentBlockStop(idx, model, responseId[0],
                            contentBlocks, accumulatedUsage, blockTypes, textAccumulator,
                            thinkingAccumulator, toolJsonAccumulator, toolMeta, eventStream);

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
                contentBlocks.set(idx, new ThinkingContent(acc != null ? acc.toString() : text, null, false));
            }
            eventStream.push(new AssistantMessageEvent.ThinkingDeltaEvent(idx, text,
                    buildPartialMessage(model, responseId, contentBlocks, usage, null)));

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
            AssistantMessageEventStream eventStream) {

        String type = blockTypes.get(idx);

        if ("text".equals(type)) {
            String content = textAcc.containsKey(idx) ? textAcc.get(idx).toString() : "";
            contentBlocks.set(idx, new TextContent(content, null));
            eventStream.push(new AssistantMessageEvent.TextEndEvent(idx, content,
                    buildPartialMessage(model, responseId, contentBlocks, usage, null)));

        } else if ("thinking".equals(type)) {
            String content = thinkingAcc.containsKey(idx) ? thinkingAcc.get(idx).toString() : "";
            contentBlocks.set(idx, new ThinkingContent(content, null, false));
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

    static List<MessageParam> convertMessages(List<Message> messages) {
        List<MessageParam> result = new ArrayList<>();
        for (Message message : messages) {
            if (message instanceof com.mariozechner.pi.ai.types.UserMessage um) {
                result.add(convertUserMessage(um));
            } else if (message instanceof AssistantMessage am) {
                result.add(convertAssistantMessage(am));
            } else if (message instanceof com.mariozechner.pi.ai.types.ToolResultMessage tr) {
                result.add(convertToolResult(tr));
            }
        }
        return result;
    }

    private static MessageParam convertUserMessage(com.mariozechner.pi.ai.types.UserMessage um) {
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
                blocks.add(ContentBlockParam.ofThinking(
                        ThinkingBlockParam.builder()
                                .thinking(tc.thinking())
                                .signature(tc.thinkingSignature() != null ? tc.thinkingSignature() : "")
                                .build()));
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

    private static MessageParam convertToolResult(com.mariozechner.pi.ai.types.ToolResultMessage tr) {
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
                Provider.ANTHROPIC.value(),
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
