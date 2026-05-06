/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.campusclaw.assistant.channel.gateway.protocol;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@SuppressWarnings("checkstyle:top_class_comment")
@JsonIgnoreProperties(ignoreUnknown = true)
public record ConnectParams(
        @JsonProperty("minProtocol") Integer minProtocol,
        @JsonProperty("maxProtocol") Integer maxProtocol,
        @JsonProperty("client") ClientInfo client,
        @JsonProperty("auth") AuthInfo auth) {}
