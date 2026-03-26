package com.mariozechner.pi.agent.context;

import com.mariozechner.pi.ai.types.Message;

import java.util.List;

/**
 * Default converter that passes agent messages straight through to the LLM.
 */
public class DefaultMessageConverter implements MessageConverter {

    @Override
    public List<Message> convert(List<Message> agentMessages) {
        return agentMessages;
    }
}
