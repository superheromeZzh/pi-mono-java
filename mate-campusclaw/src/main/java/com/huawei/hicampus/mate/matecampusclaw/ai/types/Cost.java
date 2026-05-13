/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.hicampus.mate.matecampusclaw.ai.types;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Breakdown of monetary cost (in USD) for an LLM API call.
 *
 * @param input      cost of input tokens
 * @param output     cost of output tokens
 * @param cacheRead  cost of prompt-cache read tokens
 * @param cacheWrite cost of prompt-cache write tokens
 * @param total      total cost (sum of all components)
 *
 * @version [br_eCampusCore 25.1.0_Next, 2026/05/06]
 * @since [br_eCampusCore 25.1.0_Next]
 */
public record Cost(
        @JsonProperty("input") double input,
        @JsonProperty("output") double output,
        @JsonProperty("cacheRead") double cacheRead,
        @JsonProperty("cacheWrite") double cacheWrite,
        @JsonProperty("total") double total) {

    /**
     * Returns a zero-valued {@link Cost} instance.
     *
     * @return a {@link Cost} with all fields set to {@code 0.0}
     */
    public static Cost empty() {
        return new Cost(0.0, 0.0, 0.0, 0.0, 0.0);
    }
}
