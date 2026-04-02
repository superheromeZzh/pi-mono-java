package com.huawei.hicampus.mate.matecampusclaw.ai.model;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import com.huawei.hicampus.mate.matecampusclaw.ai.types.*;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;

/**
 * Registry for {@link Model} definitions, indexed by {@link Provider} and model id.
 *
 * <p>Uses a two-level {@code Map<Provider, Map<String, Model>>} for efficient
 * lookup by provider + modelId. Pre-populates common models via {@link #init()}.
 *
 * <p>Thread-safe: all mutation methods synchronize on the internal lock.
 */
@Service
public class ModelRegistry {

    private static final Logger log = LoggerFactory.getLogger(ModelRegistry.class);

    private final Object lock = new Object();

    /** Two-level index: Provider -> (modelId -> Model). */
    private final Map<Provider, Map<String, Model>> models = new ConcurrentHashMap<>();

    /**
     * Looks up a model by provider and model id.
     *
     * @param provider the LLM provider
     * @param modelId  the model identifier
     * @return the model, or empty if not registered
     */
    public Optional<Model> getModel(Provider provider, String modelId) {
        var byId = models.get(provider);
        if (byId == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(byId.get(modelId));
    }

    /**
     * Returns all models registered for a given provider.
     *
     * @param provider the LLM provider
     * @return an unmodifiable list of models (empty if none registered)
     */
    public List<Model> getModels(Provider provider) {
        var byId = models.get(provider);
        if (byId == null) {
            return List.of();
        }
        synchronized (lock) {
            return List.copyOf(byId.values());
        }
    }

    /**
     * Returns all providers that have at least one model registered.
     *
     * @return an unmodifiable list of providers
     */
    public List<Provider> getProviders() {
        synchronized (lock) {
            return List.copyOf(models.keySet());
        }
    }

    /**
     * Registers a single model. Replaces any existing model with the same
     * provider + id combination.
     *
     * @param model the model to register
     * @throws NullPointerException if model is null
     */
    public void register(Model model) {
        Objects.requireNonNull(model, "model must not be null");
        synchronized (lock) {
            models.computeIfAbsent(model.provider(), k -> new ConcurrentHashMap<>())
                .put(model.id(), model);
        }
        log.debug("Registered model {} for provider {}", model.id(), model.provider().value());
    }

    /**
     * Registers multiple models at once.
     *
     * @param modelList the models to register
     * @throws NullPointerException if modelList is null
     */
    public void registerAll(List<Model> modelList) {
        Objects.requireNonNull(modelList, "modelList must not be null");
        synchronized (lock) {
            for (var model : modelList) {
                models.computeIfAbsent(model.provider(), k -> new ConcurrentHashMap<>())
                    .put(model.id(), model);
            }
        }
        log.debug("Registered {} model(s)", modelList.size());
    }

    // -- Static utility functions (aligned with TS models.ts) --

    /**
     * Calculates cost breakdown for a model and usage.
     * Returns a new Cost with computed values.
     */
    public static Cost calculateCost(Model model, Usage usage) {
        if (model.cost() == null) return Cost.empty();
        var mc = model.cost();
        double input = usage.input() * mc.input() / 1_000_000.0;
        double output = usage.output() * mc.output() / 1_000_000.0;
        double cacheRead = usage.cacheRead() * mc.cacheRead() / 1_000_000.0;
        double cacheWrite = usage.cacheWrite() * mc.cacheWrite() / 1_000_000.0;
        return new Cost(input, output, cacheRead, cacheWrite,
            input + output + cacheRead + cacheWrite);
    }

    /**
     * Check if a model supports xhigh thinking level.
     * Supported: GPT-5.x families, Opus 4.6 models.
     */
    public static boolean supportsXhigh(Model model) {
        String id = model.id();
        return id.contains("gpt-5.2") || id.contains("gpt-5.3") || id.contains("gpt-5.4")
            || id.contains("opus-4-6") || id.contains("opus-4.6");
    }

    /**
     * Check if two models are equal by comparing both id and provider.
     */
    public static boolean modelsAreEqual(Model a, Model b) {
        if (a == null || b == null) return false;
        return a.id().equals(b.id()) && a.provider() == b.provider();
    }

    /**
     * Returns all registered models across all providers.
     */
    public List<Model> getAllModels() {
        synchronized (lock) {
            var all = new ArrayList<Model>();
            for (var byId : models.values()) {
                all.addAll(byId.values());
            }
            return all;
        }
    }

    /**
     * Removes all registered models.
     */
    public void clear() {
        synchronized (lock) {
            models.clear();
        }
        log.debug("Cleared all models from registry");
    }

    /**
     * Pre-registers commonly used models after bean construction.
     */
    @PostConstruct
    void init() {
        registerAll(builtInModels());
        log.info("ModelRegistry initialized with {} built-in model(s)", builtInModels().size());
    }

    /**
     * Returns the list of built-in models to pre-register.
     */
    static List<Model> builtInModels() {
        return List.of(
            // --- Anthropic ---
            new Model(
                "claude-sonnet-4-20250514", "Claude Sonnet 4",
                Api.ANTHROPIC_MESSAGES, Provider.ANTHROPIC,
                "https://api.anthropic.com", true,
                List.of(InputModality.TEXT, InputModality.IMAGE),
                new ModelCost(3.0, 15.0, 0.3, 3.75),
                200000, 16000, null, null, null
            ),
            new Model(
                "claude-opus-4-20250115", "Claude Opus 4",
                Api.ANTHROPIC_MESSAGES, Provider.ANTHROPIC,
                "https://api.anthropic.com", true,
                List.of(InputModality.TEXT, InputModality.IMAGE),
                new ModelCost(15.0, 75.0, 1.5, 18.75),
                200000, 32000, null, null, null
            ),
            new Model(
                "claude-haiku-3-5", "Claude 3.5 Haiku",
                Api.ANTHROPIC_MESSAGES, Provider.ANTHROPIC,
                "https://api.anthropic.com", false,
                List.of(InputModality.TEXT, InputModality.IMAGE),
                new ModelCost(0.8, 4.0, 0.08, 1.0),
                200000, 8192, null, null, null
            ),

            // --- OpenAI ---
            new Model(
                "gpt-4o", "GPT-4o",
                Api.OPENAI_RESPONSES, Provider.OPENAI,
                "https://api.openai.com", false,
                List.of(InputModality.TEXT, InputModality.IMAGE),
                new ModelCost(2.5, 10.0, 1.25, 2.5),
                128000, 16384, null, null, null
            ),
            new Model(
                "gpt-4o-mini", "GPT-4o Mini",
                Api.OPENAI_RESPONSES, Provider.OPENAI,
                "https://api.openai.com", false,
                List.of(InputModality.TEXT, InputModality.IMAGE),
                new ModelCost(0.15, 0.6, 0.075, 0.15),
                128000, 16384, null, null, null
            ),
            new Model(
                "o3", "o3",
                Api.OPENAI_RESPONSES, Provider.OPENAI,
                "https://api.openai.com", true,
                List.of(InputModality.TEXT, InputModality.IMAGE),
                new ModelCost(10.0, 40.0, 2.5, 10.0),
                200000, 100000, null, null, null
            ),
            new Model(
                "o4-mini", "o4-mini",
                Api.OPENAI_RESPONSES, Provider.OPENAI,
                "https://api.openai.com", true,
                List.of(InputModality.TEXT, InputModality.IMAGE),
                new ModelCost(1.1, 4.4, 0.275, 1.1),
                200000, 100000, null, null, null
            ),

            // --- ZAI ---
            new Model("glm-4.5", "GLM-4.5", Api.OPENAI_COMPLETIONS, Provider.ZAI,
                "https://api.z.ai/api/coding/paas/v4", true, List.of(InputModality.TEXT),
                new ModelCost(0.6, 2.2, 0.11, 0), 131072, 98304, null, "zai", null),
            new Model("glm-4.5-air", "GLM-4.5-Air", Api.OPENAI_COMPLETIONS, Provider.ZAI,
                "https://api.z.ai/api/coding/paas/v4", true, List.of(InputModality.TEXT),
                new ModelCost(0.2, 1.1, 0.03, 0), 131072, 98304, null, "zai", null),
            new Model("glm-4.5-flash", "GLM-4.5-Flash", Api.OPENAI_COMPLETIONS, Provider.ZAI,
                "https://api.z.ai/api/coding/paas/v4", true, List.of(InputModality.TEXT),
                new ModelCost(0, 0, 0, 0), 131072, 98304, null, "zai", null),
            new Model("glm-4.5v", "GLM-4.5V", Api.OPENAI_COMPLETIONS, Provider.ZAI,
                "https://api.z.ai/api/coding/paas/v4", true, List.of(InputModality.TEXT, InputModality.IMAGE),
                new ModelCost(0.6, 1.8, 0, 0), 64000, 16384, null, "zai", null),
            new Model("glm-4.6", "GLM-4.6", Api.OPENAI_COMPLETIONS, Provider.ZAI,
                "https://api.z.ai/api/coding/paas/v4", true, List.of(InputModality.TEXT),
                new ModelCost(0.6, 2.2, 0.11, 0), 204800, 131072, null, "zai", null),
            new Model("glm-4.6v", "GLM-4.6V", Api.OPENAI_COMPLETIONS, Provider.ZAI,
                "https://api.z.ai/api/coding/paas/v4", true, List.of(InputModality.TEXT, InputModality.IMAGE),
                new ModelCost(0.3, 0.9, 0, 0), 128000, 32768, null, "zai", null),
            new Model("glm-4.7", "GLM-4.7", Api.OPENAI_COMPLETIONS, Provider.ZAI,
                "https://api.z.ai/api/coding/paas/v4", true, List.of(InputModality.TEXT),
                new ModelCost(0.6, 2.2, 0.11, 0), 204800, 131072, null, "zai", null),
            new Model("glm-4.7-flash", "GLM-4.7-Flash", Api.OPENAI_COMPLETIONS, Provider.ZAI,
                "https://api.z.ai/api/coding/paas/v4", true, List.of(InputModality.TEXT),
                new ModelCost(0, 0, 0, 0), 200000, 131072, null, "zai", null),
            new Model("glm-5", "GLM-5", Api.OPENAI_COMPLETIONS, Provider.ZAI,
                "https://api.z.ai/api/coding/paas/v4", true, List.of(InputModality.TEXT),
                new ModelCost(1.0, 3.2, 0.2, 0), 204800, 131072, null, "zai", null),
            new Model("glm-5-turbo", "GLM-5-Turbo", Api.OPENAI_COMPLETIONS, Provider.ZAI,
                "https://api.z.ai/api/coding/paas/v4", true, List.of(InputModality.TEXT),
                new ModelCost(1.2, 4.0, 0.24, 0), 200000, 131072, null, "zai", null),

            // --- Kimi Coding ---
            new Model("k2p5", "Kimi K2.5", Api.ANTHROPIC_MESSAGES, Provider.KIMI_CODING,
                "https://api.kimi.com/coding", true, List.of(InputModality.TEXT, InputModality.IMAGE),
                new ModelCost(0, 0, 0, 0), 262144, 32768, null, null, null),
            new Model("kimi-k2-thinking", "Kimi K2 Thinking", Api.ANTHROPIC_MESSAGES, Provider.KIMI_CODING,
                "https://api.kimi.com/coding", true, List.of(InputModality.TEXT),
                new ModelCost(0, 0, 0, 0), 262144, 32768, null, null, null),

            // --- MiniMax ---
            new Model("MiniMax-M2.7", "MiniMax-M2.7", Api.ANTHROPIC_MESSAGES, Provider.MINIMAX,
                "https://api.minimax.io/anthropic", true, List.of(InputModality.TEXT),
                new ModelCost(0.3, 1.2, 0.06, 0.375), 204800, 131072, null, null, null),
            new Model("MiniMax-M2.7-highspeed", "MiniMax-M2.7-highspeed", Api.ANTHROPIC_MESSAGES, Provider.MINIMAX,
                "https://api.minimax.io/anthropic", true, List.of(InputModality.TEXT),
                new ModelCost(0.6, 2.4, 0.06, 0.375), 204800, 131072, null, null, null),

            // --- MiniMax CN ---
            new Model("MiniMax-M2.7", "MiniMax-M2.7", Api.ANTHROPIC_MESSAGES, Provider.MINIMAX_CN,
                "https://api.minimaxi.com/anthropic", true, List.of(InputModality.TEXT),
                new ModelCost(0.3, 1.2, 0.06, 0.375), 204800, 131072, null, null, null),
            new Model("MiniMax-M2.7-highspeed", "MiniMax-M2.7-highspeed", Api.ANTHROPIC_MESSAGES, Provider.MINIMAX_CN,
                "https://api.minimaxi.com/anthropic", true, List.of(InputModality.TEXT),
                new ModelCost(0.6, 2.4, 0.06, 0.375), 204800, 131072, null, null, null),

            // --- Google Generative AI ---
            new Model("gemini-2.5-pro", "Gemini 2.5 Pro", Api.GOOGLE_GENERATIVE_AI, Provider.GOOGLE,
                "https://generativelanguage.googleapis.com/v1beta", true,
                List.of(InputModality.TEXT, InputModality.IMAGE),
                new ModelCost(1.25, 10.0, 0.31, 2.5), 1048576, 65536, null, null, null),
            new Model("gemini-2.5-flash", "Gemini 2.5 Flash", Api.GOOGLE_GENERATIVE_AI, Provider.GOOGLE,
                "https://generativelanguage.googleapis.com/v1beta", true,
                List.of(InputModality.TEXT, InputModality.IMAGE),
                new ModelCost(0.15, 0.6, 0.04, 0.15), 1048576, 65536, null, null, null),
            new Model("gemini-2.0-flash", "Gemini 2.0 Flash", Api.GOOGLE_GENERATIVE_AI, Provider.GOOGLE,
                "https://generativelanguage.googleapis.com/v1beta", false,
                List.of(InputModality.TEXT, InputModality.IMAGE),
                new ModelCost(0.1, 0.4, 0.025, 0.1), 1048576, 8192, null, null, null),

            // --- Google Vertex AI ---
            new Model("gemini-2.5-pro", "Gemini 2.5 Pro (Vertex)", Api.GOOGLE_VERTEX, Provider.GOOGLE_VERTEX,
                null, true, List.of(InputModality.TEXT, InputModality.IMAGE),
                new ModelCost(1.25, 10.0, 0.31, 2.5), 1048576, 65536, null, null, null),
            new Model("gemini-2.5-flash", "Gemini 2.5 Flash (Vertex)", Api.GOOGLE_VERTEX, Provider.GOOGLE_VERTEX,
                null, true, List.of(InputModality.TEXT, InputModality.IMAGE),
                new ModelCost(0.15, 0.6, 0.04, 0.15), 1048576, 65536, null, null, null),

            // --- Mistral ---
            new Model("mistral-large-latest", "Mistral Large", Api.MISTRAL_CONVERSATIONS, Provider.MISTRAL,
                "https://api.mistral.ai/v1", true,
                List.of(InputModality.TEXT, InputModality.IMAGE),
                new ModelCost(2.0, 6.0, 0.5, 1.5), 131072, 8192, null, null, null),
            new Model("mistral-medium-latest", "Mistral Medium", Api.MISTRAL_CONVERSATIONS, Provider.MISTRAL,
                "https://api.mistral.ai/v1", false,
                List.of(InputModality.TEXT),
                new ModelCost(0.4, 2.0, 0.1, 0.5), 131072, 8192, null, null, null),
            new Model("mistral-small-latest", "Mistral Small", Api.MISTRAL_CONVERSATIONS, Provider.MISTRAL,
                "https://api.mistral.ai/v1", false,
                List.of(InputModality.TEXT),
                new ModelCost(0.1, 0.3, 0.025, 0.075), 131072, 8192, null, null, null),
            new Model("codestral-latest", "Codestral", Api.MISTRAL_CONVERSATIONS, Provider.MISTRAL,
                "https://api.mistral.ai/v1", false,
                List.of(InputModality.TEXT),
                new ModelCost(0.3, 0.9, 0.075, 0.225), 262144, 8192, null, null, null),

            // --- Azure OpenAI ---
            new Model("gpt-4o", "GPT-4o (Azure)", Api.OPENAI_RESPONSES, Provider.AZURE_OPENAI,
                null, false, List.of(InputModality.TEXT, InputModality.IMAGE),
                new ModelCost(2.5, 10.0, 1.25, 2.5), 128000, 16384, null, null, null),
            new Model("gpt-4o-mini", "GPT-4o Mini (Azure)", Api.OPENAI_RESPONSES, Provider.AZURE_OPENAI,
                null, false, List.of(InputModality.TEXT, InputModality.IMAGE),
                new ModelCost(0.15, 0.6, 0.075, 0.15), 128000, 16384, null, null, null),
            new Model("o3", "o3 (Azure)", Api.OPENAI_RESPONSES, Provider.AZURE_OPENAI,
                null, true, List.of(InputModality.TEXT, InputModality.IMAGE),
                new ModelCost(10.0, 40.0, 2.5, 10.0), 200000, 100000, null, null, null),

            // --- xAI ---
            new Model("grok-3", "Grok 3", Api.OPENAI_COMPLETIONS, Provider.XAI,
                "https://api.x.ai/v1", true, List.of(InputModality.TEXT),
                new ModelCost(3.0, 15.0, 0.75, 3.75), 131072, 131072, null, null, null),
            new Model("grok-3-mini", "Grok 3 Mini", Api.OPENAI_COMPLETIONS, Provider.XAI,
                "https://api.x.ai/v1", true, List.of(InputModality.TEXT),
                new ModelCost(0.3, 0.5, 0.075, 0.125), 131072, 131072, null, null, null),
            new Model("grok-3-fast", "Grok 3 Fast", Api.OPENAI_COMPLETIONS, Provider.XAI,
                "https://api.x.ai/v1", false, List.of(InputModality.TEXT),
                new ModelCost(5.0, 25.0, 1.25, 6.25), 131072, 131072, null, null, null),

            // --- Groq ---
            new Model("llama-3.3-70b-versatile", "Llama 3.3 70B (Groq)", Api.OPENAI_COMPLETIONS, Provider.GROQ,
                "https://api.groq.com/openai/v1", false, List.of(InputModality.TEXT),
                new ModelCost(0.59, 0.79, 0, 0), 131072, 32768, null, null, null),
            new Model("llama-4-maverick-17b-128e-instruct", "Llama 4 Maverick (Groq)", Api.OPENAI_COMPLETIONS, Provider.GROQ,
                "https://api.groq.com/openai/v1", false, List.of(InputModality.TEXT),
                new ModelCost(0.2, 0.6, 0, 0), 131072, 32768, null, null, null),
            new Model("deepseek-r1-distill-llama-70b", "DeepSeek R1 70B (Groq)", Api.OPENAI_COMPLETIONS, Provider.GROQ,
                "https://api.groq.com/openai/v1", true, List.of(InputModality.TEXT),
                new ModelCost(0.59, 0.79, 0, 0), 131072, 16384, null, null, null),

            // --- OpenRouter ---
            new Model("anthropic/claude-sonnet-4", "Claude Sonnet 4 (OpenRouter)", Api.OPENAI_COMPLETIONS, Provider.OPENROUTER,
                "https://openrouter.ai/api/v1", true, List.of(InputModality.TEXT, InputModality.IMAGE),
                new ModelCost(3.0, 15.0, 0.3, 3.75), 200000, 16000, null, null, null),
            new Model("openai/gpt-4o", "GPT-4o (OpenRouter)", Api.OPENAI_COMPLETIONS, Provider.OPENROUTER,
                "https://openrouter.ai/api/v1", false, List.of(InputModality.TEXT, InputModality.IMAGE),
                new ModelCost(2.5, 10.0, 1.25, 2.5), 128000, 16384, null, null, null),
            new Model("google/gemini-2.5-pro", "Gemini 2.5 Pro (OpenRouter)", Api.OPENAI_COMPLETIONS, Provider.OPENROUTER,
                "https://openrouter.ai/api/v1", true, List.of(InputModality.TEXT, InputModality.IMAGE),
                new ModelCost(1.25, 10.0, 0.31, 2.5), 1048576, 65536, null, null, null),

            // --- OpenAI Codex ---
            new Model("codex-mini-latest", "Codex Mini", Api.OPENAI_RESPONSES, Provider.OPENAI_CODEX,
                "https://api.openai.com", true, List.of(InputModality.TEXT),
                new ModelCost(1.5, 6.0, 0.375, 1.5), 192000, 100000, null, null, null),

            // --- GitHub Copilot ---
            new Model("claude-sonnet-4", "Claude Sonnet 4 (Copilot)", Api.ANTHROPIC_MESSAGES, Provider.GITHUB_COPILOT,
                "https://api.githubcopilot.com", true, List.of(InputModality.TEXT, InputModality.IMAGE),
                new ModelCost(0, 0, 0, 0), 200000, 16000, null, null, null),
            new Model("gpt-4o", "GPT-4o (Copilot)", Api.OPENAI_COMPLETIONS, Provider.GITHUB_COPILOT,
                "https://api.githubcopilot.com", false, List.of(InputModality.TEXT, InputModality.IMAGE),
                new ModelCost(0, 0, 0, 0), 128000, 16384, null, null, null),

            // --- Cerebras ---
            new Model("llama-4-scout-17b-16e-instruct", "Llama 4 Scout (Cerebras)", Api.OPENAI_COMPLETIONS, Provider.CEREBRAS,
                "https://api.cerebras.ai/v1", false, List.of(InputModality.TEXT),
                new ModelCost(0, 0, 0, 0), 131072, 16384, null, null, null),
            new Model("llama-3.3-70b", "Llama 3.3 70B (Cerebras)", Api.OPENAI_COMPLETIONS, Provider.CEREBRAS,
                "https://api.cerebras.ai/v1", false, List.of(InputModality.TEXT),
                new ModelCost(0, 0, 0, 0), 131072, 16384, null, null, null),

            // --- HuggingFace ---
            new Model("Qwen/Qwen3-235B-A22B", "Qwen3 235B (HF)", Api.OPENAI_COMPLETIONS, Provider.HUGGINGFACE,
                "https://router.huggingface.co/v1", true, List.of(InputModality.TEXT),
                new ModelCost(0, 0, 0, 0), 131072, 32768, null, null, null),
            new Model("meta-llama/Llama-4-Maverick-17B-128E-Instruct-FP8", "Llama 4 Maverick (HF)", Api.OPENAI_COMPLETIONS, Provider.HUGGINGFACE,
                "https://router.huggingface.co/v1", false, List.of(InputModality.TEXT),
                new ModelCost(0, 0, 0, 0), 131072, 32768, null, null, null)
        );
    }
}
