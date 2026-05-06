/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.hicampus.mate.matecampusclaw.assistant.channel.gateway.protocol;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@SuppressWarnings("checkstyle:top_class_comment")
@JsonIgnoreProperties(ignoreUnknown = true)
public record SessionsSendParams(@JsonProperty("key") String key, @JsonProperty("message") Object message) {}
