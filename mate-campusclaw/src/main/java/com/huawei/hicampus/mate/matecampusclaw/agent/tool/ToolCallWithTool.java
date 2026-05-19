/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.hicampus.mate.matecampusclaw.agent.tool;

import java.util.Map;

import com.huawei.hicampus.mate.matecampusclaw.ai.types.ToolCall;

/**
 * Bundles a resolved tool implementation with the tool call to execute and its validated arguments.
 *
 * @version [br_eCampusCore 25.1.0_Next, 2026/05/06]
 * @since [br_eCampusCore 25.1.0_Next]
 */
public record ToolCallWithTool(ToolCall toolCall, AgentTool tool, Map<String, Object> validatedArgs) {}
