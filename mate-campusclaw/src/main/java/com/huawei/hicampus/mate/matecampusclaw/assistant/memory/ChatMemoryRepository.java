/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.hicampus.mate.matecampusclaw.assistant.memory;

import java.util.List;

import com.huawei.hicampus.mate.matecampusclaw.ai.types.Message;

@SuppressWarnings("checkstyle:top_class_comment")
public interface ChatMemoryRepository {

    @SuppressWarnings("checkstyle:java_doc_format_missing")
    List<Message> load(String conversationId);

    @SuppressWarnings("checkstyle:java_doc_format_missing")
    void append(String conversationId, List<Message> messages);

    @SuppressWarnings("checkstyle:java_doc_format_missing")
    void clear(String conversationId);
}
