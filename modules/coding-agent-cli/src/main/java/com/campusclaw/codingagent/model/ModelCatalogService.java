package com.campusclaw.codingagent.model;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import com.campusclaw.ai.env.ProviderConfigResolver;
import com.campusclaw.ai.model.ModelRegistry;
import com.campusclaw.ai.types.Model;
import com.campusclaw.ai.types.Provider;
import com.campusclaw.codingagent.settings.Settings;
import com.campusclaw.codingagent.settings.SettingsManager;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * Single source of truth for "which models is this CLI/server willing to expose".
 *
 * <p>The full {@link ModelRegistry} contains every built-in entry; users may
 * narrow the visible set through {@code settings.enabledModels}. This service
 * applies that filter consistently for the model picker, the {@code -m} flag's
 * scoped cycling, and the WS {@code list_models} command, so the three never
 * disagree.
 *
 * <p>Custom models registered via {@code settings.customModels} are always
 * included regardless of {@code enabledModels}: the user added them on
 * purpose, and gating them behind a second whitelist would be a footgun.
 */
@Service
public class ModelCatalogService {

    private final ModelRegistry modelRegistry;
    private final SettingsManager settingsManager;
    private final ProviderConfigResolver providerConfigResolver;

    @Autowired
    public ModelCatalogService(ModelRegistry modelRegistry, SettingsManager settingsManager,
                               ProviderConfigResolver providerConfigResolver) {
        this.modelRegistry = modelRegistry;
        this.settingsManager = settingsManager;
        this.providerConfigResolver = providerConfigResolver;
    }

    /** Convenience for tests that don't care about credential resolution. */
    public ModelCatalogService(ModelRegistry modelRegistry, SettingsManager settingsManager) {
        this(modelRegistry, settingsManager, null);
    }

    /**
     * Whether {@code model} has a usable API key resolvable from any source
     * (auth.json, {@code settings.provider}, env vars, or the model's
     * embedded {@code apiKey}). Models registered under
     * {@link Provider#CUSTOM} are always considered usable — the user added
     * them explicitly with their own credential.
     *
     * <p>When the resolver isn't wired in (test path), conservatively returns
     * {@code true} so the catalogue isn't unexpectedly empty.
     */
    public boolean hasCredentials(Model model) {
        if (model == null) { return false; }
        if (model.provider() == Provider.CUSTOM) { return true; }
        if (model.apiKey() != null && !model.apiKey().isBlank()) { return true; }
        if (providerConfigResolver == null) { return true; }
        try {
            var cfg = providerConfigResolver.resolve(model.provider(), model);
            return cfg.apiKey() != null && !cfg.apiKey().isBlank();
        } catch (Exception e) {
            return false;
        }
    }

    /** Every model registered, regardless of {@code enabledModels}. Sorted by provider+id. */
    public List<Model> getAllModels() {
        var all = new ArrayList<>(modelRegistry.getAllModels());
        all.sort(Comparator.comparing((Model m) -> m.provider().value()).thenComparing(Model::id));
        return all;
    }

    /**
     * The models a UI / API client should be allowed to switch to.
     *
     * <ul>
     *   <li>If {@code settings.enabledModels} is non-empty, return its
     *       glob/substring expansion plus all custom-provider models.</li>
     *   <li>Otherwise return everything.</li>
     * </ul>
     */
    public List<Model> getAvailableModels() {
        Settings settings = safeLoad();
        List<String> patterns = settings.enabledModels();
        if (patterns == null || patterns.isEmpty()) {
            return getAllModels();
        }

        var all = modelRegistry.getAllModels();
        var picked = new ArrayList<Model>();
        for (String pattern : patterns) {
            if (pattern == null) { continue; }
            String p = pattern.trim().toLowerCase();
            if (p.isEmpty()) { continue; }
            for (Model m : all) {
                if (matchesPattern(p, m) && !alreadyIn(picked, m)) {
                    picked.add(m);
                }
            }
        }
        // Always include custom-provider models, even if not matched by patterns.
        for (Model m : all) {
            if (m.provider() == Provider.CUSTOM && !alreadyIn(picked, m)) {
                picked.add(m);
            }
        }
        picked.sort(Comparator.comparing((Model m) -> m.provider().value()).thenComparing(Model::id));
        return picked;
    }

    /**
     * Returns {@code true} when the user has narrowed the visible model set —
     * useful when surfacing "showing X of Y" in a UI.
     */
    public boolean isFiltered() {
        Settings settings = safeLoad();
        return settings.enabledModels() != null && !settings.enabledModels().isEmpty();
    }

    /**
     * Glob/substring match against a model. Mirrors the legacy
     * {@code CampusClawCommand.matchesModelPattern} so {@code -m} cycling and
     * the WS catalogue agree.
     */
    public static boolean matchesPattern(String pattern, Model model) {
        String id = model.id().toLowerCase();
        String name = model.name().toLowerCase();
        String provider = model.provider().value().toLowerCase();

        // Strip optional ":thinking" suffix that the CLI accepts.
        int colonIdx = pattern.indexOf(':');
        String p = colonIdx >= 0 ? pattern.substring(0, colonIdx) : pattern;

        // provider/id form
        if (p.contains("/")) {
            String[] parts = p.split("/", 2);
            if (!provider.contains(parts[0])) { return false; }
            p = parts[1];
        }

        if (p.contains("*")) {
            String regex = p.replace("*", ".*");
            return id.matches(regex) || name.matches(regex);
        }
        return id.contains(p) || name.contains(p);
    }

    private Settings safeLoad() {
        try {
            return settingsManager.load();
        } catch (Exception e) {
            return Settings.empty();
        }
    }

    private static boolean alreadyIn(List<Model> list, Model m) {
        for (Model x : list) {
            if (ModelRegistry.modelsAreEqual(x, m)) { return true; }
        }
        return false;
    }
}
