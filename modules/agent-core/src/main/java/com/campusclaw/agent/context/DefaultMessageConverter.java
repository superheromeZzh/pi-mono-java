package com.campusclaw.agent.context;

import java.util.List;

import com.campusclaw.ai.types.Message;

/**
 * Default converter that passes agent messages straight through to the LLM.
 */
public class DefaultMessageConverter implements MessageConverter {

    @Override
    public List<Message> convert(List<Message> agentMessages) {
        return agentMessages;
    }
}
