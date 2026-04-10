package com.campusclaw.assistant.channel.gateway.protocol;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Map;

@JsonIgnoreProperties(ignoreUnknown = true)
public record HelloOkPayload(
    @JsonProperty("type") String type,
    @JsonProperty("protocol") int protocol,
    @JsonProperty("server") ServerInfo server,
    @JsonProperty("features") FeaturesInfo features,
    @JsonProperty("snapshot") Map<String, Object> snapshot,
    @JsonProperty("policy") PolicyInfo policy
) {}
