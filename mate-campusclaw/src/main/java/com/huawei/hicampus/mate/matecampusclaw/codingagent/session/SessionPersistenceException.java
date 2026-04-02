package com.huawei.hicampus.mate.matecampusclaw.codingagent.session;

/**
 * Thrown when session persistence operations (save/load) fail.
 */
public class SessionPersistenceException extends RuntimeException {

    public SessionPersistenceException(String message) {
        super(message);
    }

    public SessionPersistenceException(String message, Throwable cause) {
        super(message, cause);
    }
}
