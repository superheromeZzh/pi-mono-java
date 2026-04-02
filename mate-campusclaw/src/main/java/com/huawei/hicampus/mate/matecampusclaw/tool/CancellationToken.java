package com.huawei.hicampus.mate.matecampusclaw.agent.tool;

import java.util.ArrayList;
import java.util.List;

/**
 * Cooperative cancellation token for interruptible tool execution.
 */
public class CancellationToken {

    private final Object lock = new Object();
    private boolean cancelled;
    private final List<Runnable> callbacks = new ArrayList<>();

    public boolean isCancelled() {
        synchronized (lock) {
            return cancelled;
        }
    }

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
