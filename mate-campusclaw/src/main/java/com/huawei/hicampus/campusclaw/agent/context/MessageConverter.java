package com.huawei.hicampus.campusclaw.agent.context;

import java.util.List;

import com.huawei.hicampus.campusclaw.ai.types.Message;

/**
 * Converts agent-managed messages into the message list sent to the LLM.
 */
@FunctionalInterface
public interface MessageConverter {

    List<Message> convert(List<Message> agentMessages);
}
