package com.campusclaw.ai.types;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

/**
 * Sealed union of message types in a conversation.
 * Each variant carries a {@code role} discriminator for polymorphic JSON serialization.
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "role")
@JsonSubTypes({
    @JsonSubTypes.Type(value = UserMessage.class, name = "user"),
    @JsonSubTypes.Type(value = AssistantMessage.class, name = "assistant"),
    @JsonSubTypes.Type(value = ToolResultMessage.class, name = "toolResult")
})
public sealed interface Message permits UserMessage, AssistantMessage, ToolResultMessage {
}
