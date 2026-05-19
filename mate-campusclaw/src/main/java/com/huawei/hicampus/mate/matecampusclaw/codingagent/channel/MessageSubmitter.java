/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.hicampus.mate.matecampusclaw.codingagent.channel;

/**
 * Interface for submitting messages to the current interactive session.
 * Implemented by components in the CLI module that have access to the agent's submit queue.
 *
 * @version [br_eCampusCore 25.1.0_Next, 2026/05/06]
 * @since [br_eCampusCore 25.1.0_Next]
 */
public interface MessageSubmitter {

    /**
     * Submit a message to be processed by the current session's agent.
     * Returns true if the message was successfully submitted.
     */
    boolean submitMessage(String message);
}
