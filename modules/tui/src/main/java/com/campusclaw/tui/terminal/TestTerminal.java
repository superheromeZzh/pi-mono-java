package com.campusclaw.tui.terminal;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * In-memory {@link Terminal} implementation for testing.
 * <p>
 * Records all writes, tracks cursor position, and allows programmatic injection
 * of input and resize events. No real terminal I/O is performed.
 */
public class TestTerminal implements Terminal {

    private final List<String> writtenOutput = new ArrayList<>();
    private final List<Consumer<String>> inputListeners = new CopyOnWriteArrayList<>();
    private final List<Consumer<TerminalSize>> resizeListeners = new CopyOnWriteArrayList<>();
    private TerminalSize size;
    private int cursorRow;
    private int cursorCol;
    private boolean rawMode;
    private boolean closed;

    public TestTerminal() {
        this(80, 24);
    }

    public TestTerminal(int width, int height) {
        this.size = new TerminalSize(width, height);
    }

    @Override
    public void write(String data) {
        checkNotClosed();
        writtenOutput.add(data);
    }

    @Override
    public void clear() {
        checkNotClosed();
        writtenOutput.add("\033[2J\033[H");
        cursorRow = 0;
        cursorCol = 0;
    }

    @Override
    public void moveCursor(int row, int col) {
        checkNotClosed();
        cursorRow = row;
        cursorCol = col;
        writtenOutput.add("\033[" + (row + 1) + ";" + (col + 1) + "H");
    }

    @Override
    public TerminalSize getSize() {
        return size;
    }

    @Override
    public void onInput(Consumer<String> listener) {
        inputListeners.add(listener);
    }

    @Override
    public void onResize(Consumer<TerminalSize> listener) {
        resizeListeners.add(listener);
    }

    @Override
    public void enterRawMode() {
        checkNotClosed();
        rawMode = true;
    }

    @Override
    public void exitRawMode() {
        checkNotClosed();
        rawMode = false;
    }

    @Override
    public void close() {
        closed = true;
    }

    // -------------------------------------------------------------------
    // Test API: Simulate input, resize, and inspect state
    // -------------------------------------------------------------------

    /**
     * Simulates input arriving at the terminal (e.g. a keypress).
     * All registered input listeners are invoked with the given data.
     */
    public void simulateInput(String data) {
        for (Consumer<String> listener : inputListeners) {
            listener.accept(data);
        }
    }

    /**
     * Simulates a terminal resize event.
     * Updates the stored size and notifies all resize listeners.
     */
    public void simulateResize(int width, int height) {
        this.size = new TerminalSize(width, height);
        for (Consumer<TerminalSize> listener : resizeListeners) {
            listener.accept(this.size);
        }
    }

    /**
     * Returns all data that has been written to this terminal, in order.
     */
    public List<String> getWrittenOutput() {
        return Collections.unmodifiableList(writtenOutput);
    }

    /**
     * Returns all written data concatenated into a single string.
     */
    public String getFullOutput() {
        return String.join("", writtenOutput);
    }

    /**
     * Clears the recorded output buffer.
     */
    public void clearOutput() {
        writtenOutput.clear();
    }

    public int getCursorRow() {
        return cursorRow;
    }

    public int getCursorCol() {
        return cursorCol;
    }

    public boolean isRawMode() {
        return rawMode;
    }

    public boolean isClosed() {
        return closed;
    }

    private void checkNotClosed() {
        if (closed) {
            throw new IllegalStateException("Terminal is closed");
        }
    }
}
