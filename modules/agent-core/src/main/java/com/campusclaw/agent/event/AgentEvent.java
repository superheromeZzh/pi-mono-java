package com.campusclaw.agent.event;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

/**
 * Sealed union of events emitted by the agent runtime.
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
@JsonSubTypes({
    @JsonSubTypes.Type(value = AgentStartEvent.class, name = "agent_start"),
    @JsonSubTypes.Type(value = AgentEndEvent.class, name = "agent_end"),
    @JsonSubTypes.Type(value = TurnStartEvent.class, name = "turn_start"),
    @JsonSubTypes.Type(value = TurnEndEvent.class, name = "turn_end"),
    @JsonSubTypes.Type(value = MessageStartEvent.class, name = "message_start"),
    @JsonSubTypes.Type(value = MessageUpdateEvent.class, name = "message_update"),
    @JsonSubTypes.Type(value = MessageEndEvent.class, name = "message_end"),
    @JsonSubTypes.Type(value = ToolExecutionStartEvent.class, name = "tool_execution_start"),
    @JsonSubTypes.Type(value = ToolExecutionUpdateEvent.class, name = "tool_execution_update"),
    @JsonSubTypes.Type(value = ToolExecutionEndEvent.class, name = "tool_execution_end")
})
public sealed interface AgentEvent permits
    AgentStartEvent,
    AgentEndEvent,
    TurnStartEvent,
    TurnEndEvent,
    MessageStartEvent,
    MessageUpdateEvent,
    MessageEndEvent,
    ToolExecutionStartEvent,
    ToolExecutionUpdateEvent,
    ToolExecutionEndEvent {
}
