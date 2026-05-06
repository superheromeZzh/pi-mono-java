/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.campusclaw.agent.context;

import java.util.List;

import com.campusclaw.ai.types.Message;

/**
 * Converts agent-managed messages into the message list sent to the LLM.
 *
 * @version [br_eCampusCore 25.1.0_Next, 2026/05/06]
 * @since [br_eCampusCore 25.1.0_Next]
 */
@FunctionalInterface
public interface MessageConverter {

    List<Message> convert(List<Message> agentMessages);
}
