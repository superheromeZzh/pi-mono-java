package com.campusclaw.assistant.channel.gateway.protocol;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record FeaturesInfo(
    @JsonProperty("methods") List<String> methods,
    @JsonProperty("events") List<String> events
) {}
