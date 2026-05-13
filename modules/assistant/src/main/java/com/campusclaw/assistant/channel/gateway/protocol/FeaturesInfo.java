/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.campusclaw.assistant.channel.gateway.protocol;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Server capability advertisement carried in the gateway {@code hello-ok} payload.
 * Lists the request methods and event types this connection understands.
 *
 * @version [br_eCampusCore 25.1.0_Next, 2026/05/13]
 * @since [br_eCampusCore 25.1.0_Next]
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record FeaturesInfo(
        @JsonProperty("methods") List<String> methods, @JsonProperty("events") List<String> events) {}
