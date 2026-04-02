package com.huawei.hicampus.mate.matecampusclaw.ai.types;

import java.util.Map;

import com.fasterxml.jackson.annotation.JsonProperty;

import jakarta.annotation.Nullable;

/**
 * Options for streaming LLM API calls.
 *
 * <p>All fields are optional. Use {@link Builder} to construct instances fluently.
 *
 * @param temperature     sampling temperature (0.0 - 2.0)
 * @param maxTokens       maximum tokens to generate
 * @param apiKey          API key override for this request
 * @param transport       transport protocol (SSE, WebSocket, auto)
 * @param cacheRetention  prompt cache retention policy
 * @param sessionId       session identifier for stateful conversations
 * @param headers         additional HTTP headers for the request
 * @param maxRetryDelayMs maximum retry delay in milliseconds
 * @param metadata        arbitrary metadata to attach to the request
 */
public record StreamOptions(
    @JsonProperty("temperature") @Nullable Double temperature,
    @JsonProperty("maxTokens") @Nullable Integer maxTokens,
    @JsonProperty("apiKey") @Nullable String apiKey,
    @JsonProperty("transport") @Nullable Transport transport,
    @JsonProperty("cacheRetention") @Nullable CacheRetention cacheRetention,
    @JsonProperty("sessionId") @Nullable String sessionId,
    @JsonProperty("headers") @Nullable Map<String, String> headers,
    @JsonProperty("maxRetryDelayMs") @Nullable Long maxRetryDelayMs,
    @JsonProperty("metadata") @Nullable Map<String, Object> metadata
) {

    /** Returns a StreamOptions with all fields null. */
    public static StreamOptions empty() {
        return new StreamOptions(null, null, null, null, null, null, null, null, null);
    }

    /** Returns a new {@link Builder} initialized with default (null) values. */
    public static Builder builder() {
        return new Builder();
    }

    /** Returns a new {@link Builder} pre-populated from this instance. */
    public Builder toBuilder() {
        return new Builder()
            .temperature(temperature)
            .maxTokens(maxTokens)
            .apiKey(apiKey)
            .transport(transport)
            .cacheRetention(cacheRetention)
            .sessionId(sessionId)
            .headers(headers)
            .maxRetryDelayMs(maxRetryDelayMs)
            .metadata(metadata);
    }

    public static final class Builder {
        private Double temperature;
        private Integer maxTokens;
        private String apiKey;
        private Transport transport;
        private CacheRetention cacheRetention;
        private String sessionId;
        private Map<String, String> headers;
        private Long maxRetryDelayMs;
        private Map<String, Object> metadata;

        Builder() {}

        public Builder temperature(@Nullable Double temperature) {
            this.temperature = temperature;
            return this;
        }

        public Builder maxTokens(@Nullable Integer maxTokens) {
            this.maxTokens = maxTokens;
            return this;
        }

        public Builder apiKey(@Nullable String apiKey) {
            this.apiKey = apiKey;
            return this;
        }

        public Builder transport(@Nullable Transport transport) {
            this.transport = transport;
            return this;
        }

        public Builder cacheRetention(@Nullable CacheRetention cacheRetention) {
            this.cacheRetention = cacheRetention;
            return this;
        }

        public Builder sessionId(@Nullable String sessionId) {
            this.sessionId = sessionId;
            return this;
        }

        public Builder headers(@Nullable Map<String, String> headers) {
            this.headers = headers;
            return this;
        }

        public Builder maxRetryDelayMs(@Nullable Long maxRetryDelayMs) {
            this.maxRetryDelayMs = maxRetryDelayMs;
            return this;
        }

        public Builder metadata(@Nullable Map<String, Object> metadata) {
            this.metadata = metadata;
            return this;
        }

        public StreamOptions build() {
            return new StreamOptions(
                temperature, maxTokens, apiKey, transport, cacheRetention,
                sessionId, headers, maxRetryDelayMs, metadata
            );
        }
    }
}
