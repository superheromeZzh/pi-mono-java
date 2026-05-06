package com.huawei.hicampus.mate.matecampusclaw.assistant.memory;

import java.util.List;

import com.huawei.hicampus.mate.matecampusclaw.ai.types.Message;

public interface ChatMemoryRepository {

    List<Message> load(String conversationId);

    void append(String conversationId, List<Message> messages);

    void clear(String conversationId);
}
