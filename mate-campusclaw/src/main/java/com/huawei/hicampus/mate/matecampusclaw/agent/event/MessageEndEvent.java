package com.huawei.hicampus.mate.matecampusclaw.agent.event;

import com.huawei.hicampus.mate.matecampusclaw.ai.types.Message;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Emitted when a message completes processing or streaming.
 */
public record MessageEndEvent(
    @JsonProperty("message") Message message
) implements AgentEvent {
}
