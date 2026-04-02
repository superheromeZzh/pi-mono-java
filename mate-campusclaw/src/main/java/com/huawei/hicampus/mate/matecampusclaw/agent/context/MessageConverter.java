package com.huawei.hicampus.mate.matecampusclaw.agent.context;

import java.util.List;

import com.huawei.hicampus.mate.matecampusclaw.ai.types.Message;

/**
 * Converts agent-managed messages into the message list sent to the LLM.
 */
@FunctionalInterface
public interface MessageConverter {

    List<Message> convert(List<Message> agentMessages);
}
