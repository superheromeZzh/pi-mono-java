package com.huawei.hicampus.mate.matecampusclaw.agent.tool;

import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * Executable tool contract used by the agent runtime.
 */
public interface AgentTool {

    String name();

    String label();

    String description();

    JsonNode parameters();

    AgentToolResult execute(
        String toolCallId,
        Map<String, Object> params,
        CancellationToken signal,
        AgentToolUpdateCallback onUpdate
    ) throws Exception;
}
