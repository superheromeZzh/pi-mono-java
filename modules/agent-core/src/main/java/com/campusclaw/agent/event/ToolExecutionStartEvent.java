package com.campusclaw.agent.event;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Emitted when a tool starts executing.
 */
public record ToolExecutionStartEvent(
    @JsonProperty("toolCallId") String toolCallId,
    @JsonProperty("toolName") String toolName,
    @JsonProperty("args") Object args
) implements AgentEvent {
}
