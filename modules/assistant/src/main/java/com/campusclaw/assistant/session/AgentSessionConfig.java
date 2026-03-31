package com.campusclaw.assistant.session;

import com.campusclaw.agent.tool.AgentTool;
import com.campusclaw.ai.types.Model;

import java.util.List;

public record AgentSessionConfig(
    Model model,
    String systemPrompt,
    List<AgentTool> tools
) {
}
