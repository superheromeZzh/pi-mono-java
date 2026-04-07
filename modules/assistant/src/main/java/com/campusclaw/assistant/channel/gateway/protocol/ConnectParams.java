package com.campusclaw.assistant.channel.gateway.protocol;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public record ConnectParams(
    @JsonProperty("minProtocol") Integer minProtocol,
    @JsonProperty("maxProtocol") Integer maxProtocol,
    @JsonProperty("client") ClientInfo client,
    @JsonProperty("auth") AuthInfo auth
) {}
