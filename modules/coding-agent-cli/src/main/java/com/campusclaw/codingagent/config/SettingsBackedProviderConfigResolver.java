package com.campusclaw.codingagent.config;

import java.util.Map;

import com.campusclaw.ai.env.EnvApiKeyResolver;
import com.campusclaw.ai.env.ProviderConfigResolver;
import com.campusclaw.ai.env.ResolvedProviderConfig;
import com.campusclaw.ai.types.Model;
import com.campusclaw.ai.types.Provider;
import com.campusclaw.codingagent.auth.AuthStore;
import com.campusclaw.codingagent.settings.Settings;
import com.campusclaw.codingagent.settings.SettingsManager;

import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;

/**
 * Provider config resolver layered like opencode:
 *
 * <ol>
 *   <li>{@code ~/.campusclaw/agent/auth.json} (written by {@code /auth login})</li>
 *   <li>{@code settings.json#provider.<id>.apiKey / .baseURL}</li>
 *   <li>Model-embedded {@code apiKey} (for hand-edited custom models)</li>
 *   <li>Provider-specific environment variables (legacy)</li>
 * </ol>
 *
 * <p>Both API keys and base URLs go through {@link ConfigValueResolver} so
 * users can write {@code "apiKey": "${ZAI_API_KEY}"} and indirect through env.
 *
 * <p>Marked {@code @Primary} so it shadows {@link com.campusclaw.ai.env.EnvProviderConfigResolver}
 * when the CLI module is on the classpath; without the CLI, the env-only
 * resolver in {@code modules/ai} is used.
 */
@Service
@Primary
public class SettingsBackedProviderConfigResolver implements ProviderConfigResolver {

    private final SettingsManager settingsManager;
    private final EnvApiKeyResolver envApiKeyResolver;
    private final AuthStore authStore;

    public SettingsBackedProviderConfigResolver(
            SettingsManager settingsManager,
            EnvApiKeyResolver envApiKeyResolver,
            AuthStore authStore) {
        this.settingsManager = settingsManager;
        this.envApiKeyResolver = envApiKeyResolver;
        this.authStore = authStore;
    }

    @Override
    public ResolvedProviderConfig resolve(Provider provider, Model model) {
        Settings settings = safeLoad();
        Settings.ProviderConfig providerSettings = lookupProvider(settings, provider);

        String apiKey = resolveApiKey(provider, model, providerSettings);
        String baseUrl = resolveBaseUrl(providerSettings);
        Map<String, String> headers = providerSettings != null ? providerSettings.headers() : null;
        return new ResolvedProviderConfig(apiKey, baseUrl, headers);
    }

    private Settings safeLoad() {
        try {
            return settingsManager.load();
        } catch (Exception e) {
            return Settings.empty();
        }
    }

    private static Settings.ProviderConfig lookupProvider(Settings settings, Provider provider) {
        if (settings.provider() == null) { return null; }
        Settings.ProviderConfig direct = settings.provider().get(provider.value());
        if (direct != null) { return direct; }
        // Allow case-insensitive / underscore-insensitive matching for the JSON key.
        for (var e : settings.provider().entrySet()) {
            String key = e.getKey();
            if (key == null) { continue; }
            if (key.equalsIgnoreCase(provider.value())
                    || key.replace('_', '-').equalsIgnoreCase(provider.value())) {
                return e.getValue();
            }
        }
        return null;
    }

    private String resolveApiKey(Provider provider, Model model, Settings.ProviderConfig providerSettings) {
        // 1. auth.json (written by /auth login)
        String authKey = authStore.getApiKey(provider).orElse(null);
        if (authKey != null && !authKey.isBlank()) { return authKey; }

        // 2. settings.json#provider.<id>.apiKey (with ${ENV} expansion)
        if (providerSettings != null && providerSettings.apiKey() != null) {
            String resolved = ConfigValueResolver.resolve(providerSettings.apiKey());
            if (resolved != null && !resolved.isBlank()) { return resolved; }
        }

        // 3. Model-embedded
        if (model != null && model.apiKey() != null && !model.apiKey().isBlank()) {
            return model.apiKey();
        }

        // 4. Env (provider-specific then legacy fallback chain)
        return envApiKeyResolver.resolve(provider).orElse(null);
    }

    private static String resolveBaseUrl(Settings.ProviderConfig providerSettings) {
        if (providerSettings == null) { return null; }
        String url = providerSettings.effectiveBaseUrl();
        if (url == null || url.isBlank()) { return null; }
        return ConfigValueResolver.resolve(url);
    }
}
