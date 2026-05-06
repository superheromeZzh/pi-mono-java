/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.campusclaw.agent.event;

import java.util.List;

import com.campusclaw.ai.types.Message;
import com.campusclaw.ai.types.ToolResultMessage;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Emitted when a turn finishes with the message that ended it and any tool results produced.
 *
 * @version [br_eCampusCore 25.1.0_Next, 2026/05/06]
 * @since [br_eCampusCore 25.1.0_Next]
 */
public record TurnEndEvent(
        @JsonProperty("message") Message message, @JsonProperty("toolResults") List<ToolResultMessage> toolResults)
        implements AgentEvent {}
