package com.campusclaw.agent.state;

import java.util.List;
import java.util.Set;

import com.campusclaw.agent.tool.AgentTool;
import com.campusclaw.ai.types.Message;
import com.campusclaw.ai.types.Model;
import com.campusclaw.ai.types.ThinkingLevel;

/**
 * Immutable snapshot of the current agent state.
 */
public record AgentStateSnapshot(
    String systemPrompt,
    Model model,
    ThinkingLevel thinkingLevel,
    List<AgentTool> tools,
    List<Message> messages,
    boolean streaming,
    Message streamMessage,
    Set<String> pendingToolCalls,
    String error
) {

    public AgentStateSnapshot {
        tools = List.copyOf(tools);
        messages = List.copyOf(messages);
        pendingToolCalls = Set.copyOf(pendingToolCalls);
    }
}
