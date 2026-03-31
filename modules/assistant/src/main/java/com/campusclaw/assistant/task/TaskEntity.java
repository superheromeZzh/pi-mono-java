package com.campusclaw.assistant.task;

import java.time.Instant;

public record TaskEntity(
    String id,
    String conversationId,
    String prompt,
    String status,
    String result,
    String channelName,
    Instant createdAt,
    Instant updatedAt
) {
    public TaskEntity(Task task) {
        this(task.id(), task.conversationId(), task.prompt(), task.status().name(),
                task.result(), task.channelName(), task.createdAt(), task.updatedAt());
    }

    public Task toDomain() {
        return new Task(id, conversationId, prompt, TaskStatus.valueOf(status),
                result, channelName, createdAt, updatedAt);
    }
}
