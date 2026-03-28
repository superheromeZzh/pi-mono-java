package com.mariozechner.pi.ai.provider.google;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.mariozechner.pi.ai.provider.ApiProvider;
import com.mariozechner.pi.ai.stream.AssistantMessageEventStream;
import com.mariozechner.pi.ai.types.*;
import jakarta.annotation.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;

/**
 * {@link ApiProvider} for the Google Generative AI (Gemini) REST API.
 * Uses java.net.http.HttpClient for SSE streaming.
 */
@Component
public class GoogleGenerativeAIProvider implements ApiProvider {

    private static final Logger log = LoggerFactory.getLogger(GoogleGenerativeAIProvider.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String DEFAULT_BASE_URL = "https://generativelanguage.googleapis.com/v1beta";

    @Override
    public Api getApi() {
        return Api.GOOGLE_GENERATIVE_AI;
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
            eventStream.error(new IllegalStateException(
                "Google API key not found. Set GOOGLE_API_KEY or GOOGLE_CLOUD_API_KEY."));
            return;
        }

        String baseUrl = model.baseUrl() != null ? model.baseUrl() : DEFAULT_BASE_URL;
        String url = baseUrl + "/models/" + model.id() + ":streamGenerateContent?alt=sse&key=" + apiKey;

        ObjectNode requestBody = buildRequestBody(model, context, options);

        try {
            var client = HttpClient.newHttpClient();
            var request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(requestBody.toString()))
                .build();

            var response = client.send(request, HttpResponse.BodyHandlers.ofInputStream());

            List<ContentBlock> blocks = new ArrayList<>();
            Usage[] usage = {Usage.empty()};
            StopReason[] stop = {StopReason.STOP};
            int textIndex = 0;

            try (var reader = new BufferedReader(new InputStreamReader(response.body()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (line.isBlank() || !line.startsWith("data: ")) continue;
                    String data = line.substring(6).trim();
                    if (data.equals("[DONE]")) break;

                    JsonNode chunk = MAPPER.readTree(data);
                    var parsed = GoogleShared.parseChunk(chunk);

                    for (var block : parsed.blocks()) {
                        blocks.add(block);
                        if (block instanceof TextContent tc) {
                            var partial = buildMessage(model, blocks, usage[0], stop[0]);
                            eventStream.pushTextDelta(textIndex, tc.text(), partial);
                        }
                        // ToolCalls handled at end
                    }
                    if (parsed.usage() != null) usage[0] = parsed.usage();
                    if (parsed.finishReason() != null) stop[0] = GoogleShared.mapFinishReason(parsed.finishReason());
                }
            }

            var cost = computeCost(model, usage[0]);
            var finalUsage = new Usage(usage[0].input(), usage[0].output(),
                usage[0].cacheRead(), usage[0].cacheWrite(), usage[0].totalTokens(), cost);
            var finalMessage = new AssistantMessage(
                List.copyOf(blocks),
                Api.GOOGLE_GENERATIVE_AI.value(), model.provider().value(),
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
            Api.GOOGLE_GENERATIVE_AI.value(), model.provider().value(),
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
        body.set("generationConfig", genConfig);

        return body;
    }

    private String resolveApiKey(Model model, @Nullable SimpleStreamOptions options) {
        if (options != null && options.apiKey() != null) return options.apiKey();
        if (model.apiKey() != null && !model.apiKey().isBlank()) return model.apiKey();
        String key = System.getenv("GOOGLE_API_KEY");
        if (key != null && !key.isBlank()) return key;
        return System.getenv("GOOGLE_CLOUD_API_KEY");
    }

    private Cost computeCost(Model model, Usage usage) {
        if (model.cost() == null) return Cost.empty();
        var mc = model.cost();
        double input = usage.input() * mc.input() / 1_000_000.0;
        double output = usage.output() * mc.output() / 1_000_000.0;
        return new Cost(input, output, 0, 0, input + output);
    }
}
