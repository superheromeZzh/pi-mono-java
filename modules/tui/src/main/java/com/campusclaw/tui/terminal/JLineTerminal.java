package com.campusclaw.tui.terminal;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

import org.jline.terminal.Attributes;
import org.jline.terminal.Attributes.LocalFlag;
import org.jline.terminal.Size;
import org.jline.terminal.TerminalBuilder;
import org.jline.utils.NonBlockingReader;

/**
 * {@link Terminal} implementation backed by JLine 3.
 * <p>
 * Supports raw mode, terminal size detection, resize events (via SIGWINCH),
 * and non-blocking input reading on a daemon thread.
 */
public class JLineTerminal implements Terminal {

    private final org.jline.terminal.Terminal jline;
    private final PrintWriter writer;
    private final List<Consumer<String>> inputListeners = new CopyOnWriteArrayList<>();
    private final List<Consumer<TerminalSize>> resizeListeners = new CopyOnWriteArrayList<>();
    private Attributes savedAttributes;
    private volatile boolean reading;
    private Thread inputThread;

    /**
     * Creates a JLineTerminal wrapping the system terminal.
     */
    public JLineTerminal() {
        try {
            this.jline = TerminalBuilder.builder()
                    .name("campusclaw-tui")
                    .system(true)
                    .jna(true)
                    .encoding(StandardCharsets.UTF_8)
                    .build();
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to build JLine terminal", e);
        }
        this.writer = jline.writer();

        // Register SIGWINCH handler for resize events
        jline.handle(org.jline.terminal.Terminal.Signal.WINCH, signal -> {
            TerminalSize size = getSize();
            for (Consumer<TerminalSize> listener : resizeListeners) {
                listener.accept(size);
            }
        });

        // Ignore SIGINT — Ctrl+C is handled as a regular character (0x03) in raw mode
        jline.handle(org.jline.terminal.Terminal.Signal.INT, signal -> {});
    }

    /**
     * Creates a JLineTerminal wrapping a pre-built JLine terminal instance.
     * Useful for testing or custom terminal configurations.
     */
    JLineTerminal(org.jline.terminal.Terminal jlineTerminal) {
        this.jline = jlineTerminal;
        this.writer = jline.writer();

        jline.handle(org.jline.terminal.Terminal.Signal.WINCH, signal -> {
            TerminalSize size = getSize();
            for (Consumer<TerminalSize> listener : resizeListeners) {
                listener.accept(size);
            }
        });

        // Ignore SIGINT — Ctrl+C is handled as a regular character (0x03) in raw mode
        jline.handle(org.jline.terminal.Terminal.Signal.INT, signal -> {});
    }

    @Override
    public void write(String data) {
        writer.print(data);
        writer.flush();
    }

    @Override
    public void clear() {
        write("\033[2J\033[H");
    }

    @Override
    public void moveCursor(int row, int col) {
        // ANSI CUP is 1-based
        write("\033[" + (row + 1) + ";" + (col + 1) + "H");
    }

    @Override
    public TerminalSize getSize() {
        Size size = jline.getSize();
        int width = size.getColumns();
        int height = size.getRows();
        // Fall back to sensible defaults if the terminal reports 0
        return new TerminalSize(
                width > 0 ? width : 80,
                height > 0 ? height : 24
        );
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
        savedAttributes = jline.getAttributes();
        jline.enterRawMode();
        // JLine's enterRawMode() doesn't disable ISIG, so Ctrl+C still generates
        // SIGINT and the 0x03 character is consumed by the OS. Disable ISIG so
        // Ctrl+C is delivered as a regular character to the input reader.
        Attributes attrs = jline.getAttributes();
        attrs.setLocalFlag(LocalFlag.ISIG, false);
        jline.setAttributes(attrs);
        startInputThread();
    }

    @Override
    public void exitRawMode() {
        stopInputThread();
        if (savedAttributes != null) {
            jline.setAttributes(savedAttributes);
            savedAttributes = null;
        }
    }

    @Override
    public void close() {
        stopInputThread();
        try {
            jline.close();
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to close JLine terminal", e);
        }
    }

    /**
     * Starts a daemon thread that reads from the terminal's non-blocking reader
     * and dispatches input to registered listeners.
     */
    private void startInputThread() {
        if (inputThread != null && inputThread.isAlive()) {
            return;
        }
        reading = true;
        inputThread = Thread.ofVirtual().name("campusclaw-tui-input").start(() -> {
            NonBlockingReader reader = jline.reader();
            StringBuilder buf = new StringBuilder();
            try {
                while (reading) {
                    int c = reader.read(100); // 100ms timeout
                    if (c == -1) {
                        break; // EOF
                    }
                    if (c == -2) {
                        continue; // Timeout, no data
                    }
                    buf.setLength(0);
                    buf.append((char) c);
                    // Drain any immediately available characters (escape sequences)
                    while (reader.peek(10) > 0) {
                        int next = reader.read();
                        if (next < 0) break;
                        buf.append((char) next);
                    }
                    String data = buf.toString();
                    for (Consumer<String> listener : inputListeners) {
                        listener.accept(data);
                    }
                }
            } catch (IOException e) {
                if (reading) {
                    // Unexpected error during reading
                    throw new UncheckedIOException("Error reading terminal input", e);
                }
                // Expected: closed during shutdown
            }
        });
    }

    private void stopInputThread() {
        reading = false;
        if (inputThread != null) {
            try {
                inputThread.join(500);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            inputThread = null;
        }
    }
}
