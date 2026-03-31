package com.campusclaw.assistant.session;

import com.campusclaw.agent.Agent;

public interface AgentSessionFactory {

    Agent create(AgentSessionConfig config);
}
