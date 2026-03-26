package com.mariozechner.pi.agent.context;

import com.mariozechner.pi.ai.types.Message;

import java.util.List;

/**
 * Converts agent-managed messages into the message list sent to the LLM.
 */
@FunctionalInterface
public interface MessageConverter {

    List<Message> convert(List<Message> agentMessages);
}
