package com.campusclaw.agent.tool;

import java.util.Map;

import com.campusclaw.ai.types.AssistantMessage;
import com.campusclaw.ai.types.ToolCall;

/**
 * Context passed to the before-tool-call hook.
 */
public record BeforeToolCallContext(
    AssistantMessage assistantMessage,
    ToolCall toolCall,
    Map<String, Object> args,
    AgentContext context
) {
}
