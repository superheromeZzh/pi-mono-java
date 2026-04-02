package com.huawei.hicampus.mate.matecampusclaw.agent.tool;

import java.util.Map;

import com.huawei.hicampus.mate.matecampusclaw.ai.types.ToolCall;

/**
 * Bundles a resolved tool implementation with the tool call to execute and its validated arguments.
 */
public record ToolCallWithTool(
    ToolCall toolCall,
    AgentTool tool,
    Map<String, Object> validatedArgs
) {
}
