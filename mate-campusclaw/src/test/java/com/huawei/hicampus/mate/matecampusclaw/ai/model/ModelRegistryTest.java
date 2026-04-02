package com.huawei.hicampus.mate.matecampusclaw.ai.model;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;

import com.huawei.hicampus.mate.matecampusclaw.ai.types.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class ModelRegistryTest {

    private ModelRegistry registry;

    @BeforeEach
    void setUp() {
        registry = new ModelRegistry();
    }

    private Model anthropicModel(String id, String name) {
        return new Model(
            id, name,
            Api.ANTHROPIC_MESSAGES, Provider.ANTHROPIC,
            "https://api.anthropic.com", false,
            List.of(InputModality.TEXT),
            new ModelCost(3.0, 15.0, 0.3, 3.75),
            200000, 8192, null, null,
            null
        );
    }

    private Model openaiModel(String id, String name) {
        return new Model(
            id, name,
            Api.OPENAI_RESPONSES, Provider.OPENAI,
            "https://api.openai.com", false,
            List.of(InputModality.TEXT, InputModality.IMAGE),
            new ModelCost(2.5, 10.0, 1.25, 2.5),
            128000, 16384, null, null,
            null
        );
    }

    @Nested
    class RegisterAndLookup {

        @Test
        void registerSingleModel() {
            var model = anthropicModel("claude-test", "Claude Test");
            registry.register(model);

            var result = registry.getModel(Provider.ANTHROPIC, "claude-test");
            assertTrue(result.isPresent());
            assertEquals(model, result.get());
        }

        @Test
        void registerReplacesExisting() {
            var original = anthropicModel("claude-test", "Original");
            var replacement = anthropicModel("claude-test", "Replacement");

            registry.register(original);
            registry.register(replacement);

            var result = registry.getModel(Provider.ANTHROPIC, "claude-test");
            assertTrue(result.isPresent());
            assertEquals("Replacement", result.get().name());
        }

        @Test
        void registerAllMultipleModels() {
            var models = List.of(
                anthropicModel("model-a", "A"),
                anthropicModel("model-b", "B"),
                openaiModel("model-c", "C")
            );
            registry.registerAll(models);

            assertTrue(registry.getModel(Provider.ANTHROPIC, "model-a").isPresent());
            assertTrue(registry.getModel(Provider.ANTHROPIC, "model-b").isPresent());
            assertTrue(registry.getModel(Provider.OPENAI, "model-c").isPresent());
        }

        @Test
        void registerNullModelThrows() {
            assertThrows(NullPointerException.class, () -> registry.register(null));
        }

        @Test
        void registerAllNullThrows() {
            assertThrows(NullPointerException.class, () -> registry.registerAll(null));
        }
    }

    @Nested
    class GetModel {

        @Test
        void returnsEmptyForUnknownProvider() {
            registry.register(anthropicModel("model-x", "X"));
            assertTrue(registry.getModel(Provider.OPENAI, "model-x").isEmpty());
        }

        @Test
        void returnsEmptyForUnknownModelId() {
            registry.register(anthropicModel("model-x", "X"));
            assertTrue(registry.getModel(Provider.ANTHROPIC, "nonexistent").isEmpty());
        }

        @Test
        void returnsEmptyWhenRegistryIsEmpty() {
            assertTrue(registry.getModel(Provider.ANTHROPIC, "any").isEmpty());
        }
    }

    @Nested
    class GetModels {

        @Test
        void returnsAllModelsForProvider() {
            registry.register(anthropicModel("a", "A"));
            registry.register(anthropicModel("b", "B"));
            registry.register(openaiModel("c", "C"));

            var anthropicModels = registry.getModels(Provider.ANTHROPIC);
            assertEquals(2, anthropicModels.size());

            var openaiModels = registry.getModels(Provider.OPENAI);
            assertEquals(1, openaiModels.size());
        }

        @Test
        void returnsEmptyListForUnknownProvider() {
            var result = registry.getModels(Provider.MISTRAL);
            assertNotNull(result);
            assertTrue(result.isEmpty());
        }

        @Test
        void returnedListIsUnmodifiable() {
            registry.register(anthropicModel("a", "A"));
            var models = registry.getModels(Provider.ANTHROPIC);
            assertThrows(UnsupportedOperationException.class, () -> models.add(null));
        }
    }

    @Nested
    class GetProviders {

        @Test
        void returnsProvidersWithModels() {
            registry.register(anthropicModel("a", "A"));
            registry.register(openaiModel("b", "B"));

            var providers = registry.getProviders();
            assertEquals(2, providers.size());
            assertTrue(providers.contains(Provider.ANTHROPIC));
            assertTrue(providers.contains(Provider.OPENAI));
        }

        @Test
        void returnsEmptyWhenNoModels() {
            assertTrue(registry.getProviders().isEmpty());
        }

        @Test
        void returnedListIsUnmodifiable() {
            registry.register(anthropicModel("a", "A"));
            var providers = registry.getProviders();
            assertThrows(UnsupportedOperationException.class, () -> providers.add(null));
        }
    }

    @Nested
    class Clear {

        @Test
        void removesAllModels() {
            registry.register(anthropicModel("a", "A"));
            registry.register(openaiModel("b", "B"));

            registry.clear();

            assertTrue(registry.getProviders().isEmpty());
            assertTrue(registry.getModel(Provider.ANTHROPIC, "a").isEmpty());
            assertTrue(registry.getModel(Provider.OPENAI, "b").isEmpty());
        }

        @Test
        void clearOnEmptyRegistryIsNoOp() {
            assertDoesNotThrow(() -> registry.clear());
        }
    }

    @Nested
    class BuiltInModels {

        @BeforeEach
        void initRegistry() {
            registry.init();
        }

        @Test
        void containsAnthropicModels() {
            var sonnet = registry.getModel(Provider.ANTHROPIC, "claude-sonnet-4-20250514");
            assertTrue(sonnet.isPresent());
            assertEquals("Claude Sonnet 4", sonnet.get().name());
            assertEquals(Api.ANTHROPIC_MESSAGES, sonnet.get().api());

            var opus = registry.getModel(Provider.ANTHROPIC, "claude-opus-4-20250115");
            assertTrue(opus.isPresent());
            assertEquals("Claude Opus 4", opus.get().name());
            assertTrue(opus.get().reasoning());

            var haiku = registry.getModel(Provider.ANTHROPIC, "claude-haiku-3-5");
            assertTrue(haiku.isPresent());
            assertEquals("Claude 3.5 Haiku", haiku.get().name());
        }

        @Test
        void containsOpenaiModels() {
            var gpt4o = registry.getModel(Provider.OPENAI, "gpt-4o");
            assertTrue(gpt4o.isPresent());
            assertEquals("GPT-4o", gpt4o.get().name());
            assertFalse(gpt4o.get().reasoning());

            var gpt4oMini = registry.getModel(Provider.OPENAI, "gpt-4o-mini");
            assertTrue(gpt4oMini.isPresent());

            var o3 = registry.getModel(Provider.OPENAI, "o3");
            assertTrue(o3.isPresent());
            assertTrue(o3.get().reasoning());

            var o4mini = registry.getModel(Provider.OPENAI, "o4-mini");
            assertTrue(o4mini.isPresent());
            assertTrue(o4mini.get().reasoning());
        }

        @Test
        void builtInModelsHaveValidCosts() {
            for (var model : ModelRegistry.builtInModels()) {
                assertTrue(model.cost().input() >= 0, "input cost must be non-negative: " + model.id());
                assertTrue(model.cost().output() >= 0, "output cost must be non-negative: " + model.id());
                assertTrue(model.cost().cacheRead() >= 0, "cacheRead cost must be non-negative: " + model.id());
                assertTrue(model.cost().cacheWrite() >= 0, "cacheWrite cost must be non-negative: " + model.id());
            }
        }

        @Test
        void builtInModelsHaveValidContextAndTokenLimits() {
            for (var model : ModelRegistry.builtInModels()) {
                assertTrue(model.contextWindow() > 0, "contextWindow must be positive: " + model.id());
                assertTrue(model.maxTokens() > 0, "maxTokens must be positive: " + model.id());
                assertTrue(model.maxTokens() <= model.contextWindow(),
                    "maxTokens should not exceed contextWindow: " + model.id());
            }
        }

        @Test
        void builtInProviderCount() {
            var providers = registry.getProviders();
            assertEquals(17, providers.size());
            assertTrue(providers.contains(Provider.ANTHROPIC));
            assertTrue(providers.contains(Provider.OPENAI));
            assertTrue(providers.contains(Provider.ZAI));
            assertTrue(providers.contains(Provider.KIMI_CODING));
            assertTrue(providers.contains(Provider.MINIMAX));
            assertTrue(providers.contains(Provider.MINIMAX_CN));
            assertTrue(providers.contains(Provider.GOOGLE));
            assertTrue(providers.contains(Provider.GOOGLE_VERTEX));
            assertTrue(providers.contains(Provider.MISTRAL));
            assertTrue(providers.contains(Provider.AZURE_OPENAI));
            assertTrue(providers.contains(Provider.XAI));
            assertTrue(providers.contains(Provider.GROQ));
            assertTrue(providers.contains(Provider.OPENROUTER));
            assertTrue(providers.contains(Provider.OPENAI_CODEX));
            assertTrue(providers.contains(Provider.GITHUB_COPILOT));
            assertTrue(providers.contains(Provider.CEREBRAS));
            assertTrue(providers.contains(Provider.HUGGINGFACE));
        }

        @Test
        void anthropicModelCount() {
            assertEquals(3, registry.getModels(Provider.ANTHROPIC).size());
        }

        @Test
        void openaiModelCount() {
            assertEquals(4, registry.getModels(Provider.OPENAI).size());
        }

        @Test
        void zaiModelCount() {
            assertEquals(10, registry.getModels(Provider.ZAI).size());
        }

        @Test
        void kimiCodingModelCount() {
            assertEquals(2, registry.getModels(Provider.KIMI_CODING).size());
        }

        @Test
        void minimaxModelCount() {
            assertEquals(2, registry.getModels(Provider.MINIMAX).size());
        }

        @Test
        void minimaxCnModelCount() {
            assertEquals(2, registry.getModels(Provider.MINIMAX_CN).size());
        }
    }
}
