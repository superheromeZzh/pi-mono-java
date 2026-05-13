/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.hicampus.mate.matecampusclaw.agent.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Default {@link Thread.UncaughtExceptionHandler} that logs uncaught exceptions through SLF4J.
 *
 * <p>Background threads created via {@code new Thread(...)} must register an uncaught exception
 * handler before being started; otherwise a thrown exception is only printed to {@code System.err}
 * and the thread terminates silently. Register {@link #INSTANCE} as the default unless a
 * thread-specific policy is required.
 *
 * @version [br_eCampusCore 25.1.0_Next, 2026/05/13]
 * @since [br_eCampusCore 25.1.0_Next]
 */
public final class LoggingUncaughtExceptionHandler implements Thread.UncaughtExceptionHandler {

    /** Shared singleton instance — register this on every directly-instantiated {@link Thread}. */
    public static final LoggingUncaughtExceptionHandler INSTANCE = new LoggingUncaughtExceptionHandler();

    private static final Logger log = LoggerFactory.getLogger(LoggingUncaughtExceptionHandler.class);

    private LoggingUncaughtExceptionHandler() {}

    @Override
    public void uncaughtException(Thread t, Throwable e) {
        log.error("Uncaught exception in thread {}", t.getName(), e);
    }
}
