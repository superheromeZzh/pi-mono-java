/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.campusclaw.agent.state;

import java.util.List;
import java.util.Set;

import com.campusclaw.agent.tool.AgentTool;
import com.campusclaw.ai.types.Message;
import com.campusclaw.ai.types.Model;
import com.campusclaw.ai.types.ThinkingLevel;

/**
 * Immutable snapshot of the current agent state.
 *
 * @version [br_eCampusCore 25.1.0_Next, 2026/05/06]
 * @since [br_eCampusCore 25.1.0_Next]
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
        String error) {

    @SuppressWarnings("checkstyle:java_doc_format_missing")
    public AgentStateSnapshot {
        tools = List.copyOf(tools);
        messages = List.copyOf(messages);
        pendingToolCalls = Set.copyOf(pendingToolCalls);
    }
}
