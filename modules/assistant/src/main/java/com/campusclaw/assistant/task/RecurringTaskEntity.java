package com.campusclaw.assistant.task;

import java.time.Instant;
import java.util.List;

public record RecurringTaskEntity(
    String id,
    String name,
    String description,
    String cronExpression,
    String prompt,
    String modelId,
    String lastStatus,
    Instant lastExecutionAt,
    String executionResults
) {
    public RecurringTaskEntity(RecurringTask task, String executionResultsJson) {
        this(task.id(), task.name(), task.description(), task.cronExpression(),
             task.prompt(), task.modelId(), task.lastStatus(), task.lastExecutionAt(),
             executionResultsJson);
    }

    public RecurringTaskEntity(RecurringTask task) {
        this(task, "[]");
    }

    public RecurringTask toDomain(List<ExecutionResult> results) {
        return new RecurringTask(id, name, description, cronExpression, prompt, modelId,
            lastStatus, lastExecutionAt, results);
    }
}
