package com.mariozechner.pi.codingagent.settings;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.annotation.Nullable;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record Settings(
    @JsonProperty("defaultProvider") @Nullable String defaultProvider,
    @JsonProperty("defaultModel") @Nullable String defaultModel,
    @JsonProperty("defaultThinkingLevel") @Nullable String defaultThinkingLevel,
    @JsonProperty("transport") @Nullable String transport,
    @JsonProperty("steeringMode") @Nullable String steeringMode,
    @JsonProperty("followUpMode") @Nullable String followUpMode,
    @JsonProperty("compaction") @Nullable CompactionSettings compaction,
    @JsonProperty("retry") @Nullable RetrySettings retry,
    @JsonProperty("theme") @Nullable String theme,
    @JsonProperty("hideThinkingBlock") @Nullable Boolean hideThinkingBlock,
    @JsonProperty("shellPath") @Nullable String shellPath,
    @JsonProperty("enableSkillCommands") @Nullable Boolean enableSkillCommands,
    @JsonProperty("sessionDir") @Nullable String sessionDir,
    @JsonProperty("packages") @Nullable List<String> packages,
    @JsonProperty("extensions") @Nullable List<String> extensions,
    @JsonProperty("customModels") @Nullable List<CustomModelConfig> customModels
) {
    public static Settings empty() {
        return new Settings(null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null);
    }

    public record CompactionSettings(
        @JsonProperty("enabled") @Nullable Boolean enabled,
        @JsonProperty("reserveTokens") @Nullable Integer reserveTokens,
        @JsonProperty("keepRecentTokens") @Nullable Integer keepRecentTokens
    ) {}

    public record RetrySettings(
        @JsonProperty("enabled") @Nullable Boolean enabled,
        @JsonProperty("maxRetries") @Nullable Integer maxRetries,
        @JsonProperty("baseDelayMs") @Nullable Long baseDelayMs,
        @JsonProperty("maxDelayMs") @Nullable Long maxDelayMs
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record CustomModelConfig(
        @JsonProperty("id") String id,
        @JsonProperty("name") @Nullable String name,
        @JsonProperty("api") String api,
        @JsonProperty("baseUrl") String baseUrl,
        @JsonProperty("apiKey") String apiKey,
        @JsonProperty("contextWindow") @Nullable Integer contextWindow,
        @JsonProperty("maxTokens") @Nullable Integer maxTokens,
        @JsonProperty("reasoning") @Nullable Boolean reasoning,
        @JsonProperty("inputModalities") @Nullable List<String> inputModalities,
        @JsonProperty("thinkingFormat") @Nullable String thinkingFormat
    ) {}
}
