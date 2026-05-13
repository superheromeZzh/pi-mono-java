/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.campusclaw.tui.terminal;

import java.util.function.Consumer;

/**
 * Abstraction over a terminal device. Provides raw I/O, cursor control,
 * size detection, and input handling. Implementations include a real
 * JLine-backed terminal and an in-memory test terminal.
 *
 * @version [br_eCampusCore 25.1.0_Next, 2026/05/06]
 * @since [br_eCampusCore 25.1.0_Next]
 */
public interface Terminal {

    /**
     * Writes raw data (text and ANSI escape sequences) to the terminal output.
     *
     * @param data raw bytes / ANSI sequences to emit
     */
    void write(String data);

    /**
     * Clears the entire terminal screen and moves the cursor to the home position (0, 0).
     */
    void clear();

    /**
     * Moves the cursor to the specified row and column (0-based).
     * Uses the ANSI CUP (Cursor Position) escape sequence.
     *
     * @param row zero-based row index
     * @param col zero-based column index
     */
    void moveCursor(int row, int col);

    /**
     * Returns the current terminal dimensions.
     *
     * @return the current {@link TerminalSize}
     */
    TerminalSize getSize();

    /**
     * Registers a listener that is called when input data arrives.
     * The listener receives raw key sequences (may include ANSI escape sequences).
     * Multiple listeners can be registered; all are invoked in registration order.
     *
     * @param listener callback invoked with each raw input chunk
     */
    void onInput(Consumer<String> listener);

    /**
     * Registers a listener that is called when the terminal is resized.
     *
     * @param listener callback invoked with the new terminal size on every resize
     */
    void onResize(Consumer<TerminalSize> listener);

    /**
     * Enters raw mode: disables line buffering, echo, and signal processing
     * so that individual keystrokes are delivered immediately.
     */
    void enterRawMode();

    /**
     * Exits raw mode and restores the terminal to its previous state.
     */
    void exitRawMode();

    /**
     * Removes all registered input and resize listeners.
     */
    void clearListeners();

    /**
     * Releases all resources held by this terminal (streams, native handles, etc.).
     */
    void close();
}
