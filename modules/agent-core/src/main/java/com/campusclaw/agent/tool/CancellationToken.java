/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.campusclaw.agent.tool;

import java.util.ArrayList;
import java.util.List;

/**
 * Cooperative cancellation token for interruptible tool execution.
 *
 * @version [br_eCampusCore 25.1.0_Next, 2026/05/06]
 * @since [br_eCampusCore 25.1.0_Next]
 */
public class CancellationToken {

    private final Object lock = new Object();
    private boolean cancelled;
    private final List<Runnable> callbacks = new ArrayList<>();

    @SuppressWarnings("checkstyle:java_doc_format_missing")
    public boolean isCancelled() {
        synchronized (lock) {
            return cancelled;
        }
    }

    @SuppressWarnings("checkstyle:java_doc_format_missing")
    public void cancel() {
        List<Runnable> callbacksToRun;
        synchronized (lock) {
            if (cancelled) {
                return;
            }

            cancelled = true;
            callbacksToRun = List.copyOf(callbacks);
            callbacks.clear();
        }

        for (var callback : callbacksToRun) {
            callback.run();
        }
    }

    @SuppressWarnings("checkstyle:java_doc_format_missing")
    public void onCancel(Runnable callback) {
        boolean runImmediately = false;
        synchronized (lock) {
            if (cancelled) {
                runImmediately = true;
            } else {
                callbacks.add(callback);
            }
        }

        if (runImmediately) {
            callback.run();
        }
    }
}
