package com.campusclaw.ai.provider.google;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;

import com.campusclaw.ai.provider.ApiProvider;
import com.campusclaw.ai.stream.AssistantMessageEventStream;
import com.campusclaw.ai.types.*;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import jakarta.annotation.Nullable;

/**
 * {@link ApiProvider} for Google Vertex AI.
 * Uses REST API with API key or ADC auth.
 */
@Component
public class GoogleVertexAIProvider implements ApiProvider {

    private static final Logger log = LoggerFactory.getLogger(GoogleVertexAIProvider.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Override
    public Api getApi() {
        return Api.GOOGLE_VERTEX;
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
        String projectId = System.getenv("GOOGLE_CLOUD_PROJECT");
        String location = System.getenv("GOOGLE_CLOUD_LOCATION");
        if (location == null || location.isBlank()) location = "us-central1";

        String endpoint;
        if (model.baseUrl() != null) {
            endpoint = model.baseUrl() + "/models/" + model.id() + ":streamGenerateContent?alt=sse";
        } else if (projectId != null && !projectId.isBlank()) {
            endpoint = String.format(
                "https://%s-aiplatform.googleapis.com/v1/projects/%s/locations/%s/publishers/google/models/%s:streamGenerateContent?alt=sse",
                location, projectId, location, model.id()
            );
        } else if (apiKey != null && !apiKey.isBlank()) {
            endpoint = "https://generativelanguage.googleapis.com/v1beta/models/"
                + model.id() + ":streamGenerateContent?alt=sse&key=" + apiKey;
        } else {
            eventStream.error(new IllegalStateException(
                "Google Cloud credentials not found. Set GOOGLE_CLOUD_PROJECT or GOOGLE_CLOUD_API_KEY."));
            return;
        }

        ObjectNode requestBody = buildRequestBody(model, context, options);

        try {
            var requestBuilder = HttpRequest.newBuilder()
                .uri(URI.create(endpoint))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(requestBody.toString()));

            var client = HttpClient.newBuilder()
                    .connectTimeout(java.time.Duration.ofSeconds(15))
                    .build();
            var response = client.send(requestBuilder.build(), HttpResponse.BodyHandlers.ofInputStream());

            List<ContentBlock> blocks = new ArrayList<>();
            Usage[] usage = {Usage.empty()};
            StopReason[] stop = {StopReason.STOP};
            String[] currentType = {null};
            StringBuilder textAcc = new StringBuilder();
            StringBuilder thinkingAcc = new StringBuilder();
            String[] thinkingSig = {null};

            try (var reader = new BufferedReader(new InputStreamReader(response.body()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (line.isBlank() || !line.startsWith("data: ")) continue;
                    String data = line.substring(6).trim();
                    if (data.equals("[DONE]")) break;

                    JsonNode chunk = MAPPER.readTree(data);
                    var parsed = GoogleShared.parseChunk(chunk);

                    for (var block : parsed.blocks()) {
                        if (block instanceof ThinkingContent tc) {
                            if (!"thinking".equals(currentType[0])) {
                                finishCurrentBlock(currentType[0], blocks, textAcc, thinkingAcc, thinkingSig, eventStream, model, usage[0], stop[0]);
                                currentType[0] = "thinking";
                                thinkingAcc.setLength(0);
                                thinkingSig[0] = null;
                                blocks.add(new ThinkingContent("", null, false));
                                eventStream.push(new com.campusclaw.ai.stream.AssistantMessageEvent.ThinkingStartEvent(
                                    blocks.size() - 1, buildMessage(model, blocks, usage[0], stop[0])));
                            }
                            thinkingAcc.append(tc.thinking());
                            if (tc.thinkingSignature() != null) thinkingSig[0] = tc.thinkingSignature();
                            blocks.set(blocks.size() - 1, new ThinkingContent(thinkingAcc.toString(), thinkingSig[0], false));
                            eventStream.push(new com.campusclaw.ai.stream.AssistantMessageEvent.ThinkingDeltaEvent(
                                blocks.size() - 1, tc.thinking(), buildMessage(model, blocks, usage[0], stop[0])));

                        } else if (block instanceof TextContent tc) {
                            if (!"text".equals(currentType[0])) {
                                finishCurrentBlock(currentType[0], blocks, textAcc, thinkingAcc, thinkingSig, eventStream, model, usage[0], stop[0]);
                                currentType[0] = "text";
                                textAcc.setLength(0);
                                blocks.add(new TextContent("", null));
                                eventStream.push(new com.campusclaw.ai.stream.AssistantMessageEvent.TextStartEvent(
                                    blocks.size() - 1, buildMessage(model, blocks, usage[0], stop[0])));
                            }
                            textAcc.append(tc.text());
                            blocks.set(blocks.size() - 1, new TextContent(textAcc.toString(), null));
                            eventStream.pushTextDelta(blocks.size() - 1, tc.text(), buildMessage(model, blocks, usage[0], stop[0]));

                        } else if (block instanceof ToolCall tc) {
                            finishCurrentBlock(currentType[0], blocks, textAcc, thinkingAcc, thinkingSig, eventStream, model, usage[0], stop[0]);
                            currentType[0] = null;
                            blocks.add(tc);
                            int idx = blocks.size() - 1;
                            eventStream.push(new com.campusclaw.ai.stream.AssistantMessageEvent.ToolCallStartEvent(
                                idx, buildMessage(model, blocks, usage[0], stop[0])));
                            try {
                                eventStream.push(new com.campusclaw.ai.stream.AssistantMessageEvent.ToolCallDeltaEvent(
                                    idx, MAPPER.writeValueAsString(tc.arguments()), buildMessage(model, blocks, usage[0], stop[0])));
                            } catch (Exception ignored) {}
                            eventStream.push(new com.campusclaw.ai.stream.AssistantMessageEvent.ToolCallEndEvent(
                                idx, tc, buildMessage(model, blocks, usage[0], stop[0])));
                        }
                    }
                    if (parsed.usage() != null) usage[0] = parsed.usage();
                    if (parsed.finishReason() != null) stop[0] = GoogleShared.mapFinishReason(parsed.finishReason());
                }
            }
            finishCurrentBlock(currentType[0], blocks, textAcc, thinkingAcc, thinkingSig, eventStream, model, usage[0], stop[0]);

            if (blocks.stream().anyMatch(b -> b instanceof ToolCall)) {
                stop[0] = StopReason.TOOL_USE;
            }

            var cost = computeCost(model, usage[0]);
            var finalUsage = new Usage(usage[0].input(), usage[0].output(),
                usage[0].cacheRead(), usage[0].cacheWrite(), usage[0].totalTokens(), cost);
            var finalMessage = new AssistantMessage(
                List.copyOf(blocks),
                Api.GOOGLE_VERTEX.value(), model.provider().value(),
                model.id(), null, finalUsage, stop[0], null, System.currentTimeMillis()
            );
            eventStream.pushDone(stop[0], finalMessage);

        } catch (Exception e) {
            eventStream.error(e);
        }
    }

    private AssistantMessage buildMessage(Model model, List<ContentBlock> blocks, Usage usage, StopReason stop) {
        return new AssistantMessage(
            List.copyOf(blocks),
            Api.GOOGLE_VERTEX.value(), model.provider().value(),
            model.id(), null, usage, stop, null, System.currentTimeMillis()
        );
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
        if (tools != null) body.set("tools", tools);
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
        if (options != null && options.reasoning() != null
                && options.reasoning() != ThinkingLevel.OFF && model.reasoning()) {
            var thinkingConfig = MAPPER.createObjectNode();
            thinkingConfig.put("includeThoughts", true);
            int budget = GoogleGenerativeAIProvider.resolveGoogleThinkingBudget(
                model, options.reasoning(), options.thinkingBudgets());
            if (budget >= 0) {
                thinkingConfig.put("thinkingBudget", budget);
            }
            genConfig.set("thinkingConfig", thinkingConfig);
        }

        body.set("generationConfig", genConfig);
        return body;
    }

    private String resolveApiKey(Model model, @Nullable SimpleStreamOptions options) {
        if (options != null && options.apiKey() != null) return options.apiKey();
        if (model.apiKey() != null && !model.apiKey().isBlank()) return model.apiKey();
        String key = System.getenv("GOOGLE_CLOUD_API_KEY");
        if (key != null && !key.isBlank()) return key;
        return System.getenv("GOOGLE_API_KEY");
    }

    private void finishCurrentBlock(
            String type, List<ContentBlock> blocks,
            StringBuilder textAcc, StringBuilder thinkingAcc, String[] thinkingSig,
            AssistantMessageEventStream eventStream, Model model, Usage usage, StopReason stop) {
        if (type == null || blocks.isEmpty()) return;
        int idx = blocks.size() - 1;
        if ("thinking".equals(type)) {
            String content = thinkingAcc.toString();
            blocks.set(idx, new ThinkingContent(content, thinkingSig[0], false));
            eventStream.push(new com.campusclaw.ai.stream.AssistantMessageEvent.ThinkingEndEvent(
                idx, content, buildMessage(model, blocks, usage, stop)));
        } else if ("text".equals(type)) {
            String content = textAcc.toString();
            blocks.set(idx, new TextContent(content, null));
            eventStream.push(new com.campusclaw.ai.stream.AssistantMessageEvent.TextEndEvent(
                idx, content, buildMessage(model, blocks, usage, stop)));
        }
    }

    private Cost computeCost(Model model, Usage usage) {
        if (model.cost() == null) return Cost.empty();
        var mc = model.cost();
        double input = usage.input() * mc.input() / 1_000_000.0;
        double output = usage.output() * mc.output() / 1_000_000.0;
        double cacheRead = usage.cacheRead() * mc.cacheRead() / 1_000_000.0;
        return new Cost(input, output, cacheRead, 0, input + output + cacheRead);
    }
}
