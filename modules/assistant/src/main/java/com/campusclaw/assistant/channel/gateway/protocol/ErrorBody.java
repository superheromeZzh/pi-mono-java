/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.campusclaw.assistant.channel.gateway.protocol;

import java.util.Map;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Structured error envelope returned on failed gateway requests. Carries an error code,
 * human-readable message, optional details map, and retry hints (retryable flag plus
 * suggested back-off in milliseconds).
 *
 * @version [br_eCampusCore 25.1.0_Next, 2026/05/13]
 * @since [br_eCampusCore 25.1.0_Next]
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ErrorBody(
        @JsonProperty("code") String code,
        @JsonProperty("message") String message,
        @JsonProperty("details") Map<String, Object> details,
        @JsonProperty("retryable") boolean retryable,
        @JsonProperty("retryAfterMs") long retryAfterMs) {}
