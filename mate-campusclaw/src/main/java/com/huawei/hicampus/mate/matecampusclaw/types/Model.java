package com.huawei.hicampus.mate.matecampusclaw.ai.types;

import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonProperty;

import jakarta.annotation.Nullable;

/**
 * Definition of an LLM model with its capabilities and pricing.
 *
 * @param id              unique model identifier (e.g. "claude-opus-4-6")
 * @param name            human-readable display name
 * @param api             the API protocol to use
 * @param provider        the LLM provider
 * @param baseUrl         base URL for the API endpoint
 * @param reasoning       whether the model supports extended thinking / reasoning
 * @param inputModalities supported input modalities (text, image, etc.)
 * @param cost            per-million-token cost breakdown
 * @param contextWindow   maximum context window size in tokens
 * @param maxTokens       maximum output tokens per response
 * @param headers         optional custom HTTP headers for API calls
 * @param thinkingFormat  optional thinking format identifier (e.g. "zai")
 * @param apiKey          optional API key embedded in the model (for custom models)
 */
public record Model(
    @JsonProperty("id") String id,
    @JsonProperty("name") String name,
    @JsonProperty("api") Api api,
    @JsonProperty("provider") Provider provider,
    @JsonProperty("baseUrl") String baseUrl,
    @JsonProperty("reasoning") boolean reasoning,
    @JsonProperty("inputModalities") List<InputModality> inputModalities,
    @JsonProperty("cost") ModelCost cost,
    @JsonProperty("contextWindow") int contextWindow,
    @JsonProperty("maxTokens") int maxTokens,
    @JsonProperty("headers") @Nullable Map<String, String> headers,
    @JsonProperty("thinkingFormat") @Nullable String thinkingFormat,
    @JsonProperty("apiKey") @Nullable String apiKey
) {
}
