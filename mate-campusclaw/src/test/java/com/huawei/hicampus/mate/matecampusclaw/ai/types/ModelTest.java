package com.huawei.hicampus.mate.matecampusclaw.ai.types;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class ModelTest {

    private ObjectMapper mapper;

    @BeforeEach
    void setUp() {
        mapper = new ObjectMapper();
    }

    // --- Provider ---

    @Nested
    class ProviderTests {

        @Test
        void jsonValues() {
            assertEquals("anthropic", Provider.ANTHROPIC.value());
            assertEquals("openai", Provider.OPENAI.value());
            assertEquals("google-vertex", Provider.GOOGLE_VERTEX.value());
            assertEquals("amazon-bedrock", Provider.AMAZON_BEDROCK.value());
            assertEquals("azure-openai-responses", Provider.AZURE_OPENAI.value());
            assertEquals("openai-codex", Provider.OPENAI_CODEX.value());
        }

        @Test
        void fromValue() {
            assertEquals(Provider.ANTHROPIC, Provider.fromValue("anthropic"));
            assertEquals(Provider.AMAZON_BEDROCK, Provider.fromValue("amazon-bedrock"));
        }

        @Test
        void fromValueUnknownThrows() {
            assertThrows(IllegalArgumentException.class, () -> Provider.fromValue("unknown"));
        }

        @Test
        void serialization() throws JsonProcessingException {
            assertEquals("\"anthropic\"", mapper.writeValueAsString(Provider.ANTHROPIC));
            assertEquals("\"google-vertex\"", mapper.writeValueAsString(Provider.GOOGLE_VERTEX));
        }

        @Test
        void deserialization() throws JsonProcessingException {
            assertEquals(Provider.OPENAI, mapper.readValue("\"openai\"", Provider.class));
            assertEquals(Provider.MISTRAL, mapper.readValue("\"mistral\"", Provider.class));
        }
    }

    // --- Api ---

    @Nested
    class ApiTests {

        @Test
        void jsonValues() {
            assertEquals("anthropic-messages", Api.ANTHROPIC_MESSAGES.value());
            assertEquals("openai-responses", Api.OPENAI_RESPONSES.value());
            assertEquals("openai-completions", Api.OPENAI_COMPLETIONS.value());
            assertEquals("bedrock-converse-stream", Api.BEDROCK_CONVERSE_STREAM.value());
            assertEquals("google-generative-ai", Api.GOOGLE_GENERATIVE_AI.value());
            assertEquals("google-vertex", Api.GOOGLE_VERTEX.value());
            assertEquals("mistral-conversations", Api.MISTRAL_CONVERSATIONS.value());
            assertEquals("azure-openai-responses", Api.AZURE_OPENAI_RESPONSES.value());
            assertEquals("openai-codex-responses", Api.OPENAI_CODEX_RESPONSES.value());
        }

        @Test
        void fromValue() {
            assertEquals(Api.ANTHROPIC_MESSAGES, Api.fromValue("anthropic-messages"));
            assertEquals(Api.OPENAI_CODEX_RESPONSES, Api.fromValue("openai-codex-responses"));
        }

        @Test
        void fromValueUnknownThrows() {
            assertThrows(IllegalArgumentException.class, () -> Api.fromValue("bad"));
        }

        @Test
        void serialization() throws JsonProcessingException {
            assertEquals("\"anthropic-messages\"", mapper.writeValueAsString(Api.ANTHROPIC_MESSAGES));
        }

        @Test
        void deserialization() throws JsonProcessingException {
            assertEquals(Api.BEDROCK_CONVERSE_STREAM,
                mapper.readValue("\"bedrock-converse-stream\"", Api.class));
        }
    }

    // --- InputModality ---

    @Nested
    class InputModalityTests {

        @Test
        void jsonValues() {
            assertEquals("text", InputModality.TEXT.value());
            assertEquals("image", InputModality.IMAGE.value());
        }

        @Test
        void fromValue() {
            assertEquals(InputModality.TEXT, InputModality.fromValue("text"));
            assertEquals(InputModality.IMAGE, InputModality.fromValue("image"));
        }

        @Test
        void serialization() throws JsonProcessingException {
            assertEquals("\"text\"", mapper.writeValueAsString(InputModality.TEXT));
        }

        @Test
        void deserialization() throws JsonProcessingException {
            assertEquals(InputModality.IMAGE, mapper.readValue("\"image\"", InputModality.class));
        }
    }

    // --- ModelCost ---

    @Nested
    class ModelCostTests {

        @Test
        void creation() {
            var cost = new ModelCost(3.0, 15.0, 0.3, 3.75);
            assertEquals(3.0, cost.input());
            assertEquals(15.0, cost.output());
            assertEquals(0.3, cost.cacheRead());
            assertEquals(3.75, cost.cacheWrite());
        }

        @Test
        void roundTrip() throws JsonProcessingException {
            var original = new ModelCost(3.0, 15.0, 0.3, 3.75);
            var json = mapper.writeValueAsString(original);
            var restored = mapper.readValue(json, ModelCost.class);
            assertEquals(original, restored);
        }
    }

    // --- Model ---

    @Nested
    class ModelTests {

        private Model createSample() {
            return new Model(
                "claude-opus-4-6", "Claude Opus 4",
                Api.ANTHROPIC_MESSAGES, Provider.ANTHROPIC,
                "https://api.anthropic.com", true,
                List.of(InputModality.TEXT, InputModality.IMAGE),
                new ModelCost(15.0, 75.0, 1.5, 18.75),
                200000, 32000, null, null,
                null
            );
        }

        @Test
        void creation() {
            var model = createSample();
            assertEquals("claude-opus-4-6", model.id());
            assertEquals("Claude Opus 4", model.name());
            assertEquals(Api.ANTHROPIC_MESSAGES, model.api());
            assertEquals(Provider.ANTHROPIC, model.provider());
            assertTrue(model.reasoning());
            assertEquals(2, model.inputModalities().size());
            assertEquals(200000, model.contextWindow());
            assertEquals(32000, model.maxTokens());
            assertNull(model.headers());
        }

        @Test
        void creationWithHeaders() {
            var model = new Model(
                "gpt-4o", "GPT-4o",
                Api.OPENAI_RESPONSES, Provider.OPENAI,
                "https://api.openai.com", false,
                List.of(InputModality.TEXT, InputModality.IMAGE),
                new ModelCost(2.5, 10.0, 0.0, 0.0),
                128000, 16384,
                Map.of("X-Custom", "value"),
                null,
                null
            );
            assertNotNull(model.headers());
            assertEquals("value", model.headers().get("X-Custom"));
        }

        @Test
        void serialization() throws JsonProcessingException {
            var model = createSample();
            var json = mapper.readTree(mapper.writeValueAsString(model));
            assertEquals("claude-opus-4-6", json.get("id").asText());
            assertEquals("Claude Opus 4", json.get("name").asText());
            assertEquals("anthropic-messages", json.get("api").asText());
            assertEquals("anthropic", json.get("provider").asText());
            assertTrue(json.get("reasoning").asBoolean());
            assertTrue(json.get("inputModalities").isArray());
            assertEquals("text", json.get("inputModalities").get(0).asText());
            assertEquals("image", json.get("inputModalities").get(1).asText());
            assertEquals(200000, json.get("contextWindow").asInt());
            assertEquals(32000, json.get("maxTokens").asInt());
            assertTrue(json.has("cost"));
            assertEquals(15.0, json.get("cost").get("input").asDouble());
        }

        @Test
        void deserialization() throws JsonProcessingException {
            var json = """
                {
                  "id": "gpt-4o",
                  "name": "GPT-4o",
                  "api": "openai-responses",
                  "provider": "openai",
                  "baseUrl": "https://api.openai.com",
                  "reasoning": false,
                  "inputModalities": ["text", "image"],
                  "cost": {"input": 2.5, "output": 10.0, "cacheRead": 0.0, "cacheWrite": 0.0},
                  "contextWindow": 128000,
                  "maxTokens": 16384,
                  "headers": {"Authorization": "Bearer xxx"}
                }""";
            var model = mapper.readValue(json, Model.class);
            assertEquals("gpt-4o", model.id());
            assertEquals(Api.OPENAI_RESPONSES, model.api());
            assertEquals(Provider.OPENAI, model.provider());
            assertFalse(model.reasoning());
            assertEquals(128000, model.contextWindow());
            assertEquals("Bearer xxx", model.headers().get("Authorization"));
        }

        @Test
        void deserializationWithoutOptionals() throws JsonProcessingException {
            var json = """
                {
                  "id": "mistral-large",
                  "name": "Mistral Large",
                  "api": "mistral-conversations",
                  "provider": "mistral",
                  "baseUrl": "https://api.mistral.ai",
                  "reasoning": false,
                  "inputModalities": ["text"],
                  "cost": {"input": 2.0, "output": 6.0, "cacheRead": 0.0, "cacheWrite": 0.0},
                  "contextWindow": 128000,
                  "maxTokens": 8192
                }""";
            var model = mapper.readValue(json, Model.class);
            assertEquals("mistral-large", model.id());
            assertEquals(Api.MISTRAL_CONVERSATIONS, model.api());
            assertNull(model.headers());
        }

        @Test
        void roundTrip() throws JsonProcessingException {
            var original = createSample();
            var json = mapper.writeValueAsString(original);
            var restored = mapper.readValue(json, Model.class);
            assertEquals(original, restored);
        }
    }
}
