package com.campusclaw.codingagent.command.builtin;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.campusclaw.ai.model.ModelRegistry;
import com.campusclaw.ai.types.Provider;
import com.campusclaw.codingagent.auth.AuthStore;
import com.campusclaw.codingagent.command.SlashCommand;
import com.campusclaw.codingagent.command.SlashCommandContext;
import com.campusclaw.codingagent.settings.Settings;
import com.campusclaw.codingagent.settings.SettingsManager;

/**
 * Lists every provider known to the registry alongside the current resolution
 * status: how many models are registered, whether {@code auth.json} has a
 * key, whether {@code settings.json#provider.<id>} overrides apply.
 *
 * <p>The opencode equivalent is roughly {@code opencode providers}.
 */
public class ProvidersCommand implements SlashCommand {

    private final ModelRegistry modelRegistry;
    private final SettingsManager settingsManager;
    private final AuthStore authStore;

    public ProvidersCommand(ModelRegistry modelRegistry, SettingsManager settingsManager,
                            AuthStore authStore) {
        this.modelRegistry = modelRegistry;
        this.settingsManager = settingsManager;
        this.authStore = authStore;
    }

    @Override
    public String name() { return "providers"; }

    @Override
    public String description() { return "List known providers and their auth/baseURL state"; }

    @Override
    public void execute(SlashCommandContext context, String arguments) {
        Settings settings;
        try {
            settings = settingsManager.load();
        } catch (Exception e) {
            settings = Settings.empty();
        }
        Map<String, Settings.ProviderConfig> providerCfg = settings.provider() != null
                ? settings.provider() : Map.of();
        Map<String, String> auth = authStore.listSummary();

        Map<Provider, Integer> modelCounts = new LinkedHashMap<>();
        for (Provider p : modelRegistry.getProviders()) {
            modelCounts.put(p, modelRegistry.getModels(p).size());
        }

        var rows = new ArrayList<Provider>();
        for (Provider p : Provider.values()) {
            if (modelCounts.getOrDefault(p, 0) > 0
                    || providerCfg.containsKey(p.value())
                    || auth.containsKey(p.value())) {
                rows.add(p);
            }
        }
        rows.sort(Comparator.comparing(Provider::value));

        context.output().println(String.format("%-20s %-7s %-10s %-7s",
                "PROVIDER", "MODELS", "AUTH.JSON", "OVERRIDE"));
        for (Provider p : rows) {
            int count = modelCounts.getOrDefault(p, 0);
            boolean hasAuth = auth.containsKey(p.value());
            Settings.ProviderConfig cfg = providerCfg.get(p.value());
            String override = describeOverride(cfg);
            context.output().println(String.format("%-20s %-7d %-10s %-7s",
                    p.value(), count, hasAuth ? "yes" : "—", override));
        }

        // Highlight settings.provider entries that don't map to any known Provider.
        List<String> unknown = new ArrayList<>();
        for (String key : providerCfg.keySet()) {
            if (Provider.tryFromValue(key).isEmpty()) { unknown.add(key); }
        }
        if (!unknown.isEmpty()) {
            context.output().println("");
            context.output().println("settings.provider entries not matching any built-in provider:");
            for (String k : unknown) { context.output().println("  " + k); }
            context.output().println("(They are ignored today; see Phase 5 follow-up to register custom providers.)");
        }
    }

    private static String describeOverride(Settings.ProviderConfig cfg) {
        if (cfg == null) { return "—"; }
        var bits = new ArrayList<String>();
        if (cfg.apiKey() != null && !cfg.apiKey().isBlank()) { bits.add("apiKey"); }
        if (cfg.effectiveBaseUrl() != null) { bits.add("baseURL"); }
        if (cfg.headers() != null && !cfg.headers().isEmpty()) { bits.add("headers"); }
        return bits.isEmpty() ? "—" : String.join("+", bits);
    }
}
