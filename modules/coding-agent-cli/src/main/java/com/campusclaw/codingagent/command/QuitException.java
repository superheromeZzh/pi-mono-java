/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.campusclaw.codingagent.command;

/**
 * Signals that the user requested a graceful application exit
 * (e.g. via the /quit slash command).
 *
 * <p>This allows the CLI layer to shut down cleanly instead of
 * calling {@link System#exit(int)} from deep inside a command,
 * which avoids class-loader races during Spring Boot teardown.
 *
 * @version [br_eCampusCore 25.1.0_Next, 2026/05/06]
 * @since [br_eCampusCore 25.1.0_Next]
 */
public class QuitException extends RuntimeException {

    @SuppressWarnings("checkstyle:java_doc_format_missing")
    public QuitException() {
        super();
    }

    @SuppressWarnings("checkstyle:java_doc_format_missing")
    public QuitException(String message) {
        super(message);
    }
}
