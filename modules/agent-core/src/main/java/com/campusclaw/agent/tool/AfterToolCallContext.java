/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.campusclaw.agent.tool;

import java.util.Map;

import com.campusclaw.ai.types.AssistantMessage;
import com.campusclaw.ai.types.ToolCall;

/**
 * Context passed to the after-tool-call hook.
 *
 * @version [br_eCampusCore 25.1.0_Next, 2026/05/06]
 * @since [br_eCampusCore 25.1.0_Next]
 */
public record AfterToolCallContext(
        AssistantMessage assistantMessage,
        ToolCall toolCall,
        Map<String, Object> args,
        AgentToolResult result,
        boolean isError,
        AgentContext context) {}
