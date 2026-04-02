package com.huawei.hicampus.mate.matecampusclaw.ai.types;

import java.util.Map;

import jakarta.annotation.Nullable;

/**
 * Factory for creating common {@link SimpleStreamOptions} configurations.
 *
 * <p>Provides convenience methods for building options from settings,
 * model defaults, and common use cases.
 */
public final class SimpleStreamOptionsFactory {

    private SimpleStreamOptionsFactory() {}

    /**
     * Creates options from model defaults.
     */
    public static SimpleStreamOptions fromModel(Model model) {
        var builder = SimpleStreamOptions.builder()
            .maxTokens(model.maxTokens());
        if (model.reasoning()) {
            builder.reasoning(ThinkingLevel.MEDIUM);
        }
        return builder.build();
    }

    /**
     * Creates options for a quick/fast response (lower tokens, no thinking).
     */
    public static SimpleStreamOptions fast(Model model) {
        return SimpleStreamOptions.builder()
            .maxTokens(Math.min(model.maxTokens(), 4096))
            .temperature(0.0)
            .build();
    }

    /**
     * Creates options for a thorough response (max tokens, thinking enabled if supported).
     */
    public static SimpleStreamOptions thorough(Model model) {
        var builder = SimpleStreamOptions.builder()
            .maxTokens(model.maxTokens());
        if (model.reasoning()) {
            builder.reasoning(ThinkingLevel.HIGH);
        }
        return builder.build();
    }

    /**
     * Creates options with custom API key.
     */
    public static SimpleStreamOptions withApiKey(String apiKey) {
        return SimpleStreamOptions.builder()
            .apiKey(apiKey)
            .build();
    }

    /**
     * Creates options with custom headers.
     */
    public static SimpleStreamOptions withHeaders(Map<String, String> headers) {
        return SimpleStreamOptions.builder()
            .headers(headers)
            .build();
    }

    /**
     * Creates options from a thinking level and model.
     */
    public static SimpleStreamOptions withThinking(Model model, @Nullable ThinkingLevel level) {
        var builder = SimpleStreamOptions.builder()
            .maxTokens(model.maxTokens());
        if (level != null && model.reasoning()) {
            builder.reasoning(level);
        }
        return builder.build();
    }

    /**
     * Merges base options with overrides. Override fields take precedence.
     */
    public static SimpleStreamOptions merge(SimpleStreamOptions base, SimpleStreamOptions overrides) {
        if (base == null) return overrides;
        if (overrides == null) return base;

        return SimpleStreamOptions.builder()
            .temperature(overrides.temperature() != null ? overrides.temperature() : base.temperature())
            .maxTokens(overrides.maxTokens() != null ? overrides.maxTokens() : base.maxTokens())
            .apiKey(overrides.apiKey() != null ? overrides.apiKey() : base.apiKey())
            .transport(overrides.transport() != null ? overrides.transport() : base.transport())
            .cacheRetention(overrides.cacheRetention() != null ? overrides.cacheRetention() : base.cacheRetention())
            .sessionId(overrides.sessionId() != null ? overrides.sessionId() : base.sessionId())
            .headers(overrides.headers() != null ? overrides.headers() : base.headers())
            .maxRetryDelayMs(overrides.maxRetryDelayMs() != null ? overrides.maxRetryDelayMs() : base.maxRetryDelayMs())
            .metadata(overrides.metadata() != null ? overrides.metadata() : base.metadata())
            .reasoning(overrides.reasoning() != null ? overrides.reasoning() : base.reasoning())
            .thinkingBudgets(overrides.thinkingBudgets() != null ? overrides.thinkingBudgets() : base.thinkingBudgets())
            .build();
    }
}
