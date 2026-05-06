/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.campusclaw.agent.context;

import java.util.List;
import java.util.concurrent.CompletableFuture;

import com.campusclaw.agent.tool.CancellationToken;
import com.campusclaw.ai.types.Message;

/**
 * Asynchronously transforms agent messages before they are sent to the model.
 *
 * @version [br_eCampusCore 25.1.0_Next, 2026/05/06]
 * @since [br_eCampusCore 25.1.0_Next]
 */
@FunctionalInterface
public interface ContextTransformer {

    @SuppressWarnings("checkstyle:java_doc_format_missing")
    CompletableFuture<List<Message>> transform(List<Message> messages, CancellationToken signal);
}
