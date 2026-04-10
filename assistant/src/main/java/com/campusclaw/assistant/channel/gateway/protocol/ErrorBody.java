package com.campusclaw.assistant.channel.gateway.protocol;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Map;

@JsonIgnoreProperties(ignoreUnknown = true)
public record ErrorBody(
    @JsonProperty("code") String code,
    @JsonProperty("message") String message,
    @JsonProperty("details") Map<String, Object> details,
    @JsonProperty("retryable") boolean retryable,
    @JsonProperty("retryAfterMs") long retryAfterMs
) {}
