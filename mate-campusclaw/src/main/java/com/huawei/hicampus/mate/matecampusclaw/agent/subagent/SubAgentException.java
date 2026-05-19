/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.hicampus.mate.matecampusclaw.agent.subagent;

/**
 * Thrown when a sub-agent backend fails to open, prompt, or close a session.
 *
 * @version [br_eCampusCore 25.1.0_Next, 2026/05/12]
 * @since [br_eCampusCore 25.1.0_Next]
 */
public class SubAgentException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final String code;

    public SubAgentException(String code, String message) {
        super(message);
        this.code = code;
    }

    public SubAgentException(String code, String message, Throwable cause) {
        super(message, cause);
        this.code = code;
    }

    public String code() {
        return code;
    }
}
