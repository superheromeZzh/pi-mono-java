/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.campusclaw.agent.event;

import java.util.List;

import com.campusclaw.ai.types.Message;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Emitted when an agent run ends with the final message history.
 *
 * @version [br_eCampusCore 25.1.0_Next, 2026/05/06]
 * @since [br_eCampusCore 25.1.0_Next]
 */
public record AgentEndEvent(@JsonProperty("messages") List<Message> messages) implements AgentEvent {}
