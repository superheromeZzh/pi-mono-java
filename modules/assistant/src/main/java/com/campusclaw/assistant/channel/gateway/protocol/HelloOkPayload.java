/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.campusclaw.assistant.channel.gateway.protocol;

import java.util.Map;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Payload of the server's {@code hello-ok} response to a successful {@code connect}
 * handshake. Echoes the negotiated protocol version, server identity, advertised
 * methods/events, an initial state snapshot, and the per-connection policy limits.
 *
 * @version [br_eCampusCore 25.1.0_Next, 2026/05/13]
 * @since [br_eCampusCore 25.1.0_Next]
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record HelloOkPayload(
        @JsonProperty("type") String type,
        @JsonProperty("protocol") int protocol,
        @JsonProperty("server") ServerInfo server,
        @JsonProperty("features") FeaturesInfo features,
        @JsonProperty("snapshot") Map<String, Object> snapshot,
        @JsonProperty("policy") PolicyInfo policy) {}
