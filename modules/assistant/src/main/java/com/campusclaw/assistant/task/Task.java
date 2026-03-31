package com.campusclaw.assistant.task;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.annotation.Nullable;

import java.time.Instant;
import java.util.Objects;
import java.util.Set;

public record Task(
    @JsonProperty("id") String id,
    @JsonProperty("conversationId") String conversationId,
    @JsonProperty("prompt") String prompt,
    @JsonProperty("status") TaskStatus status,
    @JsonProperty("result") @Nullable String result,
    @JsonProperty("channelName") @Nullable String channelName,
    @JsonProperty("createdAt") Instant createdAt,
    @JsonProperty("updatedAt") Instant updatedAt
) {

    private static final Set<TaskStatus> VALID_TRANSITIONS_TO_IN_PROGRESS = Set.of(TaskStatus.TODO, TaskStatus.AWAITING_HUMAN_INPUT);
    private static final Set<TaskStatus> VALID_TRANSITIONS_TO_COMPLETED = Set.of(TaskStatus.IN_PROGRESS);
    private static final Set<TaskStatus> VALID_TRANSITIONS_TO_FAILED = Set.of(TaskStatus.IN_PROGRESS);
    private static final Set<TaskStatus> VALID_TRANSITIONS_TO_AWAITING = Set.of(TaskStatus.IN_PROGRESS);

    public Task {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(conversationId, "conversationId");
        Objects.requireNonNull(prompt, "prompt");
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(createdAt, "createdAt");
        Objects.requireNonNull(updatedAt, "updatedAt");
    }

    public Task transitionTo(TaskStatus newStatus) {
        validateTransition(status, newStatus);
        Instant now = Instant.now();
        return new Task(id, conversationId, prompt, newStatus, result, channelName, createdAt, now);
    }

    public Task withResult(String result) {
        return new Task(id, conversationId, prompt, status, result, channelName, createdAt, Instant.now());
    }

    private static void validateTransition(TaskStatus from, TaskStatus to) {
        boolean valid = switch (to) {
            case IN_PROGRESS -> VALID_TRANSITIONS_TO_IN_PROGRESS.contains(from);
            case COMPLETED -> VALID_TRANSITIONS_TO_COMPLETED.contains(from);
            case FAILED -> VALID_TRANSITIONS_TO_FAILED.contains(from);
            case AWAITING_HUMAN_INPUT -> VALID_TRANSITIONS_TO_AWAITING.contains(from);
            case TODO -> false;
        };
        if (!valid) {
            throw new IllegalStateException("Cannot transition from " + from + " to " + to);
        }
    }
}
