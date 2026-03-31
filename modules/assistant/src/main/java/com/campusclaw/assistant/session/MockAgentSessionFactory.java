package com.campusclaw.assistant.session;

import com.campusclaw.agent.Agent;
import com.campusclaw.ai.CampusClawAiService;
import org.springframework.stereotype.Component;

@Component
public class MockAgentSessionFactory implements AgentSessionFactory {

    private final CampusClawAiService campusClawAiService;

    public MockAgentSessionFactory(CampusClawAiService campusClawAiService) {
        this.campusClawAiService = campusClawAiService;
    }

    @Override
    public Agent create(AgentSessionConfig config) {
        Agent agent = new Agent(campusClawAiService);
        agent.setModel(config.model());
        agent.setSystemPrompt(config.systemPrompt());
        agent.setTools(config.tools());
        return agent;
    }
}
