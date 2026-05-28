/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.hicampus.mate.matecampusclaw.codingagent.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.List;

import com.huawei.hicampus.mate.matecampusclaw.ai.model.ModelRegistry;
import com.huawei.hicampus.mate.matecampusclaw.ai.types.Model;
import com.huawei.hicampus.mate.matecampusclaw.ai.types.Provider;
import com.huawei.hicampus.mate.matecampusclaw.codingagent.settings.Settings;
import com.huawei.hicampus.mate.matecampusclaw.codingagent.settings.SettingsManager;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CustomModelLoaderTest {

    @Mock
    SettingsManager settingsManager;

    @Mock
    ModelRegistry modelRegistry;

    @InjectMocks
    CustomModelLoader loader;

    private static Settings settingsWith(List<Settings.CustomModelConfig> customs) {
        // 28 fields total; customModels is at index 17 (zero-based 16).
        return new Settings(
                null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, customs,
                null, null, null, null, null, null, null, null, null, null, null);
    }

    @Nested
    class RegisterCustomModels {

        @Test
        void noCustomsUnregistersWithoutAdding() {
            when(settingsManager.load()).thenReturn(Settings.empty());
            loader.registerCustomModels();
            verify(modelRegistry, times(1)).unregisterByProvider(Provider.CUSTOM);
            verify(modelRegistry, never()).registerAll(org.mockito.ArgumentMatchers.anyList());
        }

        @Test
        void exceptionFromSettingsLoadSilent() {
            when(settingsManager.load()).thenThrow(new RuntimeException("nope"));
            loader.registerCustomModels();
            verifyNoInteractions(modelRegistry);
        }

        @Test
        void validCustomRegistered() {
            Settings.CustomModelConfig cfg = new Settings.CustomModelConfig(
                    "my-model",
                    "MyModel",
                    "openai-completions",
                    "https://example.com",
                    "sk-x",
                    null,
                    null,
                    null,
                    null,
                    null);
            when(settingsManager.load()).thenReturn(settingsWith(List.of(cfg)));
            loader.registerCustomModels();
            verify(modelRegistry, times(1)).unregisterByProvider(Provider.CUSTOM);
            @SuppressWarnings("unchecked")
            ArgumentCaptor<List<Model>> captor = ArgumentCaptor.forClass(List.class);
            verify(modelRegistry, times(1)).registerAll(captor.capture());
            assertThat(captor.getValue()).extracting(Model::id).containsExactly("my-model");
        }

        @Test
        void invalidApiSkipped() {
            Settings.CustomModelConfig cfg = new Settings.CustomModelConfig(
                    "bad", null, "not-a-real-api", "url", "key", null, null, null, null, null);
            when(settingsManager.load()).thenReturn(settingsWith(List.of(cfg)));
            loader.registerCustomModels();

            verify(modelRegistry, times(1)).unregisterByProvider(Provider.CUSTOM);
            verify(modelRegistry, never()).registerAll(org.mockito.ArgumentMatchers.anyList());
        }

        @Test
        void multipleModalitiesParsed() {
            Settings.CustomModelConfig cfg = new Settings.CustomModelConfig(
                    "vis-model",
                    "Vision",
                    "openai-completions",
                    "https://example.com",
                    "sk-x",
                    32000,
                    4096,
                    true,
                    List.of("TEXT", "IMAGE", "UNKNOWN_THING"),
                    "anthropic");
            when(settingsManager.load()).thenReturn(settingsWith(List.of(cfg)));
            loader.registerCustomModels();
            @SuppressWarnings("unchecked")
            ArgumentCaptor<List<Model>> captor = ArgumentCaptor.forClass(List.class);
            verify(modelRegistry).registerAll(captor.capture());
            Model m = captor.getValue().get(0);
            assertThat(m.contextWindow()).isEqualTo(32000);
            assertThat(m.maxTokens()).isEqualTo(4096);
            assertThat(m.reasoning()).isTrue();
            assertThat(m.thinkingFormat()).isEqualTo("anthropic");
            assertThat(m.inputModalities()).hasSize(2); // TEXT + IMAGE, UNKNOWN_THING dropped
        }
    }

    @Nested
    class Refresh {

        @Test
        void refreshClearsStaleAndRegistersNew() {
            Settings.CustomModelConfig first = new Settings.CustomModelConfig(
                    "stale", null, "openai-completions", "https://example.com", "k", null, null, null, null, null);
            when(settingsManager.load()).thenReturn(settingsWith(List.of(first)));
            loader.refresh();

            Settings.CustomModelConfig fresh = new Settings.CustomModelConfig(
                    "fresh", null, "openai-completions", "https://example.com", "k", null, null, null, null, null);
            when(settingsManager.load()).thenReturn(settingsWith(List.of(fresh)));
            loader.refresh();

            verify(modelRegistry, times(2)).unregisterByProvider(Provider.CUSTOM);
            @SuppressWarnings("unchecked")
            ArgumentCaptor<List<Model>> captor = ArgumentCaptor.forClass(List.class);
            verify(modelRegistry, times(2)).registerAll(captor.capture());
            assertThat(captor.getAllValues()).hasSize(2);
            assertThat(captor.getAllValues().get(0)).extracting(Model::id).containsExactly("stale");
            assertThat(captor.getAllValues().get(1)).extracting(Model::id).containsExactly("fresh");
        }

        @Test
        void refreshWithEmptyArrayClearsRegistry() {
            Settings.CustomModelConfig seed = new Settings.CustomModelConfig(
                    "seed", null, "openai-completions", "https://example.com", "k", null, null, null, null, null);
            when(settingsManager.load()).thenReturn(settingsWith(List.of(seed)));
            loader.refresh();

            when(settingsManager.load()).thenReturn(settingsWith(List.of()));
            loader.refresh();

            verify(modelRegistry, times(2)).unregisterByProvider(Provider.CUSTOM);

            // Only the first refresh registers anything (the seed); the empty refresh does not.
            verify(modelRegistry, times(1)).registerAll(org.mockito.ArgumentMatchers.anyList());
        }
    }
}
