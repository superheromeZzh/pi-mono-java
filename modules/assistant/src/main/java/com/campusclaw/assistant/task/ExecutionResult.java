package com.campusclaw.assistant.task;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;

public record ExecutionResult(
    @JsonProperty("executedAt") Instant executedAt,
    @JsonProperty("status") String status,
    @JsonProperty("result") String result,
    @JsonProperty("durationMs") long durationMs
) {}
