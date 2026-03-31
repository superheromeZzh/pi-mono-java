package com.campusclaw.assistant.memory;

import java.time.LocalDateTime;

public record ChatMemoryEntity(
    Long id,
    String conversationId,
    String role,
    String content,
    int sequence,
    LocalDateTime createdAt
) {
    public ChatMemoryEntity(String conversationId, String role, String content, int sequence) {
        this(null, conversationId, role, content, sequence, null);
    }
}
