/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.campusclaw.assistant.memory;

import java.util.List;

import com.campusclaw.ai.types.Message;

import org.springframework.stereotype.Service;

/**
 * Service facade over {@link ChatMemoryRepository} that exposes load/append/clear operations
 * on conversation history. Serves as the integration point for higher-level assistant
 * services that need durable chat memory.
 *
 * @version [br_eCampusCore 25.1.0_Next, 2026/05/13]
 * @since [br_eCampusCore 25.1.0_Next]
 */
@Service
public class ChatMemoryStore {

    private final ChatMemoryRepository repository;

    public ChatMemoryStore(ChatMemoryRepository repository) {
        this.repository = repository;
    }

    public List<Message> load(String conversationId) {
        return repository.load(conversationId);
    }

    public void append(String conversationId, List<Message> messages) {
        repository.append(conversationId, messages);
    }

    public void clear(String conversationId) {
        repository.clear(conversationId);
    }
}
