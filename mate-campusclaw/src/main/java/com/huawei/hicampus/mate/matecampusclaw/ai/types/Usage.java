package com.huawei.hicampus.mate.matecampusclaw.ai.types;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Token usage statistics and associated cost for an LLM API call.
 *
 * @param input       number of input tokens
 * @param output      number of output tokens
 * @param cacheRead   number of prompt-cache read tokens
 * @param cacheWrite  number of prompt-cache write tokens
 * @param totalTokens total number of tokens consumed
 * @param cost        monetary cost breakdown in USD
 */
public record Usage(
    @JsonProperty("input") int input,
    @JsonProperty("output") int output,
    @JsonProperty("cacheRead") int cacheRead,
    @JsonProperty("cacheWrite") int cacheWrite,
    @JsonProperty("totalTokens") int totalTokens,
    @JsonProperty("cost") Cost cost
) {

    /** Returns a zero-valued Usage instance with an empty Cost. */
    public static Usage empty() {
        return new Usage(0, 0, 0, 0, 0, Cost.empty());
    }
}
