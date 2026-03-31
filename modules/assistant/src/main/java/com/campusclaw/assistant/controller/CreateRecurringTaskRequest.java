package com.campusclaw.assistant.controller;

public record CreateRecurringTaskRequest(
    String name,
    String description,
    String cronExpression,
    String prompt,
    String modelId
) {}
