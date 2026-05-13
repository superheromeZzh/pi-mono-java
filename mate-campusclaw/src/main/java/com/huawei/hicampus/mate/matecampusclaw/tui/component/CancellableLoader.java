/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.hicampus.mate.matecampusclaw.tui.component;

import java.util.List;

import com.huawei.hicampus.mate.matecampusclaw.tui.Component;

/**
 * A loading animation with cancellation support.
 *
 * <p>Extends {@link Loader} by displaying a cancel hint (e.g. "Press Escape to cancel")
 * and tracking cancellation state. The parent component should check
 * {@link #isCancelled()} and act accordingly.
 *
 * @version [br_eCampusCore 25.1.0_Next, 2026/05/06]
 * @since [br_eCampusCore 25.1.0_Next]
 */
public class CancellableLoader implements Component {

    private final Loader loader;
    private String cancelHint;
    private volatile boolean cancelled;
    private Runnable onCancel;

    /**
     * Creates a cancellable loader with the given message.
     *
     * @param message text shown next to the spinner
     */
    public CancellableLoader(String message) {
        this(message, "Press Escape to cancel");
    }

    /**
     * Creates a cancellable loader with the given message and cancel hint.
     *
     * @param message text shown next to the spinner
     * @param cancelHint hint shown to indicate how to cancel
     */
    public CancellableLoader(String message, String cancelHint) {
        this.loader = new Loader(message);
        this.cancelHint = cancelHint;
        this.cancelled = false;
    }

    /**
     * Sets the message displayed during loading.
     *
     * @param message new spinner message
     */
    public void setMessage(String message) {
        loader.setMessage(message);
    }

    /**
     * Sets the cancel hint text.
     *
     * @param hint new cancel-hint text
     */
    public void setCancelHint(String hint) {
        this.cancelHint = hint;
    }

    /**
     * Sets a callback to run when cancellation is triggered.
     *
     * @param onCancel callback invoked on cancellation
     */
    public void setOnCancel(Runnable onCancel) {
        this.onCancel = onCancel;
    }

    /**
     * Returns whether cancellation has been requested.
     *
     * @return {@code true} when cancellation has been triggered
     */
    public boolean isCancelled() {
        return cancelled;
    }

    /**
     * Triggers cancellation.
     */
    public void cancel() {
        cancelled = true;
        if (onCancel != null) {
            onCancel.run();
        }
    }

    /**
     * Resets the cancellation state for reuse.
     */
    public void reset() {
        cancelled = false;
        loader.invalidate();
    }

    @Override
    public List<String> render(int width) {
        if (cancelled) {
            return List.of("Cancelled.");
        }

        var lines = loader.render(width);
        if (cancelHint != null && !cancelHint.isEmpty()) {
            String hint = "\033[90m" + cancelHint + "\033[0m"; // dim gray
            return List.of(lines.isEmpty() ? "" : lines.get(0), hint);
        }
        return lines;
    }

    @Override
    public void handleInput(String data) {
        // Escape key
        if ("\033".equals(data) || "\u001b".equals(data)) {
            cancel();
        }
    }

    @Override
    public void invalidate() {
        loader.invalidate();
        cancelled = false;
    }
}
