package com.campusclaw.assistant.channel;

/**
 * Interface for submitting messages to the current interactive session.
 * Implemented by components in the CLI module that have access to the agent's submit queue.
 */
public interface MessageSubmitter {

    /**
     * Submit a message to be processed by the current session's agent.
     * Returns true if the message was successfully submitted.
     */
    boolean submitMessage(String message);
}
