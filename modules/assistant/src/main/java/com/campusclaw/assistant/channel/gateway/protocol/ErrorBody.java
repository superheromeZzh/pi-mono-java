/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.campusclaw.assistant.channel.gateway.protocol;

import java.util.Map;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@SuppressWarnings("checkstyle:top_class_comment")
@JsonIgnoreProperties(ignoreUnknown = true)
public record ErrorBody(
        @JsonProperty("code") String code,
        @JsonProperty("message") String message,
        @JsonProperty("details") Map<String, Object> details,
        @JsonProperty("retryable") boolean retryable,
        @JsonProperty("retryAfterMs") long retryAfterMs) {}
