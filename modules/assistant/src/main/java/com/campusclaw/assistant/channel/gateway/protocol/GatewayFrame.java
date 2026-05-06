/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.campusclaw.assistant.channel.gateway.protocol;

import java.util.Map;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@SuppressWarnings("checkstyle:top_class_comment")
@JsonIgnoreProperties(ignoreUnknown = true)
public record GatewayFrame(
        @JsonProperty("type") String type,
        @JsonProperty("id") String id,
        @JsonProperty("method") String method,
        @JsonProperty("params") Object params,
        @JsonProperty("event") String event,
        @JsonProperty("payload") Object payload,
        @JsonProperty("ok") Boolean ok,
        @JsonProperty("error") Object error,
        @JsonProperty("seq") Integer seq,
        @JsonProperty("stateVersion") Map<String, Integer> stateVersion) {}
