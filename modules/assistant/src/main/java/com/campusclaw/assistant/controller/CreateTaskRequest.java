package com.campusclaw.assistant.controller;

import jakarta.annotation.Nullable;

public record CreateTaskRequest(
    String prompt,
    @Nullable String conversationId,
    @Nullable String channelName
) {}
