/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.campusclaw.codingagent.mode.rpc;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import jakarta.annotation.Nullable;

/**
 * Outbound event frame emitted by the JSONL RPC mode. Holds a {@code type} discriminator, an
 * optional correlation id linking back to the originating {@link RpcCommand}, and either a
 * {@code data} payload or an {@code error} string. Static factories cover the broadcast,
 * response, and error shapes.
 *
 * @version [br_eCampusCore 25.1.0_Next, 2026/05/13]
 * @since [br_eCampusCore 25.1.0_Next]
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record RpcEvent(
        @JsonProperty("type") String type,
        @JsonProperty("id") @Nullable String requestId,
        @JsonProperty("data") @Nullable Object data,
        @JsonProperty("error") @Nullable String error) {
    public static RpcEvent of(String type, @Nullable Object data) {
        return new RpcEvent(type, null, data, null);
    }

    public static RpcEvent response(String requestId, String type, @Nullable Object data) {
        return new RpcEvent(type, requestId, data, null);
    }

    public static RpcEvent error(String requestId, String message) {
        return new RpcEvent("error", requestId, null, message);
    }
}
