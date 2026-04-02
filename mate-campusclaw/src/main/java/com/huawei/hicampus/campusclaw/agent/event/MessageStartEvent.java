package com.huawei.hicampus.campusclaw.agent.event;

import com.huawei.hicampus.campusclaw.ai.types.Message;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Emitted when a message enters processing or streaming.
 */
public record MessageStartEvent(
    @JsonProperty("message") Message message
) implements AgentEvent {
}
