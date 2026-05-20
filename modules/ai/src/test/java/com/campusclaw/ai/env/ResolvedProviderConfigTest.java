/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.campusclaw.ai.env;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.List;
import java.util.Map;

import com.campusclaw.ai.types.Api;
import com.campusclaw.ai.types.Model;
import com.campusclaw.ai.types.Provider;

import org.junit.jupiter.api.Test;

class ResolvedProviderConfigTest {

    private static Model model(String baseUrl) {
        return new Model(
                "id",
                "name",
                Api.ANTHROPIC_MESSAGES,
                Provider.ANTHROPIC,
                baseUrl,
                false,
                List.of(),
                null,
                0,
                0,
                null,
                null,
                null);
    }

    @Test
    void emptyHasNullFields() {
        ResolvedProviderConfig cfg = ResolvedProviderConfig.empty();
        assertNull(cfg.apiKey());
        assertNull(cfg.baseUrl());
        assertNull(cfg.headers());
    }

    @Test
    void ofApiKey() {
        ResolvedProviderConfig cfg = ResolvedProviderConfig.ofApiKey("sk-test");
        assertEquals("sk-test", cfg.apiKey());
        assertNull(cfg.baseUrl());
        assertNull(cfg.headers());
    }

    @Test
    void resolveBaseUrlOverrideWins() {
        ResolvedProviderConfig cfg = new ResolvedProviderConfig("k", "https://override", Map.of());
        assertEquals("https://override", cfg.resolveBaseUrl(model("https://model")));
    }

    @Test
    void resolveBaseUrlFallsBackToModel() {
        ResolvedProviderConfig cfg = new ResolvedProviderConfig("k", null, Map.of());
        assertEquals("https://model", cfg.resolveBaseUrl(model("https://model")));
    }

    @Test
    void resolveBaseUrlBlankOverrideFallsBack() {
        ResolvedProviderConfig cfg = new ResolvedProviderConfig("k", "   ", Map.of());
        assertEquals("https://model", cfg.resolveBaseUrl(model("https://model")));
    }
}
