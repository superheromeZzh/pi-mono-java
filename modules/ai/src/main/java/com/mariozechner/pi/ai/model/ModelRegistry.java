package com.mariozechner.pi.ai.model;

import com.mariozechner.pi.ai.types.*;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

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
                200000, 16000, null, null
            ),
            new Model(
                "claude-opus-4-20250115", "Claude Opus 4",
                Api.ANTHROPIC_MESSAGES, Provider.ANTHROPIC,
                "https://api.anthropic.com", true,
                List.of(InputModality.TEXT, InputModality.IMAGE),
                new ModelCost(15.0, 75.0, 1.5, 18.75),
                200000, 32000, null, null
            ),
            new Model(
                "claude-haiku-3-5", "Claude 3.5 Haiku",
                Api.ANTHROPIC_MESSAGES, Provider.ANTHROPIC,
                "https://api.anthropic.com", false,
                List.of(InputModality.TEXT, InputModality.IMAGE),
                new ModelCost(0.8, 4.0, 0.08, 1.0),
                200000, 8192, null, null
            ),

            // --- OpenAI ---
            new Model(
                "gpt-4o", "GPT-4o",
                Api.OPENAI_RESPONSES, Provider.OPENAI,
                "https://api.openai.com", false,
                List.of(InputModality.TEXT, InputModality.IMAGE),
                new ModelCost(2.5, 10.0, 1.25, 2.5),
                128000, 16384, null, null
            ),
            new Model(
                "gpt-4o-mini", "GPT-4o Mini",
                Api.OPENAI_RESPONSES, Provider.OPENAI,
                "https://api.openai.com", false,
                List.of(InputModality.TEXT, InputModality.IMAGE),
                new ModelCost(0.15, 0.6, 0.075, 0.15),
                128000, 16384, null, null
            ),
            new Model(
                "o3", "o3",
                Api.OPENAI_RESPONSES, Provider.OPENAI,
                "https://api.openai.com", true,
                List.of(InputModality.TEXT, InputModality.IMAGE),
                new ModelCost(10.0, 40.0, 2.5, 10.0),
                200000, 100000, null, null
            ),
            new Model(
                "o4-mini", "o4-mini",
                Api.OPENAI_RESPONSES, Provider.OPENAI,
                "https://api.openai.com", true,
                List.of(InputModality.TEXT, InputModality.IMAGE),
                new ModelCost(1.1, 4.4, 0.275, 1.1),
                200000, 100000, null, null
            ),

            // --- ZAI ---
            new Model("glm-4.5", "GLM-4.5", Api.OPENAI_COMPLETIONS, Provider.ZAI,
                "https://api.z.ai/api/coding/paas/v4", true, List.of(InputModality.TEXT),
                new ModelCost(0.6, 2.2, 0.11, 0), 131072, 98304, null, "zai"),
            new Model("glm-4.5-air", "GLM-4.5-Air", Api.OPENAI_COMPLETIONS, Provider.ZAI,
                "https://api.z.ai/api/coding/paas/v4", true, List.of(InputModality.TEXT),
                new ModelCost(0.2, 1.1, 0.03, 0), 131072, 98304, null, "zai"),
            new Model("glm-4.5-flash", "GLM-4.5-Flash", Api.OPENAI_COMPLETIONS, Provider.ZAI,
                "https://api.z.ai/api/coding/paas/v4", true, List.of(InputModality.TEXT),
                new ModelCost(0, 0, 0, 0), 131072, 98304, null, "zai"),
            new Model("glm-4.5v", "GLM-4.5V", Api.OPENAI_COMPLETIONS, Provider.ZAI,
                "https://api.z.ai/api/coding/paas/v4", true, List.of(InputModality.TEXT, InputModality.IMAGE),
                new ModelCost(0.6, 1.8, 0, 0), 64000, 16384, null, "zai"),
            new Model("glm-4.6", "GLM-4.6", Api.OPENAI_COMPLETIONS, Provider.ZAI,
                "https://api.z.ai/api/coding/paas/v4", true, List.of(InputModality.TEXT),
                new ModelCost(0.6, 2.2, 0.11, 0), 204800, 131072, null, "zai"),
            new Model("glm-4.6v", "GLM-4.6V", Api.OPENAI_COMPLETIONS, Provider.ZAI,
                "https://api.z.ai/api/coding/paas/v4", true, List.of(InputModality.TEXT, InputModality.IMAGE),
                new ModelCost(0.3, 0.9, 0, 0), 128000, 32768, null, "zai"),
            new Model("glm-4.7", "GLM-4.7", Api.OPENAI_COMPLETIONS, Provider.ZAI,
                "https://api.z.ai/api/coding/paas/v4", true, List.of(InputModality.TEXT),
                new ModelCost(0.6, 2.2, 0.11, 0), 204800, 131072, null, "zai"),
            new Model("glm-4.7-flash", "GLM-4.7-Flash", Api.OPENAI_COMPLETIONS, Provider.ZAI,
                "https://api.z.ai/api/coding/paas/v4", true, List.of(InputModality.TEXT),
                new ModelCost(0, 0, 0, 0), 200000, 131072, null, "zai"),
            new Model("glm-5", "GLM-5", Api.OPENAI_COMPLETIONS, Provider.ZAI,
                "https://api.z.ai/api/coding/paas/v4", true, List.of(InputModality.TEXT),
                new ModelCost(1.0, 3.2, 0.2, 0), 204800, 131072, null, "zai"),
            new Model("glm-5-turbo", "GLM-5-Turbo", Api.OPENAI_COMPLETIONS, Provider.ZAI,
                "https://api.z.ai/api/coding/paas/v4", true, List.of(InputModality.TEXT),
                new ModelCost(1.2, 4.0, 0.24, 0), 200000, 131072, null, "zai"),

            // --- Kimi Coding ---
            new Model("k2p5", "Kimi K2.5", Api.ANTHROPIC_MESSAGES, Provider.KIMI_CODING,
                "https://api.kimi.com/coding", true, List.of(InputModality.TEXT, InputModality.IMAGE),
                new ModelCost(0, 0, 0, 0), 262144, 32768, null, null),
            new Model("kimi-k2-thinking", "Kimi K2 Thinking", Api.ANTHROPIC_MESSAGES, Provider.KIMI_CODING,
                "https://api.kimi.com/coding", true, List.of(InputModality.TEXT),
                new ModelCost(0, 0, 0, 0), 262144, 32768, null, null),

            // --- MiniMax ---
            new Model("MiniMax-M2.7", "MiniMax-M2.7", Api.ANTHROPIC_MESSAGES, Provider.MINIMAX,
                "https://api.minimax.io/anthropic", true, List.of(InputModality.TEXT),
                new ModelCost(0.3, 1.2, 0.06, 0.375), 204800, 131072, null, null),
            new Model("MiniMax-M2.7-highspeed", "MiniMax-M2.7-highspeed", Api.ANTHROPIC_MESSAGES, Provider.MINIMAX,
                "https://api.minimax.io/anthropic", true, List.of(InputModality.TEXT),
                new ModelCost(0.6, 2.4, 0.06, 0.375), 204800, 131072, null, null),

            // --- MiniMax CN ---
            new Model("MiniMax-M2.7", "MiniMax-M2.7", Api.ANTHROPIC_MESSAGES, Provider.MINIMAX_CN,
                "https://api.minimaxi.com/anthropic", true, List.of(InputModality.TEXT),
                new ModelCost(0.3, 1.2, 0.06, 0.375), 204800, 131072, null, null),
            new Model("MiniMax-M2.7-highspeed", "MiniMax-M2.7-highspeed", Api.ANTHROPIC_MESSAGES, Provider.MINIMAX_CN,
                "https://api.minimaxi.com/anthropic", true, List.of(InputModality.TEXT),
                new ModelCost(0.6, 2.4, 0.06, 0.375), 204800, 131072, null, null)
        );
    }
}
