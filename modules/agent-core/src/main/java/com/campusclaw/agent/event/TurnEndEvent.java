package com.campusclaw.agent.event;

import java.util.List;

import com.campusclaw.ai.types.Message;
import com.campusclaw.ai.types.ToolResultMessage;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Emitted when a turn finishes with the message that ended it and any tool results produced.
 */
public record TurnEndEvent(
    @JsonProperty("message") Message message,
    @JsonProperty("toolResults") List<ToolResultMessage> toolResults
) implements AgentEvent {
}
