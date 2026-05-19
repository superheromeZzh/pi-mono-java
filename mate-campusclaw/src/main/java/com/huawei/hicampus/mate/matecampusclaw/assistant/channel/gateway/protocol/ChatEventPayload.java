/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.hicampus.mate.matecampusclaw.assistant.channel.gateway.protocol;

import java.util.Map;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Payload of a streaming {@code chat.*} event frame. Identifies the originating run and
 * session, carries an in-stream sequence number, lifecycle state, the incremental message
 * body, optional error and usage details, and the terminal stop reason.
 *
 * @version [br_eCampusCore 25.1.0_Next, 2026/05/13]
 * @since [br_eCampusCore 25.1.0_Next]
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ChatEventPayload(
        @JsonProperty("runId") String runId,
        @JsonProperty("sessionKey") String sessionKey,
        @JsonProperty("seq") int seq,
        @JsonProperty("state") String state,
        @JsonProperty("message") Map<String, Object> message,
        @JsonProperty("errorMessage") String errorMessage,
        @JsonProperty("usage") Map<String, Object> usage,
        @JsonProperty("stopReason") String stopReason) {}
