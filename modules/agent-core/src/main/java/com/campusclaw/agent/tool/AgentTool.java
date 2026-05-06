/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.campusclaw.agent.tool;

import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * Executable tool contract used by the agent runtime.
 *
 * @version [br_eCampusCore 25.1.0_Next, 2026/05/06]
 * @since [br_eCampusCore 25.1.0_Next]
 */
public interface AgentTool {

    String name();

    String label();

    String description();

    JsonNode parameters();

    AgentToolResult execute(
            String toolCallId, Map<String, Object> params, CancellationToken signal, AgentToolUpdateCallback onUpdate)
            throws Exception;
}
