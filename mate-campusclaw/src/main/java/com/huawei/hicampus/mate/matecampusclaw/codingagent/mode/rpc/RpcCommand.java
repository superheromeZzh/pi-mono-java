package com.huawei.hicampus.mate.matecampusclaw.codingagent.mode.rpc;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import jakarta.annotation.Nullable;

@JsonIgnoreProperties(ignoreUnknown = true)
public record RpcCommand(
    @JsonProperty("type") String type,
    @JsonProperty("id") @Nullable String id,
    @JsonProperty("message") @Nullable String message,
    @JsonProperty("model") @Nullable String model,
    @JsonProperty("thinkingLevel") @Nullable String thinkingLevel,
    @JsonProperty("value") @Nullable Object value,
    @JsonProperty("command") @Nullable String command
) {}
