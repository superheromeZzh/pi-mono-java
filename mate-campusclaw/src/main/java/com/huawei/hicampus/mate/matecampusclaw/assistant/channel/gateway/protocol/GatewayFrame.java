/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.hicampus.mate.matecampusclaw.assistant.channel.gateway.protocol;

import java.util.Map;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Generic envelope for frames exchanged over the WebSocket gateway protocol. Carries either
 * a request ({@code type}/{@code id}/{@code method}/{@code params}), an event
 * ({@code type}/{@code event}/{@code payload}), or a response ({@code type}/{@code id}/{@code ok}/{@code error}),
 * with optional sequence and per-stream state-version map for delivery ordering.
 *
 * @version [br_eCampusCore 25.1.0_Next, 2026/05/13]
 * @since [br_eCampusCore 25.1.0_Next]
 */
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
