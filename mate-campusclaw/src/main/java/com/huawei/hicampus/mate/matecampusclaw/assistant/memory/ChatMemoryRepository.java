package com.huawei.hicampus.mate.matecampusclaw.assistant.memory;

import com.huawei.hicampus.mate.matecampusclaw.ai.types.Message;

import java.util.List;

public interface ChatMemoryRepository {

    List<Message> load(String conversationId);

    void append(String conversationId, List<Message> messages);

    void clear(String conversationId);
}
