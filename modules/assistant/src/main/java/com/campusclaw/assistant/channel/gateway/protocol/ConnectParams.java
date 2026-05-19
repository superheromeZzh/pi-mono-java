/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.campusclaw.assistant.channel.gateway.protocol;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Parameters supplied by the client in the initial gateway {@code connect} handshake.
 * Advertises the protocol version range the client can speak together with client
 * identification and bearer-token credentials.
 *
 * @version [br_eCampusCore 25.1.0_Next, 2026/05/13]
 * @since [br_eCampusCore 25.1.0_Next]
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ConnectParams(
        @JsonProperty("minProtocol") Integer minProtocol,
        @JsonProperty("maxProtocol") Integer maxProtocol,
        @JsonProperty("client") ClientInfo client,
        @JsonProperty("auth") AuthInfo auth) {}
