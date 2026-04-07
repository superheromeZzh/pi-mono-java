package com.campusclaw.assistant.memory;

import com.campusclaw.ai.types.AssistantMessage;
import com.campusclaw.ai.types.Message;
import com.campusclaw.ai.types.ToolResultMessage;
import com.campusclaw.ai.types.UserMessage;
import com.campusclaw.assistant.mapper.ChatMemoryMapper;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class MyBatisChatMemoryRepository implements ChatMemoryRepository {

    private final ObjectMapper objectMapper;
    private final ChatMemoryMapper mapper;

    public MyBatisChatMemoryRepository(ObjectMapper objectMapper, ChatMemoryMapper mapper) {
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper");
        this.mapper = Objects.requireNonNull(mapper, "mapper");
    }

    @Override
    public List<Message> load(String conversationId) {
        List<ChatMemoryEntity> entities = mapper.selectByConversationId(conversationId);
        List<Message> messages = new ArrayList<>(entities.size());
        for (ChatMemoryEntity entity : entities) {
            try {
                messages.add(objectMapper.readValue(entity.content(), Message.class));
            } catch (Exception e) {
                throw new UncheckedIOException(
                    new java.io.IOException("Failed to parse message for conversation " + conversationId, e));
            }
        }
        return messages;
    }

    @Override
    public void append(String conversationId, List<Message> messages) {
        Objects.requireNonNull(conversationId, "conversationId");
        Objects.requireNonNull(messages, "messages");
        if (messages.isEmpty()) return;

        List<ChatMemoryEntity> existing = mapper.selectByConversationId(conversationId);
        int nextSequence = existing.isEmpty() ? 0 : existing.getLast().sequence() + 1;

        for (int i = 0; i < messages.size(); i++) {
            Message message = messages.get(i);
            String content;
            try {
                content = objectMapper.writeValueAsString(message);
            } catch (Exception e) {
                throw new UncheckedIOException(
                    new java.io.IOException("Failed to serialize message for conversation " + conversationId, e));
            }
            String role = extractRole(message);
            mapper.insert(new ChatMemoryEntity(conversationId, role, content, nextSequence + i));
        }
    }

    @Override
    public void clear(String conversationId) {
        mapper.deleteByConversationId(conversationId);
    }

    private String extractRole(Message message) {
        return switch (message) {
            case UserMessage m -> "user";
            case AssistantMessage m -> "assistant";
            case ToolResultMessage m -> "toolResult";
        };
    }
}
