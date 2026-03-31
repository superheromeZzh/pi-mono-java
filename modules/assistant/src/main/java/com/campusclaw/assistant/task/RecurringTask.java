package com.campusclaw.assistant.task;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;
import java.util.List;

public record RecurringTask(
    @JsonProperty("id") String id,
    @JsonProperty("name") String name,
    @JsonProperty("description") String description,
    @JsonProperty("cronExpression") String cronExpression,
    @JsonProperty("prompt") String prompt,
    @JsonProperty("modelId") String modelId,
    @JsonProperty("lastStatus") String lastStatus,
    @JsonProperty("lastExecutionAt") Instant lastExecutionAt,
    @JsonProperty("executionResults") List<ExecutionResult> executionResults
) {
    public RecurringTask(
        String id, String name, String description, String cronExpression,
        String prompt, String modelId
    ) {
        this(id, name, description, cronExpression, prompt, modelId, null, null, null);
    }
}
