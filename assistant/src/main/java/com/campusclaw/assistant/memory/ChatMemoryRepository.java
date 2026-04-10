package com.campusclaw.assistant.memory;

import com.campusclaw.ai.types.Message;

import java.util.List;

public interface ChatMemoryRepository {

    List<Message> load(String conversationId);

    void append(String conversationId, List<Message> messages);

    void clear(String conversationId);
}
