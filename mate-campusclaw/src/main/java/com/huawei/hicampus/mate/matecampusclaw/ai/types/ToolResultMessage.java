package com.huawei.hicampus.mate.matecampusclaw.ai.types;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;

import jakarta.annotation.Nullable;

/**
 * A message carrying the result of a tool invocation.
 *
 * @param toolCallId the ID of the tool call this result responds to
 * @param toolName   the name of the tool that was called
 * @param content    the content blocks of the tool result
 * @param details    optional provider-specific detail payload
 * @param isError    whether the tool execution resulted in an error
 * @param timestamp  Unix timestamp in milliseconds
 */
public record ToolResultMessage(
    @JsonProperty("toolCallId") String toolCallId,
    @JsonProperty("toolName") String toolName,
    @JsonProperty("content") List<ContentBlock> content,
    @JsonProperty("details") @Nullable Object details,
    @JsonProperty("isError") boolean isError,
    @JsonProperty("timestamp") long timestamp
) implements Message {
}
