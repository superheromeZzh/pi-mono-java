/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.hicampus.mate.matecampusclaw.codingagent.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.huawei.hicampus.mate.matecampusclaw.ai.env.EnvApiKeyResolver;
import com.huawei.hicampus.mate.matecampusclaw.ai.env.ResolvedProviderConfig;
import com.huawei.hicampus.mate.matecampusclaw.ai.types.Api;
import com.huawei.hicampus.mate.matecampusclaw.ai.types.Model;
import com.huawei.hicampus.mate.matecampusclaw.ai.types.Provider;
import com.huawei.hicampus.mate.matecampusclaw.codingagent.auth.AuthStore;
import com.huawei.hicampus.mate.matecampusclaw.codingagent.settings.Settings;
import com.huawei.hicampus.mate.matecampusclaw.codingagent.settings.SettingsManager;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class SettingsBackedProviderConfigResolverTest {

    @Mock
    SettingsManager settingsManager;

    @Mock
    EnvApiKeyResolver envApiKeyResolver;

    @Mock
    AuthStore authStore;

    private static Settings settingsWithProvider(Map<String, Settings.ProviderConfig> provider) {
        return new Settings(
                null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null,
                null, null, null, null, null, null, null, null, null, provider, null);
    }

    private static Model model(String apiKey) {
        return new Model(
                "m",
                "M",
                Api.ANTHROPIC_MESSAGES,
                Provider.ANTHROPIC,
                null,
                false,
                List.of(),
                null,
                0,
                0,
                null,
                null,
                apiKey);
    }

    @Nested
    class Resolution {

        @Test
        void authStoreKeyWinsOverEverything() {
            when(settingsManager.load()).thenReturn(Settings.empty());
            when(authStore.getApiKey(Provider.ANTHROPIC)).thenReturn(Optional.of("from-auth"));
            SettingsBackedProviderConfigResolver r =
                    new SettingsBackedProviderConfigResolver(settingsManager, envApiKeyResolver, authStore);
            ResolvedProviderConfig cfg = r.resolve(Provider.ANTHROPIC, model("from-model"));
            assertThat(cfg.apiKey()).isEqualTo("from-auth");
        }

        @Test
        void providerSettingsApiKeyUsedWhenAuthEmpty() {
            when(authStore.getApiKey(any(Provider.class))).thenReturn(Optional.empty());
            Settings.ProviderConfig pc = new Settings.ProviderConfig("from-settings", null, null, null);
            when(settingsManager.load()).thenReturn(settingsWithProvider(Map.of("anthropic", pc)));
            SettingsBackedProviderConfigResolver r =
                    new SettingsBackedProviderConfigResolver(settingsManager, envApiKeyResolver, authStore);
            ResolvedProviderConfig cfg = r.resolve(Provider.ANTHROPIC, null);
            assertThat(cfg.apiKey()).isEqualTo("from-settings");
        }

        @Test
        void modelApiKeyUsedWhenSettingsAndAuthEmpty() {
            when(authStore.getApiKey(any(Provider.class))).thenReturn(Optional.empty());
            when(settingsManager.load()).thenReturn(Settings.empty());
            SettingsBackedProviderConfigResolver r =
                    new SettingsBackedProviderConfigResolver(settingsManager, envApiKeyResolver, authStore);
            ResolvedProviderConfig cfg = r.resolve(Provider.ANTHROPIC, model("from-model"));
            assertThat(cfg.apiKey()).isEqualTo("from-model");
        }

        @Test
        void envFallback() {
            when(authStore.getApiKey(any(Provider.class))).thenReturn(Optional.empty());
            when(settingsManager.load()).thenReturn(Settings.empty());
            when(envApiKeyResolver.resolve(Provider.ANTHROPIC)).thenReturn(Optional.of("from-env"));
            SettingsBackedProviderConfigResolver r =
                    new SettingsBackedProviderConfigResolver(settingsManager, envApiKeyResolver, authStore);
            ResolvedProviderConfig cfg = r.resolve(Provider.ANTHROPIC, null);
            assertThat(cfg.apiKey()).isEqualTo("from-env");
        }

        @Test
        void allMissingReturnsNullKey() {
            when(authStore.getApiKey(any(Provider.class))).thenReturn(Optional.empty());
            when(settingsManager.load()).thenReturn(Settings.empty());
            when(envApiKeyResolver.resolve(any(Provider.class))).thenReturn(Optional.empty());
            SettingsBackedProviderConfigResolver r =
                    new SettingsBackedProviderConfigResolver(settingsManager, envApiKeyResolver, authStore);
            ResolvedProviderConfig cfg = r.resolve(Provider.ANTHROPIC, null);
            assertThat(cfg.apiKey()).isNull();
        }

        @Test
        void baseUrlFromSettings() {
            when(authStore.getApiKey(any(Provider.class))).thenReturn(Optional.empty());
            Settings.ProviderConfig pc =
                    new Settings.ProviderConfig("k", "https://override.example", null, Map.of("X-Foo", "1"));
            when(settingsManager.load()).thenReturn(settingsWithProvider(Map.of("anthropic", pc)));
            SettingsBackedProviderConfigResolver r =
                    new SettingsBackedProviderConfigResolver(settingsManager, envApiKeyResolver, authStore);
            ResolvedProviderConfig cfg = r.resolve(Provider.ANTHROPIC, null);
            assertThat(cfg.baseUrl()).isEqualTo("https://override.example");
            assertThat(cfg.headers()).containsEntry("X-Foo", "1");
        }
    }

    @Nested
    class ProviderLookup {

        @Test
        void caseInsensitiveKeyMatch() {
            when(authStore.getApiKey(any(Provider.class))).thenReturn(Optional.empty());
            Settings.ProviderConfig pc = new Settings.ProviderConfig("k", null, null, null);
            when(settingsManager.load()).thenReturn(settingsWithProvider(Map.of("Anthropic", pc)));
            SettingsBackedProviderConfigResolver r =
                    new SettingsBackedProviderConfigResolver(settingsManager, envApiKeyResolver, authStore);
            assertThat(r.resolve(Provider.ANTHROPIC, null).apiKey()).isEqualTo("k");
        }

        @Test
        void underscoreDashTolerant() {
            when(authStore.getApiKey(any(Provider.class))).thenReturn(Optional.empty());
            Settings.ProviderConfig pc = new Settings.ProviderConfig("k", null, null, null);
            when(settingsManager.load()).thenReturn(settingsWithProvider(Map.of("amazon_bedrock", pc)));
            SettingsBackedProviderConfigResolver r =
                    new SettingsBackedProviderConfigResolver(settingsManager, envApiKeyResolver, authStore);
            assertThat(r.resolve(Provider.AMAZON_BEDROCK, null).apiKey()).isEqualTo("k");
        }
    }

    @Nested
    class FaultyLoad {

        @Test
        void settingsExceptionTreatedAsEmpty() {
            when(settingsManager.load()).thenThrow(new RuntimeException("io"));
            when(authStore.getApiKey(any(Provider.class))).thenReturn(Optional.of("via-auth"));
            SettingsBackedProviderConfigResolver r =
                    new SettingsBackedProviderConfigResolver(settingsManager, envApiKeyResolver, authStore);
            assertThat(r.resolve(Provider.ANTHROPIC, null).apiKey()).isEqualTo("via-auth");
        }
    }
}
