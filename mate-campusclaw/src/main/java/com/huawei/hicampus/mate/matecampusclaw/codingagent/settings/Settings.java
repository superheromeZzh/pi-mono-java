package com.huawei.hicampus.mate.matecampusclaw.codingagent.settings;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import jakarta.annotation.Nullable;

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
    @JsonProperty("customModels") @Nullable List<CustomModelConfig> customModels,
    @JsonProperty("quietStartup") @Nullable Boolean quietStartup,
    @JsonProperty("shellCommandPrefix") @Nullable String shellCommandPrefix,
    @JsonProperty("enabledModels") @Nullable List<String> enabledModels,
    @JsonProperty("doubleEscapeAction") @Nullable String doubleEscapeAction,
    @JsonProperty("treeFilterMode") @Nullable String treeFilterMode,
    @JsonProperty("collapseChangelog") @Nullable Boolean collapseChangelog,
    @JsonProperty("branchSummary") @Nullable BranchSummarySettings branchSummary,
    @JsonProperty("terminal") @Nullable TerminalSettings terminal,
    @JsonProperty("images") @Nullable ImageSettings images
) {
    public static Settings empty() {
        return new Settings(null, null, null, null, null, null, null, null, null, null, null,
                null, null, null, null, null, null, null, null, null, null, null, null, null, null);
    }

    public record BranchSummarySettings(
        @JsonProperty("reserveTokens") @Nullable Integer reserveTokens,
        @JsonProperty("skipPrompt") @Nullable Boolean skipPrompt
    ) {}

    public record TerminalSettings(
        @JsonProperty("showImages") @Nullable Boolean showImages,
        @JsonProperty("clearOnShrink") @Nullable Boolean clearOnShrink
    ) {}

    public record ImageSettings(
        @JsonProperty("autoResize") @Nullable Boolean autoResize,
        @JsonProperty("blockImages") @Nullable Boolean blockImages
    ) {}

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
