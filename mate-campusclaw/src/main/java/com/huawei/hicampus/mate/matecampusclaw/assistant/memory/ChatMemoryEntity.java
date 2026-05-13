/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.hicampus.mate.matecampusclaw.assistant.memory;

import java.time.LocalDateTime;

/**
 * Row representation of a persisted chat message in the {@code chat_memory} table.
 * Identified by an auto-increment id, ordered within a conversation by {@code sequence},
 * and carries the serialized message payload along with role and creation timestamp.
 *
 * @version [br_eCampusCore 25.1.0_Next, 2026/05/13]
 * @since [br_eCampusCore 25.1.0_Next]
 */
public record ChatMemoryEntity(
        Long id, String conversationId, String role, String content, int sequence, LocalDateTime createdAt) {
    public ChatMemoryEntity(String conversationId, String role, String content, int sequence) {
        this(null, conversationId, role, content, sequence, null);
    }
}
