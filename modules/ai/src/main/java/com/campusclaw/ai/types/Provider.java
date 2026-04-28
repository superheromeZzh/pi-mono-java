package com.campusclaw.ai.types;

import java.util.Optional;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * LLM provider identifier.
 */
public enum Provider {

    ANTHROPIC("anthropic"),
    OPENAI("openai"),
    GOOGLE("google"),
    GOOGLE_VERTEX("google-vertex"),
    MISTRAL("mistral"),
    AMAZON_BEDROCK("amazon-bedrock"),
    AZURE_OPENAI("azure-openai-responses"),
    OPENAI_CODEX("openai-codex"),
    ZAI("zai"),
    KIMI_CODING("kimi-coding"),
    MINIMAX("minimax"),
    MINIMAX_CN("minimax-cn"),
    GITHUB_COPILOT("github-copilot"),
    XAI("xai"),
    GROQ("groq"),
    CEREBRAS("cerebras"),
    OPENROUTER("openrouter"),
    VERCEL_AI_GATEWAY("vercel-ai-gateway"),
    HUGGINGFACE("huggingface"),
    GOOGLE_GEMINI_CLI("google-gemini-cli"),
    GOOGLE_ANTIGRAVITY("google-antigravity"),
    OPENCODE("opencode"),
    CUSTOM("custom");

    private final String value;

    Provider(String value) {
        this.value = value;
    }

    @JsonValue
    public String value() {
        return value;
    }

    @JsonCreator
    public static Provider fromValue(String value) {
        for (var p : values()) {
            if (p.value.equals(value)) {
                return p;
            }
        }
        throw new IllegalArgumentException("Unknown Provider: " + value);
    }

    /**
     * Lenient variant of {@link #fromValue(String)} for user-supplied input.
     * Returns empty for unknown / null / blank values instead of throwing.
     * Matching is also case-insensitive and treats {@code _} and {@code -} as
     * equivalent so {@code AZURE_OPENAI} matches {@code "azure-openai"} and
     * {@code "azure_openai"}.
     */
    public static Optional<Provider> tryFromValue(String value) {
        if (value == null || value.isBlank()) { return Optional.empty(); }
        String normalized = value.toLowerCase().replace('_', '-');
        for (var p : values()) {
            if (p.value.toLowerCase().replace('_', '-').equals(normalized)) {
                return Optional.of(p);
            }
        }
        return Optional.empty();
    }
}
