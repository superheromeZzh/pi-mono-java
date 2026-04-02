package com.huawei.hicampus.mate.matecampusclaw.ai.types;

import com.fasterxml.jackson.annotation.JsonProperty;

import jakarta.annotation.Nullable;

/**
 * A thinking/reasoning content block produced during extended thinking.
 *
 * @param thinking          the thinking text
 * @param thinkingSignature optional signature for the thinking block
 * @param redacted          whether the thinking content has been redacted
 */
public record ThinkingContent(
    @JsonProperty("thinking") String thinking,
    @JsonProperty("thinkingSignature") @Nullable String thinkingSignature,
    @JsonProperty("redacted") boolean redacted
) implements ContentBlock {

    public ThinkingContent(String thinking) {
        this(thinking, null, false);
    }
}
