package com.huawei.hicampus.mate.matecampusclaw.agent.context;

import java.util.List;

import com.huawei.hicampus.mate.matecampusclaw.ai.types.Message;

/**
 * Default converter that passes agent messages straight through to the LLM.
 */
public class DefaultMessageConverter implements MessageConverter {

    @Override
    public List<Message> convert(List<Message> agentMessages) {
        return agentMessages;
    }
}
