package com.huawei.hicampus.mate.matecampusclaw.ai.stream;

import com.huawei.hicampus.mate.matecampusclaw.ai.types.AssistantMessage;
import com.huawei.hicampus.mate.matecampusclaw.ai.types.StopReason;
import com.huawei.hicampus.mate.matecampusclaw.ai.types.ToolCall;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

/**
 * Sealed union of events emitted during an LLM assistant message stream.
 *
 * <p>Events follow a protocol:
 * <ol>
 *   <li>{@link StartEvent} — stream begins</li>
 *   <li>Content events (text, thinking, tool call) with start/delta/end lifecycle</li>
 *   <li>{@link DoneEvent} or {@link ErrorEvent} — stream ends</li>
 * </ol>
 *
 * <p>Uses {@code type} as the JSON discriminator for polymorphic serialization.
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
@JsonSubTypes({
    @JsonSubTypes.Type(value = AssistantMessageEvent.StartEvent.class, name = "start"),
    @JsonSubTypes.Type(value = AssistantMessageEvent.TextStartEvent.class, name = "text_start"),
    @JsonSubTypes.Type(value = AssistantMessageEvent.TextDeltaEvent.class, name = "text_delta"),
    @JsonSubTypes.Type(value = AssistantMessageEvent.TextEndEvent.class, name = "text_end"),
    @JsonSubTypes.Type(value = AssistantMessageEvent.ThinkingStartEvent.class, name = "thinking_start"),
    @JsonSubTypes.Type(value = AssistantMessageEvent.ThinkingDeltaEvent.class, name = "thinking_delta"),
    @JsonSubTypes.Type(value = AssistantMessageEvent.ThinkingEndEvent.class, name = "thinking_end"),
    @JsonSubTypes.Type(value = AssistantMessageEvent.ToolCallStartEvent.class, name = "toolcall_start"),
    @JsonSubTypes.Type(value = AssistantMessageEvent.ToolCallDeltaEvent.class, name = "toolcall_delta"),
    @JsonSubTypes.Type(value = AssistantMessageEvent.ToolCallEndEvent.class, name = "toolcall_end"),
    @JsonSubTypes.Type(value = AssistantMessageEvent.DoneEvent.class, name = "done"),
    @JsonSubTypes.Type(value = AssistantMessageEvent.ErrorEvent.class, name = "error")
})
public sealed interface AssistantMessageEvent permits
    AssistantMessageEvent.StartEvent,
    AssistantMessageEvent.TextStartEvent,
    AssistantMessageEvent.TextDeltaEvent,
    AssistantMessageEvent.TextEndEvent,
    AssistantMessageEvent.ThinkingStartEvent,
    AssistantMessageEvent.ThinkingDeltaEvent,
    AssistantMessageEvent.ThinkingEndEvent,
    AssistantMessageEvent.ToolCallStartEvent,
    AssistantMessageEvent.ToolCallDeltaEvent,
    AssistantMessageEvent.ToolCallEndEvent,
    AssistantMessageEvent.DoneEvent,
    AssistantMessageEvent.ErrorEvent {

    // --- Stream lifecycle ---

    /** Stream begins; carries the initial partial assistant message. */
    record StartEvent(
        @JsonProperty("partial") AssistantMessage partial
    ) implements AssistantMessageEvent {}

    // --- Text content events ---

    /** A new text content block begins at the given index. */
    record TextStartEvent(
        @JsonProperty("contentIndex") int contentIndex,
        @JsonProperty("partial") AssistantMessage partial
    ) implements AssistantMessageEvent {}

    /** Incremental text content delta. */
    record TextDeltaEvent(
        @JsonProperty("contentIndex") int contentIndex,
        @JsonProperty("delta") String delta,
        @JsonProperty("partial") AssistantMessage partial
    ) implements AssistantMessageEvent {}

    /** Text content block at the given index is complete. */
    record TextEndEvent(
        @JsonProperty("contentIndex") int contentIndex,
        @JsonProperty("content") String content,
        @JsonProperty("partial") AssistantMessage partial
    ) implements AssistantMessageEvent {}

    // --- Thinking content events ---

    /** A new thinking content block begins at the given index. */
    record ThinkingStartEvent(
        @JsonProperty("contentIndex") int contentIndex,
        @JsonProperty("partial") AssistantMessage partial
    ) implements AssistantMessageEvent {}

    /** Incremental thinking content delta. */
    record ThinkingDeltaEvent(
        @JsonProperty("contentIndex") int contentIndex,
        @JsonProperty("delta") String delta,
        @JsonProperty("partial") AssistantMessage partial
    ) implements AssistantMessageEvent {}

    /** Thinking content block at the given index is complete. */
    record ThinkingEndEvent(
        @JsonProperty("contentIndex") int contentIndex,
        @JsonProperty("content") String content,
        @JsonProperty("partial") AssistantMessage partial
    ) implements AssistantMessageEvent {}

    // --- Tool call events ---

    /** A new tool call content block begins at the given index. */
    record ToolCallStartEvent(
        @JsonProperty("contentIndex") int contentIndex,
        @JsonProperty("partial") AssistantMessage partial
    ) implements AssistantMessageEvent {}

    /** Incremental tool call arguments delta (JSON fragment). */
    record ToolCallDeltaEvent(
        @JsonProperty("contentIndex") int contentIndex,
        @JsonProperty("delta") String delta,
        @JsonProperty("partial") AssistantMessage partial
    ) implements AssistantMessageEvent {}

    /** Tool call at the given index is complete with the final parsed ToolCall. */
    record ToolCallEndEvent(
        @JsonProperty("contentIndex") int contentIndex,
        @JsonProperty("toolCall") ToolCall toolCall,
        @JsonProperty("partial") AssistantMessage partial
    ) implements AssistantMessageEvent {}

    // --- Completion events ---

    /**
     * Stream completed successfully.
     *
     * @param reason  the stop reason (stop, length, or toolUse)
     * @param message the final complete assistant message
     */
    record DoneEvent(
        @JsonProperty("reason") StopReason reason,
        @JsonProperty("message") AssistantMessage message
    ) implements AssistantMessageEvent {}

    /**
     * Stream ended with an error.
     *
     * @param reason the error reason ("error" or "aborted")
     * @param error  the assistant message containing error details
     */
    record ErrorEvent(
        @JsonProperty("reason") String reason,
        @JsonProperty("error") AssistantMessage error
    ) implements AssistantMessageEvent {}
}
