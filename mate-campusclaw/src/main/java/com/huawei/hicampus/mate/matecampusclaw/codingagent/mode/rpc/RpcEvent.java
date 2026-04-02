package com.huawei.hicampus.mate.matecampusclaw.codingagent.mode.rpc;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import jakarta.annotation.Nullable;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record RpcEvent(
    @JsonProperty("type") String type,
    @JsonProperty("id") @Nullable String requestId,
    @JsonProperty("data") @Nullable Object data,
    @JsonProperty("error") @Nullable String error
) {
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
