package com.huawei.hicampus.mate.matecampusclaw.agent.event;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Emitted when a tool finishes execution.
 */
public record ToolExecutionEndEvent(
    @JsonProperty("toolCallId") String toolCallId,
    @JsonProperty("toolName") String toolName,
    @JsonProperty("result") Object result,
    @JsonProperty("isError") boolean isError
) implements AgentEvent {
}
