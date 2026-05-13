/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.hicampus.mate.matecampusclaw.codingagent.mode.rpc;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import jakarta.annotation.Nullable;

/**
 * Inbound command frame for the JSONL RPC mode. Carries a {@code type} discriminator, an
 * optional correlation id, and a loose union of payload fields (message, model, thinkingLevel,
 * value, command) interpreted according to {@code type}.
 *
 * @version [br_eCampusCore 25.1.0_Next, 2026/05/13]
 * @since [br_eCampusCore 25.1.0_Next]
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record RpcCommand(
        @JsonProperty("type") String type,
        @JsonProperty("id") @Nullable String id,
        @JsonProperty("message") @Nullable String message,
        @JsonProperty("model") @Nullable String model,
        @JsonProperty("thinkingLevel") @Nullable String thinkingLevel,
        @JsonProperty("value") @Nullable Object value,
        @JsonProperty("command") @Nullable String command) {}
