package com.huawei.hicampus.mate.matecampusclaw.codingagent.resolver;

import java.util.*;

import com.huawei.hicampus.mate.matecampusclaw.ai.model.ModelRegistry;
import com.huawei.hicampus.mate.matecampusclaw.ai.types.Model;
import com.huawei.hicampus.mate.matecampusclaw.ai.types.Provider;
import com.huawei.hicampus.mate.matecampusclaw.codingagent.settings.Settings;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jakarta.annotation.Nullable;

/**
 * Resolves model identifiers to {@link Model} instances with fallback
 * and scoped resolution support.
 *
 * <p>Resolution order:
 * <ol>
 *   <li>Exact match by model id across all providers</li>
 *   <li>Scoped model overrides (per-provider or per-task)</li>
 *   <li>Fallback to default model from settings</li>
 *   <li>Fallback to a known safe default</li>
 * </ol>
 */
public class ModelResolver {

    private static final Logger log = LoggerFactory.getLogger(ModelResolver.class);
    private static final String SAFE_DEFAULT = "claude-sonnet-4-20250514";

    private final ModelRegistry modelRegistry;
    private final Map<String, String> scopedOverrides = new LinkedHashMap<>();

    public ModelResolver(ModelRegistry modelRegistry) {
        this.modelRegistry = Objects.requireNonNull(modelRegistry);
    }

    /**
     * Resolves a model id to a Model, with fallback.
     *
     * @param modelId  the requested model id, or null to use defaults
     * @param settings optional settings for default model lookup
     * @return the resolved model
     * @throws IllegalArgumentException if no model can be resolved
     */
    public Model resolve(@Nullable String modelId, @Nullable Settings settings) {
        // 1. If modelId given, try exact match
        if (modelId != null && !modelId.isBlank()) {
            var model = findModel(modelId);
            if (model.isPresent()) return model.get();
            log.warn("Model '{}' not found, trying fallbacks", modelId);
        }

        // 2. Check scoped overrides
        if (modelId != null) {
            String override = scopedOverrides.get(modelId);
            if (override != null) {
                var model = findModel(override);
                if (model.isPresent()) return model.get();
            }
        }

        // 3. Default from settings
        if (settings != null && settings.defaultModel() != null) {
            var model = findModel(settings.defaultModel());
            if (model.isPresent()) return model.get();
        }

        // 4. Safe default
        var model = findModel(SAFE_DEFAULT);
        if (model.isPresent()) return model.get();

        throw new IllegalArgumentException("Cannot resolve model: " + modelId);
    }

    /**
     * Adds a scoped model override. When {@code alias} is requested,
     * {@code targetModelId} will be resolved instead.
     */
    public void addScopedOverride(String alias, String targetModelId) {
        scopedOverrides.put(alias, targetModelId);
    }

    /**
     * Removes a scoped model override.
     */
    public void removeScopedOverride(String alias) {
        scopedOverrides.remove(alias);
    }

    /**
     * Returns all scoped overrides.
     */
    public Map<String, String> getScopedOverrides() {
        return Map.copyOf(scopedOverrides);
    }

    /**
     * Finds a model by exact id across all providers.
     */
    public Optional<Model> findModel(String modelId) {
        for (Provider provider : modelRegistry.getProviders()) {
            var model = modelRegistry.getModel(provider, modelId);
            if (model.isPresent()) return model;
        }
        return Optional.empty();
    }

    /**
     * Returns all available model ids across all providers.
     */
    public List<String> getAllModelIds() {
        var ids = new ArrayList<String>();
        for (Provider provider : modelRegistry.getProviders()) {
            for (Model model : modelRegistry.getModels(provider)) {
                ids.add(model.id());
            }
        }
        return ids;
    }
}
