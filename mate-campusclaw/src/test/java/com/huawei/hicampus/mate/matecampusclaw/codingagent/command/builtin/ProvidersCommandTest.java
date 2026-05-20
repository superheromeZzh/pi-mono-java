/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.hicampus.mate.matecampusclaw.codingagent.command.builtin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.huawei.hicampus.mate.matecampusclaw.ai.model.ModelRegistry;
import com.huawei.hicampus.mate.matecampusclaw.ai.types.Api;
import com.huawei.hicampus.mate.matecampusclaw.ai.types.Model;
import com.huawei.hicampus.mate.matecampusclaw.ai.types.Provider;
import com.huawei.hicampus.mate.matecampusclaw.codingagent.auth.AuthStore;
import com.huawei.hicampus.mate.matecampusclaw.codingagent.command.SlashCommandContext;
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
class ProvidersCommandTest {

    @Mock
    ModelRegistry modelRegistry;

    @Mock
    SettingsManager settingsManager;

    @Mock
    AuthStore authStore;

    private final List<String> out = new ArrayList<>();
    private final SlashCommandContext ctx = new SlashCommandContext(null, out::add);

    private static Model model() {
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
                null);
    }

    private static Settings settingsWithProvider(Map<String, Settings.ProviderConfig> provider) {
        return new Settings(
                null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null,
                null, null, null, null, null, null, null, null, null, provider, null);
    }

    @Nested
    class Metadata {

        @Test
        void identity() {
            ProvidersCommand c = new ProvidersCommand(modelRegistry, settingsManager, authStore);
            assertThat(c.name()).isEqualTo("providers");
            assertThat(c.description()).isNotBlank();
        }
    }

    @Nested
    class Output {

        @Test
        void rendersHeaderAlways() {
            when(settingsManager.load()).thenReturn(Settings.empty());
            when(modelRegistry.getProviders()).thenReturn(List.of());
            when(authStore.listSummary()).thenReturn(Map.of());
            new ProvidersCommand(modelRegistry, settingsManager, authStore).execute(ctx, null);
            assertThat(out)
                    .first()
                    .asString()
                    .contains("PROVIDER")
                    .contains("MODELS")
                    .contains("AUTH.JSON");
        }

        @Test
        void listsProviderWithModelsCountAndAuth() {
            when(settingsManager.load()).thenReturn(Settings.empty());
            when(modelRegistry.getProviders()).thenReturn(List.of(Provider.ANTHROPIC));
            when(modelRegistry.getModels(Provider.ANTHROPIC)).thenReturn(List.of(model(), model()));
            when(authStore.listSummary()).thenReturn(Map.of("anthropic", "api"));
            new ProvidersCommand(modelRegistry, settingsManager, authStore).execute(ctx, null);
            String joined = String.join("\n", out);
            assertThat(joined).contains("anthropic").contains("2").contains("yes");
        }

        @Test
        void describesProviderOverrides() {
            when(modelRegistry.getProviders()).thenReturn(List.of());
            when(authStore.listSummary()).thenReturn(Map.of());
            Settings.ProviderConfig cfg =
                    new Settings.ProviderConfig("k", "https://override.example", null, Map.of("X-Foo", "1"));
            when(settingsManager.load()).thenReturn(settingsWithProvider(Map.of("anthropic", cfg)));
            new ProvidersCommand(modelRegistry, settingsManager, authStore).execute(ctx, null);
            String joined = String.join("\n", out);
            assertThat(joined).contains("apiKey").contains("baseURL").contains("headers");
        }

        @Test
        void unknownSettingsProviderListed() {
            when(modelRegistry.getProviders()).thenReturn(List.of());
            when(authStore.listSummary()).thenReturn(Map.of());
            Settings.ProviderConfig cfg = new Settings.ProviderConfig("k", null, null, null);
            when(settingsManager.load()).thenReturn(settingsWithProvider(Map.of("mystery-id", cfg)));
            new ProvidersCommand(modelRegistry, settingsManager, authStore).execute(ctx, null);
            String joined = String.join("\n", out);
            assertThat(joined).contains("mystery-id").contains("not matching any");
        }

        @Test
        void settingsExceptionTreatedAsEmpty() {
            when(settingsManager.load()).thenThrow(new RuntimeException("oops"));
            when(modelRegistry.getProviders()).thenReturn(List.of());
            when(authStore.listSummary()).thenReturn(Map.of());
            new ProvidersCommand(modelRegistry, settingsManager, authStore).execute(ctx, null);
            assertThat(out).isNotEmpty();
        }
    }
}
