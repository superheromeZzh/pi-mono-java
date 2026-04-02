package com.huawei.hicampus.mate.matecampusclaw.agent.loop;

import java.util.List;

import com.huawei.hicampus.mate.matecampusclaw.ai.types.Message;

/**
 * Functional interface for supplying steering or follow-up messages
 * to the agent loop. Replaces the simple MessageQueue.drain() pattern
 * with a pluggable callback.
 */
@FunctionalInterface
public interface SteeringMessageSupplier {

    /**
     * Returns pending messages. Called by the agent loop after tool execution
     * (for steering) or after an end-of-turn (for follow-ups).
     *
     * @return list of pending messages, or empty list if none
     */
    List<Message> get();
}
