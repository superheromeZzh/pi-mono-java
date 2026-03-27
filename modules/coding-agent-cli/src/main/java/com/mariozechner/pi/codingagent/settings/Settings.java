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
    @JsonProperty("extensions") @Nullable List<String> extensions
) {
    public static Settings empty() {
        return new Settings(null, null, null, null, null, null, null, null, null, null, null, null, null, null, null);
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
}
