package com.campusclaw.ai.types;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Breakdown of monetary cost (in USD) for an LLM API call.
 *
 * @param input      cost of input tokens
 * @param output     cost of output tokens
 * @param cacheRead  cost of prompt-cache read tokens
 * @param cacheWrite cost of prompt-cache write tokens
 * @param total      total cost (sum of all components)
 */
public record Cost(
    @JsonProperty("input") double input,
    @JsonProperty("output") double output,
    @JsonProperty("cacheRead") double cacheRead,
    @JsonProperty("cacheWrite") double cacheWrite,
    @JsonProperty("total") double total
) {

    /** Returns a zero-valued Cost instance. */
    public static Cost empty() {
        return new Cost(0.0, 0.0, 0.0, 0.0, 0.0);
    }
}
