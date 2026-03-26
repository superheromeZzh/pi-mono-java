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
                200000, 16000, null
            ),
            new Model(
                "claude-opus-4-20250115", "Claude Opus 4",
                Api.ANTHROPIC_MESSAGES, Provider.ANTHROPIC,
                "https://api.anthropic.com", true,
                List.of(InputModality.TEXT, InputModality.IMAGE),
                new ModelCost(15.0, 75.0, 1.5, 18.75),
                200000, 32000, null
            ),
            new Model(
                "claude-haiku-3-5", "Claude 3.5 Haiku",
                Api.ANTHROPIC_MESSAGES, Provider.ANTHROPIC,
                "https://api.anthropic.com", false,
                List.of(InputModality.TEXT, InputModality.IMAGE),
                new ModelCost(0.8, 4.0, 0.08, 1.0),
                200000, 8192, null
            ),

            // --- OpenAI ---
            new Model(
                "gpt-4o", "GPT-4o",
                Api.OPENAI_RESPONSES, Provider.OPENAI,
                "https://api.openai.com", false,
                List.of(InputModality.TEXT, InputModality.IMAGE),
                new ModelCost(2.5, 10.0, 1.25, 2.5),
                128000, 16384, null
            ),
            new Model(
                "gpt-4o-mini", "GPT-4o Mini",
                Api.OPENAI_RESPONSES, Provider.OPENAI,
                "https://api.openai.com", false,
                List.of(InputModality.TEXT, InputModality.IMAGE),
                new ModelCost(0.15, 0.6, 0.075, 0.15),
                128000, 16384, null
            ),
            new Model(
                "o3", "o3",
                Api.OPENAI_RESPONSES, Provider.OPENAI,
                "https://api.openai.com", true,
                List.of(InputModality.TEXT, InputModality.IMAGE),
                new ModelCost(10.0, 40.0, 2.5, 10.0),
                200000, 100000, null
            ),
            new Model(
                "o4-mini", "o4-mini",
                Api.OPENAI_RESPONSES, Provider.OPENAI,
                "https://api.openai.com", true,
                List.of(InputModality.TEXT, InputModality.IMAGE),
                new ModelCost(1.1, 4.4, 0.275, 1.1),
                200000, 100000, null
            )
        );
    }
}
