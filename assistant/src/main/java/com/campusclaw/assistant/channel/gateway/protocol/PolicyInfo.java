package com.campusclaw.assistant.channel.gateway.protocol;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public record PolicyInfo(
    @JsonProperty("maxPayload") int maxPayload,
    @JsonProperty("maxBufferedBytes") int maxBufferedBytes,
    @JsonProperty("tickIntervalMs") int tickIntervalMs
) {}
