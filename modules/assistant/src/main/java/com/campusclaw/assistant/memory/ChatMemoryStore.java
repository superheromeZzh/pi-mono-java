package com.campusclaw.assistant.memory;

import java.util.List;

import com.campusclaw.ai.types.Message;

import org.springframework.stereotype.Service;

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
