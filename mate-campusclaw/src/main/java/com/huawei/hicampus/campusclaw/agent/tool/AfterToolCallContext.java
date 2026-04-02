package com.huawei.hicampus.campusclaw.agent.tool;

import java.util.Map;

import com.huawei.hicampus.campusclaw.ai.types.AssistantMessage;
import com.huawei.hicampus.campusclaw.ai.types.ToolCall;

/**
 * Context passed to the after-tool-call hook.
 */
public record AfterToolCallContext(
    AssistantMessage assistantMessage,
    ToolCall toolCall,
    Map<String, Object> args,
    AgentToolResult result,
    boolean isError,
    AgentContext context
) {
}
