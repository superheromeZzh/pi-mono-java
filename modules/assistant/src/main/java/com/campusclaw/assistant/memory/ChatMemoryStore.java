/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.campusclaw.assistant.memory;

import java.util.List;

import com.campusclaw.ai.types.Message;

import org.springframework.stereotype.Service;

@SuppressWarnings("checkstyle:top_class_comment")
@Service
public class ChatMemoryStore {

    private final ChatMemoryRepository repository;

    @SuppressWarnings("checkstyle:java_doc_format_missing")
    public ChatMemoryStore(ChatMemoryRepository repository) {
        this.repository = repository;
    }

    @SuppressWarnings("checkstyle:java_doc_format_missing")
    public List<Message> load(String conversationId) {
        return repository.load(conversationId);
    }

    @SuppressWarnings("checkstyle:java_doc_format_missing")
    public void append(String conversationId, List<Message> messages) {
        repository.append(conversationId, messages);
    }

    @SuppressWarnings("checkstyle:java_doc_format_missing")
    public void clear(String conversationId) {
        repository.clear(conversationId);
    }
}
