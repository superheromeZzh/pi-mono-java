package com.huawei.hicampus.mate.matecampusclaw.codingagent.command;

/**
 * Signals that the user requested a graceful application exit
 * (e.g. via the /quit slash command).
 *
 * <p>This allows the CLI layer to shut down cleanly instead of
 * calling {@link System#exit(int)} from deep inside a command,
 * which avoids class-loader races during Spring Boot teardown.
 */
public class QuitException extends RuntimeException {

    public QuitException() {
        super();
    }

    public QuitException(String message) {
        super(message);
    }
}
