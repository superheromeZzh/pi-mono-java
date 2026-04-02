package com.huawei.hicampus.mate.matecampusclaw.ai.types;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * A message from the user.
 *
 * @param content   the content blocks of the message
 * @param timestamp Unix timestamp in milliseconds
 */
public record UserMessage(
    @JsonProperty("content") List<ContentBlock> content,
    @JsonProperty("timestamp") long timestamp
) implements Message {

    /** Convenience constructor that wraps a plain text string into a single TextContent block. */
    public UserMessage(String text, long timestamp) {
        this(List.of(new TextContent(text, null)), timestamp);
    }
}
