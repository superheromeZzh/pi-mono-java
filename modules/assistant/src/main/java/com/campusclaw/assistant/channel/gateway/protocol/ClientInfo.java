/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.campusclaw.assistant.channel.gateway.protocol;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Client identification block sent during the gateway handshake. Captures the client name,
 * version, OS, architecture, and process id for diagnostics and policy decisions.
 *
 * @version [br_eCampusCore 25.1.0_Next, 2026/05/13]
 * @since [br_eCampusCore 25.1.0_Next]
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ClientInfo(
        @JsonProperty("name") String name,
        @JsonProperty("version") String version,
        @JsonProperty("os") String os,
        @JsonProperty("arch") String arch,
        @JsonProperty("pid") Long pid) {}
