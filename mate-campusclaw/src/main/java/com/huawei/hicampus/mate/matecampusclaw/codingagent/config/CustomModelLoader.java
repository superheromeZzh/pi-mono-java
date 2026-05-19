/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.hicampus.mate.matecampusclaw.codingagent.config;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import com.huawei.hicampus.mate.matecampusclaw.ai.model.ModelRegistry;
import com.huawei.hicampus.mate.matecampusclaw.ai.types.Api;
import com.huawei.hicampus.mate.matecampusclaw.ai.types.InputModality;
import com.huawei.hicampus.mate.matecampusclaw.ai.types.Model;
import com.huawei.hicampus.mate.matecampusclaw.ai.types.ModelCost;
import com.huawei.hicampus.mate.matecampusclaw.ai.types.Provider;
import com.huawei.hicampus.mate.matecampusclaw.codingagent.settings.Settings;
import com.huawei.hicampus.mate.matecampusclaw.codingagent.settings.SettingsManager;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

/**
 * Registers user-defined models into the {@link ModelRegistry} once Spring is
 * ready: both {@code settings.customModels} entries and any catalog file at
 * {@code ~/.campusclaw/agent/models.json}. Runs once at startup so that the
 * model selector overlay and {@code -m} flag see the same catalog.
 *
 * @version [br_eCampusCore 25.1.0_Next, 2026/05/06]
 * @since [br_eCampusCore 25.1.0_Next]
 */
@Service
public class CustomModelLoader {

    private static final Logger log = LoggerFactory.getLogger(CustomModelLoader.class);

    private final SettingsManager settingsManager;
    private final ModelRegistry modelRegistry;

    public CustomModelLoader(SettingsManager settingsManager, ModelRegistry modelRegistry) {
        this.settingsManager = settingsManager;
        this.modelRegistry = modelRegistry;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void registerCustomModels() {
        Settings settings;
        try {
            settings = settingsManager.load();
        } catch (Exception e) {
            return;
        }
        if (settings.customModels() == null || settings.customModels().isEmpty()) {
            return;
        }

        List<Model> toRegister = new ArrayList<>();
        for (Settings.CustomModelConfig cfg : settings.customModels()) {
            try {
                toRegister.add(toModel(cfg));
            } catch (Exception e) {
                log.warn("Skipping invalid custom model {}: {}", cfg.id(), e.getMessage());
            }
        }
        if (!toRegister.isEmpty()) {
            modelRegistry.registerAll(toRegister);
            log.info("Registered {} custom model(s) from settings.json", toRegister.size());
        }
    }

    private static Model toModel(Settings.CustomModelConfig cfg) {
        Api api = Api.fromValue(cfg.api());
        Provider provider = Provider.CUSTOM;
        ModelCost cost = new ModelCost(0, 0, 0, 0);
        List<InputModality> modalities = new ArrayList<>();
        if (cfg.inputModalities() != null) {
            for (String m : cfg.inputModalities()) {
                try {
                    modalities.add(InputModality.valueOf(m.toUpperCase(Locale.ROOT)));
                } catch (IllegalArgumentException ignored) {
                    // skip unknown modality string from settings.json — falls back to TEXT below
                }
            }
        }
        if (modalities.isEmpty()) {
            modalities.add(InputModality.TEXT);
        }
        return new Model(
                cfg.id(),
                cfg.name() != null ? cfg.name() : cfg.id(),
                api,
                provider,
                ConfigValueResolver.resolve(cfg.baseUrl()),
                cfg.reasoning() != null && cfg.reasoning(),
                modalities,
                cost,
                cfg.contextWindow() != null ? cfg.contextWindow() : 128000,
                cfg.maxTokens() != null ? cfg.maxTokens() : 8192,
                null,
                cfg.thinkingFormat(),
                ConfigValueResolver.resolve(cfg.apiKey()));
    }
}
