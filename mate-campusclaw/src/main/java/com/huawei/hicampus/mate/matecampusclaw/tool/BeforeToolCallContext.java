package com.huawei.hicampus.mate.matecampusclaw.agent.tool;

import java.util.Map;

import com.huawei.hicampus.mate.matecampusclaw.ai.types.AssistantMessage;
import com.huawei.hicampus.mate.matecampusclaw.ai.types.ToolCall;

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
