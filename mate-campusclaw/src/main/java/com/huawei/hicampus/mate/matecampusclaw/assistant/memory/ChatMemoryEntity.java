/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.hicampus.mate.matecampusclaw.assistant.memory;

import java.time.LocalDateTime;

@SuppressWarnings("checkstyle:top_class_comment")
public record ChatMemoryEntity(
        Long id, String conversationId, String role, String content, int sequence, LocalDateTime createdAt) {
    @SuppressWarnings("checkstyle:java_doc_format_missing")
    public ChatMemoryEntity(String conversationId, String role, String content, int sequence) {
        this(null, conversationId, role, content, sequence, null);
    }
}
