/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.campusclaw.tui;

import java.util.List;

/**
 * Core TUI component interface. Components render themselves as an array of terminal lines
 * (strings containing ANSI escape codes). Components only care about available width;
 * vertical positioning is managed by their parent or the TUI renderer.
 *
 * @version [br_eCampusCore 25.1.0_Next, 2026/05/06]
 * @since [br_eCampusCore 25.1.0_Next]
 */
public interface Component {

    /**
     * Renders this component for the given viewport width.
     *
     * @param width the available width in terminal columns
     * @return a list of strings, each representing one line of terminal output (may contain ANSI codes)
     */
    List<String> render(int width);

    /**
     * Handles keyboard input when this component has focus.
     * Default implementation does nothing.
     *
     * @param data the raw input data (key sequence)
     */
    default void handleInput(String data) {}

    /**
     * Whether this component wants to receive key release events (Kitty keyboard protocol).
     * Default is false — release events are filtered out.
     */
    default boolean wantsKeyRelease() {
        return false;
    }

    /**
     * Invalidates any cached rendering state, forcing a full re-render on the next
     * {@link #render(int)} call. Called when theme changes or when the component
     * needs to re-render from scratch.
     */
    void invalidate();
}
