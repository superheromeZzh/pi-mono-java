/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.campusclaw.agent.loop;

import java.util.List;

import com.campusclaw.ai.types.Message;

/**
 * Functional interface for supplying steering or follow-up messages
 * to the agent loop. Replaces the simple MessageQueue.drain() pattern
 * with a pluggable callback.
 *
 * @version [br_eCampusCore 25.1.0_Next, 2026/05/06]
 * @since [br_eCampusCore 25.1.0_Next]
 */
@FunctionalInterface
public interface SteeringMessageSupplier {

    /**
     * Returns pending messages. Called by the agent loop after tool execution
     * (for steering) or after an end-of-turn (for follow-ups).
     *
     * @return list of pending messages, or empty list if none
     */
    List<Message> get();
}
