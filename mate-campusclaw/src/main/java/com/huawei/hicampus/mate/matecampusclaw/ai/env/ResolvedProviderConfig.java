/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.hicampus.mate.matecampusclaw.ai.env;

import java.util.Map;

import com.huawei.hicampus.mate.matecampusclaw.ai.types.Model;

import jakarta.annotation.Nullable;

/**
 * Resolved per-provider runtime configuration. Combines API key, base URL, and
 * optional custom HTTP headers from any source (settings file, model-embedded,
 * environment variables).
 *
 * @param apiKey  the resolved API key, or null when none is available
 * @param baseUrl explicit base URL override, or null to use the model's default
 * @param headers optional extra HTTP headers
 *
 * @version [br_eCampusCore 25.1.0_Next, 2026/05/06]
 * @since [br_eCampusCore 25.1.0_Next]
 */
public record ResolvedProviderConfig(
        @Nullable String apiKey, @Nullable String baseUrl, @Nullable Map<String, String> headers) {

    @SuppressWarnings("checkstyle:java_doc_format_missing")
    public static ResolvedProviderConfig empty() {
        return new ResolvedProviderConfig(null, null, null);
    }

    @SuppressWarnings("checkstyle:java_doc_format_missing")
    public static ResolvedProviderConfig ofApiKey(@Nullable String apiKey) {
        return new ResolvedProviderConfig(apiKey, null, null);
    }

    /** Returns the effective base URL: explicit override or the model's default. */
    public String resolveBaseUrl(Model model) {
        if (baseUrl != null && !baseUrl.isBlank()) {
            return baseUrl;
        }
        return model.baseUrl();
    }
}
