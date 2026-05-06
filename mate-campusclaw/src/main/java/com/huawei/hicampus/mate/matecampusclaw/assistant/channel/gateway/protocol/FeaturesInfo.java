/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.hicampus.mate.matecampusclaw.assistant.channel.gateway.protocol;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@SuppressWarnings("checkstyle:top_class_comment")
@JsonIgnoreProperties(ignoreUnknown = true)
public record FeaturesInfo(
        @JsonProperty("methods") List<String> methods, @JsonProperty("events") List<String> events) {}
