package com.huawei.hicampus.mate.matecampusclaw.tui;

import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

import com.huawei.hicampus.mate.matecampusclaw.tui.ansi.AnsiUtils;
import com.huawei.hicampus.mate.matecampusclaw.tui.component.Container;
import com.huawei.hicampus.mate.matecampusclaw.tui.terminal.Terminal;
import com.huawei.hicampus.mate.matecampusclaw.tui.terminal.TerminalSize;

/**
 * Full-screen TUI renderer. Manages a component tree, renders it to terminal lines,
 * and uses synchronized output for flicker-free display.
 *
 * <p>Rendering strategy: always clear-and-redraw the visible viewport.
 * This is simple, correct, and matches how campusclaw TS handles structural changes.
 * The synchronized output escape sequences prevent flicker.
 */
public class Tui {

    private static final String SYNC_START = "\033[?2026h";
    private static final String SYNC_END = "\033[?2026l";
    private static final String CLEAR_LINE = "\033[2K";
    private static final String HIDE_CURSOR = "\033[?25l";
    private static final String SHOW_CURSOR = "\033[?25h";
    private static final String HOME = "\033[H";
    private static final String ERASE_SCREEN = "\033[2J";
    private static final String ERASE_SCROLLBACK = "\033[3J";

    private final Terminal terminal;
    private final boolean syncOutputSupported;

    private Container root;
    private Consumer<String> inputHandler;
    private volatile boolean running = false;
    private int prevStartLine = 0;
    private int prevHeight = 0;

    public Tui(Terminal terminal) {
        this.terminal = Objects.requireNonNull(terminal);
        this.syncOutputSupported = isSyncOutputSupported();
    }

    /**
     * Detect whether the terminal supports synchronized output (DEC private mode 2026).
     * macOS Terminal.app does not support this and can crash when receiving large
     * buffered writes with unrecognized escape sequences during its rendering pipeline.
     */
    private static boolean isSyncOutputSupported() {
        String termProgram = System.getenv("TERM_PROGRAM");
        // Apple Terminal does not support synchronized output and may crash
        if ("Apple_Terminal".equals(termProgram)) {
            return false;
        }
        // Most modern terminals support it: iTerm2, kitty, WezTerm, Alacritty, etc.
        return true;
    }

    public void setRoot(Container root) {
        this.root = root;
    }

    public void setInputHandler(Consumer<String> handler) {
        this.inputHandler = handler;
    }

    /** Start the TUI. Enters raw mode, hides cursor, clears screen. */
    public void start() {
        running = true;
        terminal.enterRawMode();
        terminal.write(HIDE_CURSOR + ERASE_SCROLLBACK + ERASE_SCREEN + HOME);

        terminal.onInput(data -> {
            if (inputHandler != null) {
                inputHandler.accept(data);
            }
        });

        terminal.onResize(size -> render());
    }

    /** Stop the TUI. Shows cursor, moves below content, exits raw mode. */
    public void stop() {
        running = false;
        // Move cursor below all content and show it
        TerminalSize size = terminal.getSize();
        terminal.write("\033[" + size.height() + ";1H" + SHOW_CURSOR + "\r\n");
        terminal.exitRawMode();
    }

    /**
     * Synchronously render the component tree to the terminal.
     * Always redraws the full viewport — simple, correct, flicker-free with sync output.
     */
    public synchronized void render() {
        if (!running || root == null) return;

        TerminalSize size = terminal.getSize();
        int width = size.width();
        int height = size.height();
        if (width <= 0 || height <= 0) return;

        // Render component tree
        List<String> allLines = root.render(width);

        // Show only the last `height` lines (viewport scrolling)
        int startLine = Math.max(0, allLines.size() - height);
        int visibleCount = Math.min(allLines.size(), height);

        // Build output buffer with synchronized output
        var sb = new StringBuilder(allLines.size() * (width + 20));
        if (syncOutputSupported) {
            sb.append(SYNC_START);
        }

        // When viewport shifts down (new content pushes old off-screen),
        // scroll the terminal to push old lines into scrollback buffer.
        // This lets users scroll up in their terminal to see earlier content
        // (matching campusclaw's behavior).
        int shift = startLine - prevStartLine;
        if (shift > 0 && prevHeight == height && prevHeight > 0) {
            // Clamp shift to terminal height to avoid overwhelming the terminal
            // with an enormous number of newlines when content grows rapidly.
            int clampedShift = Math.min(shift, height);
            // Move cursor to bottom of screen, emit newlines to scroll
            sb.append("\033[").append(height).append(";1H");
            sb.append("\r\n".repeat(clampedShift));
        }

        // Use explicit cursor positioning for each row instead of \r\n.
        // This prevents line duplication when a line with CJK double-width
        // characters overflows the terminal width — \r\n would jump to the
        // wrong row after overflow, but \033[row;1H always goes to the right place.
        for (int i = 0; i < height; i++) {
            sb.append("\033[").append(i + 1).append(";1H");
            sb.append(CLEAR_LINE);
            if (i < visibleCount) {
                String line = allLines.get(startLine + i);
                if (AnsiUtils.visibleWidth(line) > width) {
                    line = AnsiUtils.sliceByColumn(line, 0, width);
                }
                sb.append(line);
            }
        }

        if (syncOutputSupported) {
            sb.append(SYNC_END);
        }
        terminal.write(sb.toString());
        prevStartLine = startLine;
        prevHeight = height;
    }

    public Terminal getTerminal() {
        return terminal;
    }
}
