package com.campusclaw.ai.provider.mistral;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.*;

import com.campusclaw.ai.provider.ApiProvider;
import com.campusclaw.ai.stream.AssistantMessageEventStream;
import com.campusclaw.ai.types.*;
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
 */
@Component
public class MistralProvider implements ApiProvider {

    private static final Logger log = LoggerFactory.getLogger(MistralProvider.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String DEFAULT_BASE_URL = "https://api.mistral.ai/v1";
    private static final String ENV_API_KEY = "MISTRAL_API_KEY";

    @Override
    public Api getApi() {
        return Api.MISTRAL_CONVERSATIONS;
    }

    @Override
    public AssistantMessageEventStream stream(Model model, Context context, @Nullable StreamOptions options) {
        return streamSimple(model, context, options != null ? SimpleStreamOptions.from(options) : null);
    }

    @Override
    public AssistantMessageEventStream streamSimple(Model model, Context context, @Nullable SimpleStreamOptions options) {
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

    private void executeStream(Model model, Context context, @Nullable SimpleStreamOptions options,
                               AssistantMessageEventStream eventStream) {
        String apiKey = resolveApiKey(model, options);
        if (apiKey == null || apiKey.isBlank()) {
            eventStream.error(new IllegalStateException("Mistral API key not found. Set MISTRAL_API_KEY."));
            return;
        }

        String baseUrl = model.baseUrl() != null ? model.baseUrl() : DEFAULT_BASE_URL;
        String url = baseUrl + "/chat/completions";

        ObjectNode requestBody = buildRequestBody(model, context, options);

        try {
            var client = HttpClient.newBuilder()
                    .connectTimeout(java.time.Duration.ofSeconds(15))
                    .build();
            var request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + apiKey)
                .POST(HttpRequest.BodyPublishers.ofString(requestBody.toString()))
                .build();

            var response = client.send(request, HttpResponse.BodyHandlers.ofInputStream());

            List<ContentBlock> accumulatedBlocks = new ArrayList<>();
            StringBuilder currentText = new StringBuilder();
            StringBuilder currentThinking = new StringBuilder();
            Map<Integer, ToolCallAccumulator> toolCallAccs = new HashMap<>();
            Usage[] usage = {Usage.empty()};
            StopReason[] stop = {StopReason.STOP};
            int textIndex = 0;
            boolean[] thinkingStarted = {false};

            try (var reader = new BufferedReader(new InputStreamReader(response.body()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (line.isBlank() || !line.startsWith("data: ")) continue;
                    String data = line.substring(6).trim();
                    if (data.equals("[DONE]")) break;

                    JsonNode chunk = MAPPER.readTree(data);
                    var choices = chunk.path("choices");
                    if (!choices.isArray() || choices.isEmpty()) continue;

                    var choice = choices.get(0);
                    var delta = choice.path("delta");

                    // Content — can be a string or an array of typed items
                    if (delta.has("content") && !delta.get("content").isNull()) {
                        var contentNode = delta.get("content");
                        if (contentNode.isTextual()) {
                            // Simple string content
                            String text = contentNode.asText();
                            if (!text.isEmpty()) {
                                currentText.append(text);
                                var partial = buildPartial(model, accumulatedBlocks,
                                    currentText.toString(), currentThinking.toString(),
                                    toolCallAccs, stop[0], usage[0]);
                                eventStream.pushTextDelta(textIndex, text, partial);
                            }
                        } else if (contentNode.isArray()) {
                            // Array of typed items (thinking, text, etc.)
                            for (var item : contentNode) {
                                String itemType = item.path("type").asText("");
                                if ("thinking".equals(itemType)) {
                                    // Extract thinking text from nested array
                                    var thinkingArr = item.path("thinking");
                                    var sb = new StringBuilder();
                                    if (thinkingArr.isArray()) {
                                        for (var part : thinkingArr) {
                                            if ("text".equals(part.path("type").asText(""))) {
                                                sb.append(part.path("text").asText(""));
                                            }
                                        }
                                    }
                                    String thinkText = sb.toString();
                                    if (!thinkText.isEmpty()) {
                                        if (!thinkingStarted[0]) {
                                            thinkingStarted[0] = true;
                                            accumulatedBlocks.add(new ThinkingContent("", null, false));
                                            int idx = accumulatedBlocks.size() - 1;
                                            eventStream.push(new com.campusclaw.ai.stream.AssistantMessageEvent.ThinkingStartEvent(
                                                idx, buildPartial(model, accumulatedBlocks,
                                                    currentText.toString(), currentThinking.toString(),
                                                    toolCallAccs, stop[0], usage[0])));
                                        }
                                        currentThinking.append(thinkText);
                                        int idx = accumulatedBlocks.size() - 1;
                                        accumulatedBlocks.set(idx, new ThinkingContent(currentThinking.toString(), null, false));
                                        eventStream.push(new com.campusclaw.ai.stream.AssistantMessageEvent.ThinkingDeltaEvent(
                                            idx, thinkText,
                                            buildPartial(model, accumulatedBlocks,
                                                currentText.toString(), currentThinking.toString(),
                                                toolCallAccs, stop[0], usage[0])));
                                    }
                                } else if ("text".equals(itemType)) {
                                    String text = item.path("text").asText("");
                                    if (!text.isEmpty()) {
                                        currentText.append(text);
                                        var partial = buildPartial(model, accumulatedBlocks,
                                            currentText.toString(), currentThinking.toString(),
                                            toolCallAccs, stop[0], usage[0]);
                                        eventStream.pushTextDelta(textIndex, text, partial);
                                    }
                                }
                            }
                        }
                    }

                    // Tool calls
                    if (delta.has("tool_calls") && delta.get("tool_calls").isArray()) {
                        for (var tc : delta.get("tool_calls")) {
                            int idx = tc.path("index").asInt(0);
                            var acc = toolCallAccs.computeIfAbsent(idx, k -> new ToolCallAccumulator());
                            if (tc.has("id")) acc.id = tc.get("id").asText();
                            var fn = tc.path("function");
                            if (fn.has("name")) acc.name = fn.get("name").asText();
                            if (fn.has("arguments")) acc.arguments.append(fn.get("arguments").asText());
                        }
                    }

                    // Finish reason
                    if (choice.has("finish_reason") && !choice.get("finish_reason").isNull()) {
                        stop[0] = mapFinishReason(choice.get("finish_reason").asText());
                    }

                    // Usage
                    if (chunk.has("usage") && !chunk.get("usage").isNull()) {
                        var u = chunk.get("usage");
                        int input = u.path("prompt_tokens").asInt(0);
                        int output = u.path("completion_tokens").asInt(0);
                        usage[0] = new Usage(input, output, 0, 0, input + output, Cost.empty());
                    }
                }
            }

            // Close open thinking block
            if (thinkingStarted[0] && !currentThinking.isEmpty()) {
                int thinkIdx = accumulatedBlocks.size() - 1;
                accumulatedBlocks.set(thinkIdx, new ThinkingContent(currentThinking.toString(), null, false));
                eventStream.push(new com.campusclaw.ai.stream.AssistantMessageEvent.ThinkingEndEvent(
                    thinkIdx, currentThinking.toString(),
                    buildPartial(model, accumulatedBlocks, currentText.toString(),
                        currentThinking.toString(), toolCallAccs, stop[0], usage[0])));
            }

            // Build final message
            var finalBlocks = new ArrayList<ContentBlock>(accumulatedBlocks);
            if (!currentText.isEmpty()) {
                finalBlocks.add(new TextContent(currentText.toString()));
            }
            for (var acc : toolCallAccs.values()) {
                finalBlocks.add(acc.toToolCall());
            }

            var cost = computeCost(model, usage[0]);
            var finalUsage = new Usage(usage[0].input(), usage[0].output(), 0, 0,
                usage[0].input() + usage[0].output(), cost);
            var finalMessage = new AssistantMessage(
                List.copyOf(finalBlocks),
                Api.MISTRAL_CONVERSATIONS.value(), model.provider().value(),
                model.id(), null, finalUsage, stop[0], null, System.currentTimeMillis()
            );
            eventStream.pushDone(stop[0], finalMessage);

        } catch (Exception e) {
            eventStream.error(e);
        }
    }

    private AssistantMessage buildPartial(
        Model model,
        List<ContentBlock> accumulatedBlocks, String currentText,
        String currentThinking,
        Map<Integer, ToolCallAccumulator> toolCallAccs,
        StopReason stop, Usage usage
    ) {
        var blocks = new ArrayList<ContentBlock>(accumulatedBlocks);
        if (!currentText.isEmpty()) blocks.add(new TextContent(currentText));
        for (var acc : toolCallAccs.values()) {
            if (acc.name != null) blocks.add(acc.toToolCall());
        }
        return new AssistantMessage(
            blocks, Api.MISTRAL_CONVERSATIONS.value(), model.provider().value(),
            model.id(), null, usage, stop, null, System.currentTimeMillis()
        );
    }

    private ObjectNode buildRequestBody(Model model, Context context, @Nullable SimpleStreamOptions options) {
        var body = MAPPER.createObjectNode();
        body.put("model", model.id());
        body.put("stream", true);
        body.set("stream_options", MAPPER.createObjectNode().put("include_usage", true));

        var messages = MAPPER.createArrayNode();

        if (context.systemPrompt() != null && !context.systemPrompt().isBlank()) {
            var sys = MAPPER.createObjectNode();
            sys.put("role", "system");
            sys.put("content", context.systemPrompt());
            messages.add(sys);
        }

        for (var msg : context.messages()) {
            switch (msg) {
                case UserMessage um -> {
                    var m = MAPPER.createObjectNode();
                    m.put("role", "user");
                    m.put("content", extractText(um.content()));
                    messages.add(m);
                }
                case AssistantMessage am -> {
                    var m = MAPPER.createObjectNode();
                    m.put("role", "assistant");
                    StringBuilder text = new StringBuilder();
                    var toolCalls = MAPPER.createArrayNode();
                    var contentArray = MAPPER.createArrayNode();
                    boolean hasThinking = false;
                    for (var block : am.content()) {
                        if (block instanceof ThinkingContent tc) {
                            if (tc.thinking() != null && !tc.thinking().isBlank()) {
                                hasThinking = true;
                                var thinkingItem = MAPPER.createObjectNode();
                                thinkingItem.put("type", "thinking");
                                var thinkingParts = MAPPER.createArrayNode();
                                thinkingParts.add(MAPPER.createObjectNode()
                                    .put("type", "text").put("text", tc.thinking()));
                                thinkingItem.set("thinking", thinkingParts);
                                contentArray.add(thinkingItem);
                            }
                        } else if (block instanceof TextContent tc) text.append(tc.text());
                        else if (block instanceof ToolCall tc) {
                            var tcNode = MAPPER.createObjectNode();
                            tcNode.put("id", tc.id());
                            tcNode.put("type", "function");
                            var fn = MAPPER.createObjectNode();
                            fn.put("name", tc.name());
                            try {
                                fn.put("arguments", MAPPER.writeValueAsString(tc.arguments()));
                            } catch (Exception e) {
                                fn.put("arguments", "{}");
                            }
                            tcNode.set("function", fn);
                            toolCalls.add(tcNode);
                        }
                    }
                    if (hasThinking) {
                        // Use array content format when thinking is present
                        if (!text.isEmpty()) {
                            contentArray.add(MAPPER.createObjectNode()
                                .put("type", "text").put("text", text.toString()));
                        }
                        m.set("content", contentArray);
                    } else if (!text.isEmpty()) {
                        m.put("content", text.toString());
                    }
                    if (!toolCalls.isEmpty()) m.set("tool_calls", toolCalls);
                    messages.add(m);
                }
                case ToolResultMessage trm -> {
                    var m = MAPPER.createObjectNode();
                    m.put("role", "tool");
                    m.put("tool_call_id", trm.toolCallId());
                    m.put("content", extractText(trm.content()));
                    messages.add(m);
                }
                default -> {}
            }
        }
        body.set("messages", messages);

        if (context.tools() != null && !context.tools().isEmpty()) {
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
            body.set("tools", tools);
        }

        if (options != null) {
            if (options.maxTokens() != null) body.put("max_tokens", options.maxTokens());
            if (options.temperature() != null) body.put("temperature", options.temperature());

            // Reasoning / thinking mode
            if (options.reasoning() != null && options.reasoning() != ThinkingLevel.OFF
                    && model.reasoning()) {
                body.put("promptMode", "reasoning");
            }
        } else {
            body.put("max_tokens", model.maxTokens());
        }

        return body;
    }

    private String resolveApiKey(Model model, @Nullable SimpleStreamOptions options) {
        if (options != null && options.apiKey() != null) return options.apiKey();
        if (model.apiKey() != null && !model.apiKey().isBlank()) return model.apiKey();
        return System.getenv(ENV_API_KEY);
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
        if (model.cost() == null) return Cost.empty();
        var mc = model.cost();
        double input = usage.input() * mc.input() / 1_000_000.0;
        double output = usage.output() * mc.output() / 1_000_000.0;
        return new Cost(input, output, 0, 0, input + output);
    }

    private static String extractText(List<ContentBlock> content) {
        var sb = new StringBuilder();
        for (var block : content) {
            if (block instanceof TextContent tc) sb.append(tc.text());
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
                } catch (Exception ignored) {}
            }
            return new ToolCall(id != null ? id : UUID.randomUUID().toString(), name != null ? name : "", args);
        }

        private static final ObjectMapper MAPPER = new ObjectMapper();
    }
}
