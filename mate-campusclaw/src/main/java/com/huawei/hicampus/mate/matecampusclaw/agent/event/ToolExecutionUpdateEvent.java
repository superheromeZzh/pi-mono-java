package com.huawei.hicampus.mate.matecampusclaw.agent.event;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Emitted for partial tool execution results.
 */
public record ToolExecutionUpdateEvent(
    @JsonProperty("toolCallId") String toolCallId,
    @JsonProperty("toolName") String toolName,
    @JsonProperty("args") Object args,
    @JsonProperty("partialResult") Object partialResult
) implements AgentEvent {
}
