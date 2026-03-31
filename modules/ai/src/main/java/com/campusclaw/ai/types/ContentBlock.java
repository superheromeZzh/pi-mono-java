package com.campusclaw.ai.types;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

/**
 * Sealed union of content block types that compose assistant messages.
 * Each variant carries a {@code type} discriminator for polymorphic JSON serialization.
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
@JsonSubTypes({
    @JsonSubTypes.Type(value = TextContent.class, name = "text"),
    @JsonSubTypes.Type(value = ImageContent.class, name = "image"),
    @JsonSubTypes.Type(value = ThinkingContent.class, name = "thinking"),
    @JsonSubTypes.Type(value = ToolCall.class, name = "toolCall")
})
public sealed interface ContentBlock permits TextContent, ImageContent, ThinkingContent, ToolCall {
}
