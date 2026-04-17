package com.campusclaw.assistant.channel.gateway.protocol;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public record ServerInfo(
    @JsonProperty("version") String version,
    @JsonProperty("connId") String connId
) {}
