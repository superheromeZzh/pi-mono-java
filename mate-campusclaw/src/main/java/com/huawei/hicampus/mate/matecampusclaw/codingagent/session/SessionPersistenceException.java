/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.hicampus.mate.matecampusclaw.codingagent.session;

/**
 * Thrown when session persistence operations (save/load) fail.
 *
 * @version [br_eCampusCore 25.1.0_Next, 2026/05/06]
 * @since [br_eCampusCore 25.1.0_Next]
 */
public class SessionPersistenceException extends RuntimeException {

    public SessionPersistenceException(String message) {
        super(message);
    }

    public SessionPersistenceException(String message, Throwable cause) {
        super(message, cause);
    }
}
