/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.campusclaw.agent.event;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Emitted when a tool starts executing.
 *
 * @version [br_eCampusCore 25.1.0_Next, 2026/05/06]
 * @since [br_eCampusCore 25.1.0_Next]
 */
public record ToolExecutionStartEvent(
        @JsonProperty("toolCallId") String toolCallId,
        @JsonProperty("toolName") String toolName,
        @JsonProperty("args") Object args)
        implements AgentEvent {}
