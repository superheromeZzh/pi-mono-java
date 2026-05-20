/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.campusclaw.codingagent.resolver;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.campusclaw.ai.model.ModelRegistry;
import com.campusclaw.ai.types.Api;
import com.campusclaw.ai.types.Model;
import com.campusclaw.ai.types.Provider;
import com.campusclaw.codingagent.settings.Settings;
import com.campusclaw.codingagent.settings.SettingsManager;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AgentModelResolverTest {

    @Mock
    SettingsManager settingsManager;

    @Mock
    ModelRegistry modelRegistry;

    private static Model model(String id) {
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
                null);
    }

    private static Settings settingsWithAgent(Map<String, Settings.AgentConfig> agents) {
        return new Settings(
                null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null,
                null, null, null, null, null, null, null, null, null, null, agents);
    }

    @Nested
    class Resolution {

        @Test
        void noAgentMapReturnsDefault() {
            Model def = model("default");
            when(settingsManager.load()).thenReturn(Settings.empty());
            AgentModelResolver r = new AgentModelResolver(settingsManager, modelRegistry);
            assertThat(r.resolve("summarizer", def)).isSameAs(def);
        }

        @Test
        void missingAgentReturnsDefault() {
            Model def = model("default");
            when(settingsManager.load()).thenReturn(settingsWithAgent(Map.of("other", new Settings.AgentConfig("x"))));
            AgentModelResolver r = new AgentModelResolver(settingsManager, modelRegistry);
            assertThat(r.resolve("summarizer", def)).isSameAs(def);
        }

        @Test
        void blankModelReturnsDefault() {
            Model def = model("default");
            when(settingsManager.load())
                    .thenReturn(settingsWithAgent(Map.of("summarizer", new Settings.AgentConfig(""))));
            AgentModelResolver r = new AgentModelResolver(settingsManager, modelRegistry);
            assertThat(r.resolve("summarizer", def)).isSameAs(def);
        }

        @Test
        void overrideLookedUpByPlainId() {
            Model def = model("default");
            Model overrideModel = model("fast-model");
            when(settingsManager.load())
                    .thenReturn(settingsWithAgent(Map.of("summarizer", new Settings.AgentConfig("fast-model"))));
            when(modelRegistry.getProviders()).thenReturn(List.of(Provider.OPENAI));
            when(modelRegistry.getModel(Provider.OPENAI, "fast-model")).thenReturn(Optional.of(overrideModel));
            AgentModelResolver r = new AgentModelResolver(settingsManager, modelRegistry);
            assertThat(r.resolve("summarizer", def)).isSameAs(overrideModel);
        }

        @Test
        void overrideStripsProviderPrefix() {
            Model def = model("default");
            Model overrideModel = model("gpt-4");
            when(settingsManager.load())
                    .thenReturn(settingsWithAgent(Map.of("summarizer", new Settings.AgentConfig("openai/gpt-4"))));
            when(modelRegistry.getProviders()).thenReturn(List.of(Provider.OPENAI));
            when(modelRegistry.getModel(Provider.OPENAI, "gpt-4")).thenReturn(Optional.of(overrideModel));
            AgentModelResolver r = new AgentModelResolver(settingsManager, modelRegistry);
            assertThat(r.resolve("summarizer", def)).isSameAs(overrideModel);
        }

        @Test
        void overrideNotFoundFallsBackToDefault() {
            Model def = model("default");
            when(settingsManager.load())
                    .thenReturn(settingsWithAgent(Map.of("summarizer", new Settings.AgentConfig("missing"))));
            when(modelRegistry.getProviders()).thenReturn(List.of(Provider.OPENAI));
            when(modelRegistry.getModel(any(Provider.class), any(String.class))).thenReturn(Optional.empty());
            AgentModelResolver r = new AgentModelResolver(settingsManager, modelRegistry);
            assertThat(r.resolve("summarizer", def)).isSameAs(def);
        }

        @Test
        void settingsExceptionFallsBackToDefault() {
            Model def = model("default");
            when(settingsManager.load()).thenThrow(new RuntimeException("io"));
            AgentModelResolver r = new AgentModelResolver(settingsManager, modelRegistry);
            assertThat(r.resolve("summarizer", def)).isSameAs(def);
        }
    }
}
