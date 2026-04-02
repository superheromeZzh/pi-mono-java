package com.huawei.hicampus.mate.matecampusclaw.agent.event;

import com.huawei.hicampus.mate.matecampusclaw.ai.types.Message;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Emitted when a message enters processing or streaming.
 */
public record MessageStartEvent(
    @JsonProperty("message") Message message
) implements AgentEvent {
}
