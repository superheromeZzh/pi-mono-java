/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.hicampus.mate.matecampusclaw.assistant.channel.gateway.protocol;

import java.util.Map;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@SuppressWarnings("checkstyle:top_class_comment")
@JsonIgnoreProperties(ignoreUnknown = true)
public record HelloOkPayload(
        @JsonProperty("type") String type,
        @JsonProperty("protocol") int protocol,
        @JsonProperty("server") ServerInfo server,
        @JsonProperty("features") FeaturesInfo features,
        @JsonProperty("snapshot") Map<String, Object> snapshot,
        @JsonProperty("policy") PolicyInfo policy) {}
