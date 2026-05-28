/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.hicampus.mate.matecampusclaw.codingagent.mode.server;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;

import com.huawei.hicampus.mate.matecampusclaw.ai.model.ModelRegistry;
import com.huawei.hicampus.mate.matecampusclaw.ai.types.Api;
import com.huawei.hicampus.mate.matecampusclaw.ai.types.InputModality;
import com.huawei.hicampus.mate.matecampusclaw.ai.types.Model;
import com.huawei.hicampus.mate.matecampusclaw.ai.types.ModelCost;
import com.huawei.hicampus.mate.matecampusclaw.ai.types.Provider;
import com.huawei.hicampus.mate.matecampusclaw.codingagent.config.CustomModelLoader;
import com.huawei.hicampus.mate.matecampusclaw.codingagent.model.ModelCatalogService;
import com.huawei.hicampus.mate.matecampusclaw.codingagent.settings.Settings;
import com.huawei.hicampus.mate.matecampusclaw.codingagent.settings.SettingsManager;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

/**
 * Behavioural tests for {@link SettingsHandler}. Drives the package-private
 * apply/build methods directly to avoid the heft of WebFlux mock plumbing —
 * the {@code getModels} / {@code setDefaultModel} / {@code setCustomModels}
 * entry points are thin adapters that delegate to those methods, so covering
 * them keeps end-to-end behaviour honest.
 *
 * @version [br_eCampusCore 25.1.0_Next, 2026/05/22]
 * @since [br_eCampusCore 25.1.0_Next]
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class SettingsHandlerTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Mock
    SettingsManager settingsManager;

    @Mock
    ModelRegistry modelRegistry;

    @Mock
    ModelCatalogService modelCatalog;

    @Mock
    CustomModelLoader customModelLoader;

    SettingsHandler handler;

    @BeforeEach
    void setUp() {
        handler = new SettingsHandler(settingsManager, modelRegistry, modelCatalog, customModelLoader);
    }

    private static Model anthropicModel(String id) {
        return new Model(
                id,
                id,
                Api.ANTHROPIC_MESSAGES,
                Provider.ANTHROPIC,
                "https://api.anthropic.com",
                false,
                List.of(InputModality.TEXT),
                new ModelCost(1.0, 2.0, 0.5, 0.75),
                200000,
                8192,
                null,
                null,
                null);
    }

    private static Settings settingsWithDefault(String defaultModel, List<Settings.CustomModelConfig> customs) {
        return new Settings(
                null,
                defaultModel,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                customs,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null);
    }

    @Nested
    class BuildModelsSnapshot {

        @Test
        void includesDefaultCustomsAndAvailable() {
            var model = anthropicModel("claude-haiku-3-5");
            when(settingsManager.load()).thenReturn(settingsWithDefault("claude-haiku-3-5", List.of()));
            when(modelCatalog.getAvailableModels()).thenReturn(List.of(model));
            when(modelCatalog.hasCredentials(model)).thenReturn(true);
            when(modelCatalog.isFiltered()).thenReturn(false);

            Map<String, Object> snapshot = handler.buildModelsSnapshot();

            assertThat(snapshot.get("defaultModel")).isEqualTo("claude-haiku-3-5");
            assertThat(snapshot.get("customModels")).isEqualTo(List.of());
            assertThat(snapshot.get("filtered")).isEqualTo(false);
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> entries = (List<Map<String, Object>>) snapshot.get("availableModels");
            assertThat(entries).hasSize(1);
            assertThat(entries.get(0).get("id")).isEqualTo("claude-haiku-3-5");
            assertThat(entries.get(0).get("provider")).isEqualTo("anthropic");
            assertThat(entries.get(0).get("hasCredentials")).isEqualTo(true);
        }

        @Test
        void nullCustomsBecomesEmptyList() {
            when(settingsManager.load()).thenReturn(Settings.empty());
            when(modelCatalog.getAvailableModels()).thenReturn(List.of());
            when(modelCatalog.isFiltered()).thenReturn(false);

            Map<String, Object> snapshot = handler.buildModelsSnapshot();
            assertThat(snapshot.get("customModels")).isEqualTo(List.of());
        }
    }

    @Nested
    class ApplyDefaultModel {

        @Test
        void unknownModelRejected() {
            when(modelRegistry.getAllModels()).thenReturn(List.of(anthropicModel("known")));
            var result = handler.applyDefaultModel(new SettingsHandler.DefaultModelRequest("ghost"));
            assertThat(result.status).isEqualTo(400);
            assertThat(result.errorMessage).isEqualTo("unknown model: ghost");
            verify(settingsManager, never()).setGlobal(any(), any());
        }

        @Test
        void blankModelRejected() {
            var result = handler.applyDefaultModel(new SettingsHandler.DefaultModelRequest(""));
            assertThat(result.status).isEqualTo(400);
            assertThat(result.errorMessage).isEqualTo("model is required");
            verify(settingsManager, never()).setGlobal(any(), any());
        }

        @Test
        void nullRequestRejected() {
            var result = handler.applyDefaultModel(null);
            assertThat(result.status).isEqualTo(400);
            assertThat(result.errorMessage).isEqualTo("model is required");
        }

        @Test
        void knownModelPersisted() {
            when(modelRegistry.getAllModels()).thenReturn(List.of(anthropicModel("known")));
            var result = handler.applyDefaultModel(new SettingsHandler.DefaultModelRequest("known"));
            assertThat(result.status).isEqualTo(200);
            assertThat(result.body).containsEntry("defaultModel", "known");
            verify(settingsManager, times(1)).setGlobal("defaultModel", "known");
        }
    }

    @Nested
    class ApplyCustomModels {

        @Test
        void notArrayRejected() throws Exception {
            JsonNode body = MAPPER.readTree("{\"id\":\"x\"}");
            var result = handler.applyCustomModels(body);
            assertThat(result.status).isEqualTo(400);
            assertThat(result.errorMessage).isEqualTo("customModels payload must be a JSON array");
            verify(settingsManager, never()).setGlobal(any(), any());
            verify(customModelLoader, never()).refresh();
        }

        @Test
        void missingIdRejected() throws Exception {
            JsonNode body = MAPPER.readTree("[{\"api\":\"openai\",\"baseUrl\":\"u\",\"apiKey\":\"k\"}]");
            var result = handler.applyCustomModels(body);
            assertThat(result.status).isEqualTo(400);
            assertThat(result.errorMessage).contains("customModels[0].id is required");
            verify(customModelLoader, never()).refresh();
        }

        @Test
        void missingApiRejected() throws Exception {
            JsonNode body = MAPPER.readTree("[{\"id\":\"foo\",\"baseUrl\":\"u\",\"apiKey\":\"k\"}]");
            var result = handler.applyCustomModels(body);
            assertThat(result.status).isEqualTo(400);
            assertThat(result.errorMessage).contains("customModels[0].api is required");
        }

        @Test
        void duplicateIdRejected() throws Exception {
            String json = "[" + "{\"id\":\"foo\",\"api\":\"openai\",\"baseUrl\":\"u\",\"apiKey\":\"k\"},"
                    + "{\"id\":\"foo\",\"api\":\"openai\",\"baseUrl\":\"u2\",\"apiKey\":\"k2\"}" + "]";
            JsonNode body = MAPPER.readTree(json);
            var result = handler.applyCustomModels(body);
            assertThat(result.status).isEqualTo(400);
            assertThat(result.errorMessage).isEqualTo("duplicate customModels id: foo");
            verify(customModelLoader, never()).refresh();
        }

        @Test
        void emptyArrayClearsAndRefreshes() throws Exception {
            JsonNode body = MAPPER.readTree("[]");
            var result = handler.applyCustomModels(body);
            assertThat(result.status).isEqualTo(200);
            assertThat(result.body).containsEntry("count", 0);
            verify(settingsManager, times(1)).setGlobal(eq("customModels"), any());
            verify(customModelLoader, times(1)).refresh();
        }

        @Test
        void validPayloadPersistedAndRegistryRefreshed() throws Exception {
            String json = "[{\"id\":\"local-foo\",\"api\":\"openai\",\"baseUrl\":\"http://x\",\"apiKey\":\"sk-x\"}]";
            JsonNode body = MAPPER.readTree(json);
            var result = handler.applyCustomModels(body);
            assertThat(result.status).isEqualTo(200);
            assertThat(result.body).containsEntry("count", 1);
            verify(settingsManager, times(1)).setGlobal(eq("customModels"), any());
            verify(customModelLoader, times(1)).refresh();
        }
    }
}
