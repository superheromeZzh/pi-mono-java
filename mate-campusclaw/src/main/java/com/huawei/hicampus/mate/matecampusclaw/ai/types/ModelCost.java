/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.hicampus.mate.matecampusclaw.ai.types;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Per-million-token cost in USD for a model.
 *
 * @param input      cost per million input tokens
 * @param output     cost per million output tokens
 * @param cacheRead  cost per million cache-read tokens
 * @param cacheWrite cost per million cache-write tokens
 *
 * @version [br_eCampusCore 25.1.0_Next, 2026/05/06]
 * @since [br_eCampusCore 25.1.0_Next]
 */
public record ModelCost(
        @JsonProperty("input") double input,
        @JsonProperty("output") double output,
        @JsonProperty("cacheRead") double cacheRead,
        @JsonProperty("cacheWrite") double cacheWrite) {}
