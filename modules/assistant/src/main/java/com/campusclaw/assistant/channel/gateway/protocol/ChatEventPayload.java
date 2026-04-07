package com.campusclaw.assistant.channel.gateway.protocol;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Map;

@JsonIgnoreProperties(ignoreUnknown = true)
public record ChatEventPayload(
    @JsonProperty("runId") String runId,
    @JsonProperty("sessionKey") String sessionKey,
    @JsonProperty("seq") int seq,
    @JsonProperty("state") String state,
    @JsonProperty("message") Map<String, Object> message,
    @JsonProperty("errorMessage") String errorMessage,
    @JsonProperty("usage") Map<String, Object> usage,
    @JsonProperty("stopReason") String stopReason
) {}
