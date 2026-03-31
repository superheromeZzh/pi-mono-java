package com.campusclaw.agent.tool;

/**
 * Callback for streaming partial tool results while a tool is executing.
 */
@FunctionalInterface
public interface AgentToolUpdateCallback {

    void onUpdate(AgentToolResult partialResult);
}
