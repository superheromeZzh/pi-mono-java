/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.hicampus.mate.matecampusclaw.codingagent.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.util.List;

import com.huawei.hicampus.mate.matecampusclaw.ai.env.ProviderConfigResolver;
import com.huawei.hicampus.mate.matecampusclaw.ai.env.ResolvedProviderConfig;
import com.huawei.hicampus.mate.matecampusclaw.ai.model.ModelRegistry;
import com.huawei.hicampus.mate.matecampusclaw.ai.types.Api;
import com.huawei.hicampus.mate.matecampusclaw.ai.types.Model;
import com.huawei.hicampus.mate.matecampusclaw.ai.types.Provider;
import com.huawei.hicampus.mate.matecampusclaw.codingagent.settings.Settings;
import com.huawei.hicampus.mate.matecampusclaw.codingagent.settings.SettingsManager;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

/**
 * Unit tests for {@link ModelCatalogService}. Exercises the filtering logic
 * (enabledModels glob/substring), custom-provider passthrough, credential
 * resolution fallbacks, and the static {@code matchesPattern} helper that
 * powers both the model selector overlay and the {@code -m} flag cycling.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ModelCatalogServiceTest {

    @Mock
    ModelRegistry modelRegistry;

    @Mock
    SettingsManager settingsManager;

    @Mock
    ProviderConfigResolver providerConfigResolver;

    private static Model model(String id, String name, Provider provider) {
        return new Model(
                id, name, Api.ANTHROPIC_MESSAGES, provider, null, false, List.of(), null, 0, 0, null, null, null);
    }

    private static Model modelWithKey(String id, String key) {
        return new Model(
                id,
                id,
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
                key);
    }

    private static Settings settingsWithEnabled(List<String> patterns) {
        return new Settings(
                null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null,
                null, null, patterns, null, null, null, null, null, null, null, null);
    }

    @Nested
    class HasCredentials {

        @Test
        void nullModelFalse() {
            ModelCatalogService svc = new ModelCatalogService(modelRegistry, settingsManager);
            assertThat(svc.hasCredentials(null)).isFalse();
        }

        @Test
        void customProviderAlwaysTrue() {
            ModelCatalogService svc = new ModelCatalogService(modelRegistry, settingsManager, providerConfigResolver);
            assertThat(svc.hasCredentials(model("m", "M", Provider.CUSTOM))).isTrue();
        }

        @Test
        void modelEmbeddedKeyCounts() {
            ModelCatalogService svc = new ModelCatalogService(modelRegistry, settingsManager, providerConfigResolver);
            assertThat(svc.hasCredentials(modelWithKey("m", "sk-x"))).isTrue();
        }

        @Test
        void noResolverReturnsTrue() {
            ModelCatalogService svc = new ModelCatalogService(modelRegistry, settingsManager);
            assertThat(svc.hasCredentials(model("m", "M", Provider.ANTHROPIC))).isTrue();
        }

        @Test
        void resolverWithKeyTrue() {
            when(providerConfigResolver.resolve(any(Provider.class), any(Model.class)))
                    .thenReturn(new ResolvedProviderConfig("resolved-key", null, null));
            ModelCatalogService svc = new ModelCatalogService(modelRegistry, settingsManager, providerConfigResolver);
            assertThat(svc.hasCredentials(model("m", "M", Provider.ANTHROPIC))).isTrue();
        }

        @Test
        void resolverWithoutKeyFalse() {
            when(providerConfigResolver.resolve(any(Provider.class), any(Model.class)))
                    .thenReturn(new ResolvedProviderConfig(null, null, null));
            ModelCatalogService svc = new ModelCatalogService(modelRegistry, settingsManager, providerConfigResolver);
            assertThat(svc.hasCredentials(model("m", "M", Provider.ANTHROPIC))).isFalse();
        }

        @Test
        void resolverExceptionFalse() {
            when(providerConfigResolver.resolve(any(Provider.class), any(Model.class)))
                    .thenThrow(new RuntimeException("boom"));
            ModelCatalogService svc = new ModelCatalogService(modelRegistry, settingsManager, providerConfigResolver);
            assertThat(svc.hasCredentials(model("m", "M", Provider.ANTHROPIC))).isFalse();
        }
    }

    @Nested
    class GetAllModels {

        @Test
        void sortedByProviderAndId() {
            when(modelRegistry.getAllModels())
                    .thenReturn(List.of(
                            model("zeta", "z", Provider.OPENAI),
                            model("alpha", "a", Provider.ANTHROPIC),
                            model("beta", "b", Provider.OPENAI)));
            ModelCatalogService svc = new ModelCatalogService(modelRegistry, settingsManager);
            List<Model> sorted = svc.getAllModels();
            assertThat(sorted).extracting(Model::id).containsExactly("alpha", "beta", "zeta");
        }
    }

    @Nested
    class GetAvailableModels {

        @Test
        void emptyEnabledReturnsAll() {
            when(settingsManager.load()).thenReturn(Settings.empty());
            when(modelRegistry.getAllModels())
                    .thenReturn(List.of(model("a", "a", Provider.ANTHROPIC), model("b", "b", Provider.ANTHROPIC)));
            ModelCatalogService svc = new ModelCatalogService(modelRegistry, settingsManager);
            assertThat(svc.getAvailableModels()).hasSize(2);
        }

        @Test
        void enabledPatternFiltersBySubstring() {
            when(settingsManager.load()).thenReturn(settingsWithEnabled(List.of("alpha")));
            when(modelRegistry.getAllModels())
                    .thenReturn(List.of(
                            model("alpha-1", "a1", Provider.ANTHROPIC),
                            model("alpha-2", "a2", Provider.ANTHROPIC),
                            model("beta", "b", Provider.ANTHROPIC)));
            ModelCatalogService svc = new ModelCatalogService(modelRegistry, settingsManager);
            assertThat(svc.getAvailableModels()).extracting(Model::id).containsExactlyInAnyOrder("alpha-1", "alpha-2");
        }

        @Test
        void customModelsAlwaysIncluded() {
            when(settingsManager.load()).thenReturn(settingsWithEnabled(List.of("alpha")));
            when(modelRegistry.getAllModels())
                    .thenReturn(List.of(
                            model("alpha-1", "a", Provider.ANTHROPIC), model("my-custom", "c", Provider.CUSTOM)));
            ModelCatalogService svc = new ModelCatalogService(modelRegistry, settingsManager);
            assertThat(svc.getAvailableModels())
                    .extracting(Model::id)
                    .containsExactlyInAnyOrder("alpha-1", "my-custom");
        }

        @Test
        void blankPatternSkipped() {
            when(settingsManager.load()).thenReturn(settingsWithEnabled(java.util.Arrays.asList("", "   ", null, "a")));
            when(modelRegistry.getAllModels()).thenReturn(List.of(model("alpha", "a", Provider.ANTHROPIC)));
            ModelCatalogService svc = new ModelCatalogService(modelRegistry, settingsManager);
            assertThat(svc.getAvailableModels()).hasSize(1);
        }

        @Test
        void settingsLoadExceptionTreatedAsEmpty() {
            when(settingsManager.load()).thenThrow(new RuntimeException("io"));
            when(modelRegistry.getAllModels()).thenReturn(List.of(model("m", "m", Provider.ANTHROPIC)));
            ModelCatalogService svc = new ModelCatalogService(modelRegistry, settingsManager);
            assertThat(svc.getAvailableModels()).hasSize(1);
        }
    }

    @Nested
    class IsFiltered {

        @Test
        void emptyEnabledNotFiltered() {
            when(settingsManager.load()).thenReturn(Settings.empty());
            ModelCatalogService svc = new ModelCatalogService(modelRegistry, settingsManager);
            assertThat(svc.isFiltered()).isFalse();
        }

        @Test
        void nonEmptyEnabledFiltered() {
            when(settingsManager.load()).thenReturn(settingsWithEnabled(List.of("a")));
            ModelCatalogService svc = new ModelCatalogService(modelRegistry, settingsManager);
            assertThat(svc.isFiltered()).isTrue();
        }
    }

    @Nested
    class MatchesPattern {

        @Test
        void substringMatchesIdAndName() {
            assertThat(ModelCatalogService.matchesPattern("gpt", model("gpt-4", "GPT-4", Provider.OPENAI)))
                    .isTrue();
            assertThat(ModelCatalogService.matchesPattern(
                            "flash", model("gemini-flash", "Gemini Flash", Provider.GOOGLE)))
                    .isTrue();
            assertThat(ModelCatalogService.matchesPattern("xyz", model("gpt-4", "GPT-4", Provider.OPENAI)))
                    .isFalse();
        }

        @Test
        void wildcardMatchesUsingRegex() {
            assertThat(ModelCatalogService.matchesPattern("claude-*", model("claude-3", "C3", Provider.ANTHROPIC)))
                    .isTrue();
            assertThat(ModelCatalogService.matchesPattern("claude-*", model("gpt-4", "GPT-4", Provider.OPENAI)))
                    .isFalse();
        }

        @Test
        void providerPrefixFilters() {
            assertThat(ModelCatalogService.matchesPattern("openai/gpt", model("gpt-4", "GPT-4", Provider.OPENAI)))
                    .isTrue();
            assertThat(ModelCatalogService.matchesPattern("openai/gpt", model("gpt-4", "GPT-4", Provider.ANTHROPIC)))
                    .isFalse();
        }

        @Test
        void thinkingSuffixStripped() {
            assertThat(ModelCatalogService.matchesPattern("gpt:thinking", model("gpt-4", "GPT-4", Provider.OPENAI)))
                    .isTrue();
        }
    }
}
