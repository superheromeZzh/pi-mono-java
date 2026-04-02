package com.huawei.hicampus.mate.matecampusclaw.ai.types;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Per-million-token cost in USD for a model.
 *
 * @param input      cost per million input tokens
 * @param output     cost per million output tokens
 * @param cacheRead  cost per million cache-read tokens
 * @param cacheWrite cost per million cache-write tokens
 */
public record ModelCost(
    @JsonProperty("input") double input,
    @JsonProperty("output") double output,
    @JsonProperty("cacheRead") double cacheRead,
    @JsonProperty("cacheWrite") double cacheWrite
) {
}
