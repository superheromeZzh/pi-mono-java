/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.hicampus.mate.matecampusclaw.codingagent.model;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

import com.huawei.hicampus.mate.matecampusclaw.ai.env.ProviderConfigResolver;
import com.huawei.hicampus.mate.matecampusclaw.ai.model.ModelRegistry;
import com.huawei.hicampus.mate.matecampusclaw.ai.types.Model;
import com.huawei.hicampus.mate.matecampusclaw.ai.types.Provider;
import com.huawei.hicampus.mate.matecampusclaw.codingagent.settings.Settings;
import com.huawei.hicampus.mate.matecampusclaw.codingagent.settings.SettingsManager;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * Single source of truth for "which models is this CLI/server willing to expose".
 *
 * <p>The full {@link ModelRegistry} contains every built-in entry. This
 * service narrows that down to what a UI / API client should actually be
 * offered: models with resolvable credentials, optionally further narrowed by
 * {@code settings.enabledModels}, with custom-provider models surfaced first.
 * It backs the WS {@code list_models} command and the REST settings snapshot;
 * the interactive picker and the {@code -m} flag query {@link ModelRegistry}
 * directly and are intentionally not credential-filtered.
 *
 * <p>Custom models registered via {@code settings.customModels} are always
 * included regardless of {@code enabledModels}: the user added them on
 * purpose, and gating them behind a second whitelist would be a footgun.
 *
 * @version [br_eCampusCore 25.1.0_Next, 2026/05/06]
 * @since [br_eCampusCore 25.1.0_Next]
 */
@Service
public class ModelCatalogService {

    // Orders custom-provider models first, then alphabetically by provider value and id.
    private static final Comparator<Model> CUSTOM_FIRST = Comparator.comparingInt(
                    (Model m) -> m.provider() == Provider.CUSTOM ? 0 : 1)
            .thenComparing(m -> m.provider().value())
            .thenComparing(Model::id);

    private final ModelRegistry modelRegistry;
    private final SettingsManager settingsManager;
    private final ProviderConfigResolver providerConfigResolver;

    @Autowired
    public ModelCatalogService(
            ModelRegistry modelRegistry,
            SettingsManager settingsManager,
            ProviderConfigResolver providerConfigResolver) {
        this.modelRegistry = modelRegistry;
        this.settingsManager = settingsManager;
        this.providerConfigResolver = providerConfigResolver;
    }

    /**
     * Convenience for tests that don't care about credential resolution.
     *
     * @param modelRegistry the modelRegistry
     * @param settingsManager the settingsManager
     */
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
     *
     * @param model the model
     * @return the result
     */
    public boolean hasCredentials(Model model) {
        if (model == null) {
            return false;
        }
        if (model.provider() == Provider.CUSTOM) {
            return true;
        }
        if (model.apiKey() != null && !model.apiKey().isBlank()) {
            return true;
        }
        if (providerConfigResolver == null) {
            return true;
        }
        try {
            var cfg = providerConfigResolver.resolve(model.provider(), model);
            return cfg.apiKey() != null && !cfg.apiKey().isBlank();
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Every model registered, regardless of {@code enabledModels} or credentials.
     * Sorted with custom-provider models first, then by provider value and id.
     * This is the "show everything" escape hatch behind {@code list_models all:true}.
     *
     * @return the result
     */
    public List<Model> getAllModels() {
        var all = new ArrayList<>(modelRegistry.getAllModels());
        all.sort(CUSTOM_FIRST);
        return all;
    }

    /**
     * The models a UI / API client should be allowed to switch to: only models
     * with resolvable credentials, optionally narrowed by
     * {@code settings.enabledModels}, with custom-provider models always
     * included and sorted to the head of the list.
     *
     * <ul>
     *   <li>Start from the {@code enabledModels} glob/substring expansion (plus
     *       all custom-provider models) when configured; otherwise the full
     *       registry.</li>
     *   <li>Drop any model the server cannot authenticate
     *       ({@link #hasCredentials}). Custom-provider models always pass.</li>
     *   <li>Sort custom-provider models first, then by provider value and id.</li>
     * </ul>
     *
     * <p>Callers that need the unfiltered registry should use
     * {@link #getAllModels()} instead.
     *
     * @return the result
     */
    public List<Model> getAvailableModels() {
        List<Model> candidates = narrowToEnabled(modelRegistry.getAllModels());

        // Keep only models the server can actually authenticate; custom always passes.
        var usable = new ArrayList<Model>(candidates.size());
        for (Model m : candidates) {
            if (hasCredentials(m)) {
                usable.add(m);
            }
        }
        usable.sort(CUSTOM_FIRST);
        return usable;
    }

    /**
     * Applies the {@code settings.enabledModels} whitelist to the full model
     * list, always keeping custom-provider models. No credential filtering or
     * sorting here — that is layered on by {@link #getAvailableModels()}.
     *
     * @param all the full model list from the registry
     * @return the whitelist-narrowed candidate list (unsorted)
     */
    private List<Model> narrowToEnabled(List<Model> all) {
        Settings settings = safeLoad();
        List<String> patterns = settings.enabledModels();
        if (patterns == null || patterns.isEmpty()) {
            return new ArrayList<>(all);
        }

        var picked = new ArrayList<Model>();
        for (String pattern : patterns) {
            if (pattern == null) {
                continue;
            }
            String p = pattern.trim().toLowerCase(Locale.ROOT);
            if (p.isEmpty()) {
                continue;
            }
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
        return picked;
    }

    /**
     * Returns {@code true} when the user has narrowed the visible model set —
     * useful when surfacing "showing X of Y" in a UI.
     *
     * @return the result
     */
    public boolean isFiltered() {
        Settings settings = safeLoad();
        return settings.enabledModels() != null && !settings.enabledModels().isEmpty();
    }

    /**
     * Glob/substring match against a model. Mirrors the legacy
     * {@code CampusClawCommand.matchesModelPattern} so {@code -m} cycling and
     * the WS catalogue agree.
     *
     * @param pattern the pattern
     * @param model the model
     * @return the result
     */
    public static boolean matchesPattern(String pattern, Model model) {
        String id = model.id().toLowerCase(Locale.ROOT);
        String name = model.name().toLowerCase(Locale.ROOT);
        String provider = model.provider().value().toLowerCase(Locale.ROOT);

        // Strip optional ":thinking" suffix that the CLI accepts.
        int colonIdx = pattern.indexOf(':');
        String p = colonIdx >= 0 ? pattern.substring(0, colonIdx) : pattern;

        // provider/id form
        if (p.contains("/")) {
            String[] parts = p.split("/", 2);
            if (!provider.contains(parts[0])) {
                return false;
            }
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
            if (ModelRegistry.modelsAreEqual(x, m)) {
                return true;
            }
        }
        return false;
    }
}
