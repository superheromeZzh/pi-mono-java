/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.campusclaw.assistant.channel.gateway.protocol;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Per-connection policy advertised by the gateway server. Caps inbound payload size,
 * the buffered backlog the server retains, and the heartbeat tick interval in milliseconds.
 *
 * @version [br_eCampusCore 25.1.0_Next, 2026/05/13]
 * @since [br_eCampusCore 25.1.0_Next]
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record PolicyInfo(
        @JsonProperty("maxPayload") int maxPayload,
        @JsonProperty("maxBufferedBytes") int maxBufferedBytes,
        @JsonProperty("tickIntervalMs") int tickIntervalMs) {}
