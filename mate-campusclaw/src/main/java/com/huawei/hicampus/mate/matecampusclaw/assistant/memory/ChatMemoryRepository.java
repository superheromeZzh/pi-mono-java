/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.hicampus.mate.matecampusclaw.assistant.memory;

import java.util.List;

import com.huawei.hicampus.mate.matecampusclaw.ai.types.Message;

/**
 * Persistence abstraction for conversation history. Implementations load, append, and clear
 * messages keyed by conversation id; the assistant module ships a MyBatis-backed
 * implementation but consumers may supply their own.
 *
 * @version [br_eCampusCore 25.1.0_Next, 2026/05/13]
 * @since [br_eCampusCore 25.1.0_Next]
 */
public interface ChatMemoryRepository {

    List<Message> load(String conversationId);

    void append(String conversationId, List<Message> messages);

    void clear(String conversationId);
}
