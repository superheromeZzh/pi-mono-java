package com.huawei.hicampus.mate.matecampusclaw.assistant.memory;

import com.huawei.hicampus.mate.matecampusclaw.ai.types.Message;

import java.util.List;

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
