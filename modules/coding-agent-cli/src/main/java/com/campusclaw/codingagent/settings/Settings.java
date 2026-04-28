package com.campusclaw.codingagent.settings;

import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import jakarta.annotation.Nullable;

@JsonIgnoreProperties(ignoreUnknown = true)
public record Settings(
    @JsonProperty("defaultProvider") @Nullable String defaultProvider,
    @JsonProperty("defaultModel") @Nullable String defaultModel,
    /** opencode-style alias for defaultModel (e.g. "anthropic/claude-sonnet-4"). */
    @JsonProperty("model") @Nullable String model,
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
    @JsonProperty("images") @Nullable ImageSettings images,
    /** Per-provider config: API key / base URL / headers (opencode-style). */
    @JsonProperty("provider") @Nullable Map<String, ProviderConfig> provider,
    /** Per-agent overrides: e.g. {"summarizer": {"model": "..."}}. */
    @JsonProperty("agent") @Nullable Map<String, AgentConfig> agent
) {
    public static Settings empty() {
        return new Settings(null, null, null, null, null, null, null, null, null, null, null,
                null, null, null, null, null, null, null, null, null, null, null, null, null, null,
                null, null, null);
    }

    /** Returns the resolved default model id. opencode-style "model" wins over "defaultModel". */
    @Nullable
    public String resolvedDefaultModel() {
        if (model != null && !model.isBlank()) { return model; }
        return defaultModel;
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

    /**
     * opencode-style provider config block:
     * {"provider": {"zai": {"apiKey": "${ZAI_API_KEY}", "baseURL": "..."}}}
     *
     * Values are run through {@code ConfigValueResolver} so {@code ${ENV}} and
     * {@code ${ENV:-default}} placeholders expand at read time.
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ProviderConfig(
        @JsonProperty("apiKey") @Nullable String apiKey,
        /** Both {@code baseURL} (opencode) and {@code baseUrl} are accepted. */
        @JsonProperty("baseURL") @Nullable String baseURL,
        @JsonProperty("baseUrl") @Nullable String baseUrlAlt,
        @JsonProperty("headers") @Nullable Map<String, String> headers
    ) {
        /** Returns the effective base URL — {@code baseURL} preferred, falls back to {@code baseUrl}. */
        @Nullable
        public String effectiveBaseUrl() {
            if (baseURL != null && !baseURL.isBlank()) { return baseURL; }
            return baseUrlAlt;
        }
    }

    /**
     * Per-agent config: scopes a model to a named role like "summarizer" or
     * "subagent". opencode-equivalent: {@code agent.<name>.model}.
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record AgentConfig(
        @JsonProperty("model") @Nullable String model
    ) {}
}
