package com.huawei.hicampus.campusclaw.agent.event;

import com.huawei.hicampus.campusclaw.ai.stream.AssistantMessageEvent;
import com.huawei.hicampus.campusclaw.ai.types.Message;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Emitted for incremental assistant message updates.
 */
public record MessageUpdateEvent(
    @JsonProperty("message") Message message,
    @JsonProperty("assistantMessageEvent") AssistantMessageEvent assistantMessageEvent
) implements AgentEvent {
}
