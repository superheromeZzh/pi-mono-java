package com.huawei.hicampus.mate.matecampusclaw.agent.event;

import java.util.List;

import com.huawei.hicampus.mate.matecampusclaw.ai.types.Message;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Emitted when an agent run ends with the final message history.
 */
public record AgentEndEvent(
    @JsonProperty("messages") List<Message> messages
) implements AgentEvent {
}
