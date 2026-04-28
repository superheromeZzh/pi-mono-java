package com.campusclaw.codingagent.resolver;

import java.util.Optional;

import com.campusclaw.ai.model.ModelRegistry;
import com.campusclaw.ai.types.Model;
import com.campusclaw.codingagent.settings.Settings;
import com.campusclaw.codingagent.settings.SettingsManager;

import org.springframework.stereotype.Service;

/**
 * Resolves the {@link Model} that should be used for a named "agent role"
 * (e.g. {@code summarizer}, {@code subagent}). The resolution chain mirrors
 * opencode's {@code agent.<name>.model}:
 *
 * <ol>
 *   <li>{@code settings.agent.<name>.model}</li>
 *   <li>The fallback model passed in by the caller (typically the active session model)</li>
 * </ol>
 *
 * <p>Reusing this in {@link com.campusclaw.codingagent.compaction.Compactor},
 * cron jobs and future subagents lets users send cheap work (summarisation,
 * planning) to a smaller / faster model without affecting the foreground chat.
 */
@Service
public class AgentModelResolver {

    private final SettingsManager settingsManager;
    private final ModelRegistry modelRegistry;

    public AgentModelResolver(SettingsManager settingsManager, ModelRegistry modelRegistry) {
        this.settingsManager = settingsManager;
        this.modelRegistry = modelRegistry;
    }

    /**
     * Resolves the model for a named agent role. Falls back to {@code defaultModel}
     * if no override is configured or the override fails to resolve.
     */
    public Model resolve(String agentName, Model defaultModel) {
        return findOverride(agentName)
                .flatMap(this::lookupAcrossProviders)
                .orElse(defaultModel);
    }

    private Optional<String> findOverride(String agentName) {
        Settings settings;
        try {
            settings = settingsManager.load();
        } catch (Exception e) {
            return Optional.empty();
        }
        if (settings.agent() == null) { return Optional.empty(); }
        Settings.AgentConfig cfg = settings.agent().get(agentName);
        if (cfg == null || cfg.model() == null || cfg.model().isBlank()) {
            return Optional.empty();
        }
        return Optional.of(cfg.model());
    }

    private Optional<Model> lookupAcrossProviders(String modelId) {
        // Strip optional "provider/" prefix; ModelRegistry indexes by id within each provider.
        String stripped = modelId;
        int slash = stripped.indexOf('/');
        if (slash >= 0) { stripped = stripped.substring(slash + 1); }
        for (var provider : modelRegistry.getProviders()) {
            var found = modelRegistry.getModel(provider, stripped);
            if (found.isPresent()) { return found; }
        }
        return Optional.empty();
    }
}
