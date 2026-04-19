package com.campusclaw.tui;

import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

import com.campusclaw.tui.ansi.AnsiUtils;
import com.campusclaw.tui.component.Container;
import com.campusclaw.tui.terminal.Terminal;
import com.campusclaw.tui.terminal.TerminalSize;

/**
 * Full-screen TUI renderer. Manages a component tree, renders it to terminal lines,
 * and uses synchronized output for flicker-free display.
 *
 * <p>Rendering strategy: differential rendering with relative cursor movement.
 * Only lines that actually changed are rewritten; when content grows past the
 * viewport, a single burst of {@code \r\n} pushes the old lines into the terminal's
 * scrollback so the user can still scroll up. A full redraw is issued only on the
 * first frame, on width/height changes, or when content shrinks below the
 * high-water mark.
 *
 * <p>This approach mirrors the reference TypeScript implementation and avoids
 * flooding the terminal with absolute cursor positioning + clear-line sequences
 * on every frame — which, on macOS Terminal.app, triggers the
 * {@code NSPersistentUIManager} heap-corruption bug when window state is
 * serialized.
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

    // Differential render state (all in buffer coordinates — row 0 is the topmost
    // line ever rendered for the current session, never re-numbered after scroll).
    private List<String> previousLines = Collections.emptyList();
    private int hardwareCursorRow = 0;
    private int previousViewportTop = 0;
    private int previousWidth = 0;
    private int previousHeight = 0;
    private int maxLinesRendered = 0;

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
        terminal.clearListeners();
        terminal.enterRawMode();
        terminal.write(HIDE_CURSOR + ERASE_SCROLLBACK + ERASE_SCREEN + HOME);

        // Reset differential state so the first render is a clean append from
        // the top of an (assumed-empty) viewport.
        previousLines = Collections.emptyList();
        hardwareCursorRow = 0;
        previousViewportTop = 0;
        previousWidth = 0;
        previousHeight = 0;
        maxLinesRendered = 0;

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
     * Synchronously render the component tree to the terminal using a
     * differential strategy: only lines that changed are rewritten, using
     * relative cursor movement. When the rendered content exceeds the viewport,
     * a single burst of {@code \r\n} at the bottom lets the terminal scroll
     * old content into scrollback (preserving history for the user).
     */
    public synchronized void render() {
        if (!running || root == null) { return; }

        TerminalSize size = terminal.getSize();
        int width = size.width();
        int height = size.height();
        if (width <= 0 || height <= 0) { return; }

        List<String> newLines = root.render(width);

        boolean widthChanged = previousWidth != 0 && previousWidth != width;
        boolean heightChanged = previousHeight != 0 && previousHeight != height;

        // First frame — output directly; start() already cleared the screen.
        if (previousLines.isEmpty() && !widthChanged && !heightChanged) {
            fullRender(newLines, width, height, false);
            return;
        }

        // Width change invalidates line wrapping; height change invalidates the
        // viewport alignment. Both require a full clear + redraw.
        if (widthChanged || heightChanged) {
            fullRender(newLines, width, height, true);
            return;
        }

        // Content shrunk below the high-water mark: old rows would linger as
        // orphans, so do a full clear + redraw.
        if (newLines.size() < maxLinesRendered) {
            fullRender(newLines, width, height, true);
            return;
        }

        // Find first and last changed lines.
        int firstChanged = -1;
        int lastChanged = -1;
        int maxLen = Math.max(newLines.size(), previousLines.size());
        for (int i = 0; i < maxLen; i++) {
            String oldLine = i < previousLines.size() ? previousLines.get(i) : "";
            String newLine = i < newLines.size() ? newLines.get(i) : "";
            if (!oldLine.equals(newLine)) {
                if (firstChanged == -1) { firstChanged = i; }
                lastChanged = i;
            }
        }
        boolean appendedLines = newLines.size() > previousLines.size();
        if (appendedLines) {
            if (firstChanged == -1) { firstChanged = previousLines.size(); }
            lastChanged = newLines.size() - 1;
        }
        boolean appendStart = appendedLines
                && firstChanged == previousLines.size()
                && firstChanged > 0;

        // Nothing changed.
        if (firstChanged == -1) {
            return;
        }

        // Change is above the current viewport (scrolled out of reach): we
        // can't reach it with relative movement, so full redraw.
        if (firstChanged < previousViewportTop) {
            fullRender(newLines, width, height, true);
            return;
        }

        int viewportTop = previousViewportTop;
        int hwCursor = hardwareCursorRow;
        StringBuilder sb = new StringBuilder();
        if (syncOutputSupported) { sb.append(SYNC_START); }

        // If the first line we need to rewrite sits below the current viewport,
        // scroll the terminal down — this is the ONLY place where lines get
        // pushed into the terminal's scrollback.
        int prevViewportBottom = viewportTop + height - 1;
        int moveTargetRow = appendStart ? firstChanged - 1 : firstChanged;
        if (moveTargetRow > prevViewportBottom) {
            int currentScreenRow = Math.max(0, Math.min(height - 1, hwCursor - viewportTop));
            int moveToBottom = height - 1 - currentScreenRow;
            if (moveToBottom > 0) { sb.append("\033[").append(moveToBottom).append('B'); }
            int scroll = moveTargetRow - prevViewportBottom;
            sb.append("\r\n".repeat(scroll));
            viewportTop += scroll;
            hwCursor = moveTargetRow;
        }

        // Relative cursor move to the first line we need to rewrite.
        int targetScreenRow = moveTargetRow - viewportTop;
        int currentScreenRow = hwCursor - viewportTop;
        int lineDiff = targetScreenRow - currentScreenRow;
        if (lineDiff > 0) { sb.append("\033[").append(lineDiff).append('B'); }
        else if (lineDiff < 0) { sb.append("\033[").append(-lineDiff).append('A'); }

        // Column 0. For an append-at-end case we also step down one row.
        sb.append(appendStart ? "\r\n" : "\r");

        // Rewrite only the changed range [firstChanged .. lastChanged].
        int renderEnd = Math.min(lastChanged, newLines.size() - 1);
        for (int i = firstChanged; i <= renderEnd; i++) {
            if (i > firstChanged) { sb.append("\r\n"); }
            sb.append(CLEAR_LINE);
            String line = newLines.get(i);
            if (AnsiUtils.visibleWidth(line) > width) {
                line = AnsiUtils.sliceByColumn(line, 0, width);
            }
            sb.append(line);
        }

        if (syncOutputSupported) { sb.append(SYNC_END); }
        terminal.write(sb.toString());

        hardwareCursorRow = renderEnd;
        previousLines = newLines;
        previousWidth = width;
        previousHeight = height;
        previousViewportTop = Math.max(viewportTop, renderEnd - height + 1);
        maxLinesRendered = Math.max(maxLinesRendered, newLines.size());
    }

    /**
     * Emit every line of the current frame, optionally preceded by a full
     * clear (used for width/height changes and shrink-below-high-water-mark).
     * On the first frame {@code clear} is false — {@link #start()} has already
     * cleared the screen.
     */
    private void fullRender(List<String> newLines, int width, int height, boolean clear) {
        StringBuilder sb = new StringBuilder();
        if (syncOutputSupported) { sb.append(SYNC_START); }
        if (clear) { sb.append(ERASE_SCREEN).append(HOME).append(ERASE_SCROLLBACK); }
        for (int i = 0; i < newLines.size(); i++) {
            if (i > 0) { sb.append("\r\n"); }
            String line = newLines.get(i);
            if (AnsiUtils.visibleWidth(line) > width) {
                line = AnsiUtils.sliceByColumn(line, 0, width);
            }
            sb.append(line);
        }
        if (syncOutputSupported) { sb.append(SYNC_END); }
        terminal.write(sb.toString());

        hardwareCursorRow = Math.max(0, newLines.size() - 1);
        if (clear) {
            maxLinesRendered = newLines.size();
        } else {
            maxLinesRendered = Math.max(maxLinesRendered, newLines.size());
        }
        int bufferLength = Math.max(height, newLines.size());
        previousViewportTop = Math.max(0, bufferLength - height);
        previousLines = newLines;
        previousWidth = width;
        previousHeight = height;
    }

    public Terminal getTerminal() {
        return terminal;
    }
}
