/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.hicampus.mate.matecampusclaw.tui;

/**
 * Marks a {@link Component} as focusable, meaning it can receive keyboard input.
 * Components that implement both {@link Component} and {@link Focusable} participate
 * in the TUI focus management system.
 *
 * @version [br_eCampusCore 25.1.0_Next, 2026/05/06]
 * @since [br_eCampusCore 25.1.0_Next]
 */
public interface Focusable {

    @SuppressWarnings("checkstyle:java_doc_format_missing")
    boolean isFocused();

    @SuppressWarnings("checkstyle:java_doc_format_missing")
    void setFocused(boolean focused);

    /**
     * Checks whether a component implements both {@link Component} and {@link Focusable}.
     *
     * @param component the component to check (may be null)
     * @return true if the component is non-null and implements Focusable
     */
    static boolean isFocusable(Component component) {
        return component instanceof Focusable;
    }
}
