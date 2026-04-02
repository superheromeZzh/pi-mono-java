package com.huawei.hicampus.mate.matecampusclaw.ai.types;

import com.fasterxml.jackson.annotation.JsonProperty;

import jakarta.annotation.Nullable;

/**
 * Token budgets for each thinking level.
 *
 * @param minimal token budget for minimal thinking
 * @param low     token budget for low thinking
 * @param medium  token budget for medium thinking
 * @param high    token budget for high thinking
 */
public record ThinkingBudgets(
    @JsonProperty("minimal") @Nullable Integer minimal,
    @JsonProperty("low") @Nullable Integer low,
    @JsonProperty("medium") @Nullable Integer medium,
    @JsonProperty("high") @Nullable Integer high
) {
}
