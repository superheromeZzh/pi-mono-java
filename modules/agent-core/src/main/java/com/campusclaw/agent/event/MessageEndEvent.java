package com.campusclaw.agent.event;

import com.campusclaw.ai.types.Message;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Emitted when a message completes processing or streaming.
 */
public record MessageEndEvent(
    @JsonProperty("message") Message message
) implements AgentEvent {
}
