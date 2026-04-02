package com.huawei.hicampus.mate.matecampusclaw.tui.component;

import java.text.BreakIterator;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import com.huawei.hicampus.mate.matecampusclaw.tui.Component;
import com.huawei.hicampus.mate.matecampusclaw.tui.Focusable;
import com.huawei.hicampus.mate.matecampusclaw.tui.ansi.AnsiUtils;

/**
 * Multi-line text editor component with word wrapping, undo/redo, and Emacs-style kill ring.
 * <p>
 * Supported operations:
 * <ul>
 *   <li>Arrow keys — cursor movement (left, right, up, down)</li>
 *   <li>Home / Ctrl+A — move to line start</li>
 *   <li>End / Ctrl+E — move to line end</li>
 *   <li>Backspace — delete character backward</li>
 *   <li>Delete — delete character forward</li>
 *   <li>Enter — insert new line</li>
 *   <li>Ctrl+K — kill to end of line (push to kill ring)</li>
 *   <li>Ctrl+U — kill to start of line (push to kill ring)</li>
 *   <li>Ctrl+Y — yank (paste from kill ring)</li>
 *   <li>Alt+Y — yank-pop (cycle kill ring)</li>
 *   <li>Ctrl+Z — undo</li>
 *   <li>Ctrl+Shift+Z — redo (not yet implemented, placeholder)</li>
 *   <li>Ctrl+W — delete word backward</li>
 *   <li>Alt+D — delete word forward</li>
 *   <li>Alt+Left / Ctrl+Left — move word backward</li>
 *   <li>Alt+Right / Ctrl+Right — move word forward</li>
 * </ul>
 */
public class Editor implements Component, Focusable {

    // --- ANSI key sequences ---
    private static final String KEY_UP = "\033[A";
    private static final String KEY_DOWN = "\033[B";
    private static final String KEY_RIGHT = "\033[C";
    private static final String KEY_LEFT = "\033[D";
    private static final String KEY_HOME = "\033[H";
    private static final String KEY_END = "\033[F";
    private static final String KEY_DELETE = "\033[3~";
    private static final String KEY_BACKSPACE = "\177";  // DEL
    private static final String KEY_BACKSPACE_BS = "\010"; // BS
    private static final String KEY_ENTER = "\r";
    private static final String KEY_NEWLINE = "\n";

    // Ctrl keys
    private static final String KEY_CTRL_A = "\001";
    private static final String KEY_CTRL_B = "\002";
    private static final String KEY_CTRL_E = "\005";
    private static final String KEY_CTRL_F = "\006";
    private static final String KEY_CTRL_K = "\013";
    private static final String KEY_CTRL_U = "\025";
    private static final String KEY_CTRL_W = "\027";
    private static final String KEY_CTRL_Y = "\031";
    private static final String KEY_CTRL_Z = "\032";

    // Alt key sequences
    private static final String KEY_ALT_LEFT = "\033[1;3D";
    private static final String KEY_CTRL_LEFT = "\033[1;5D";
    private static final String KEY_ALT_RIGHT = "\033[1;3C";
    private static final String KEY_CTRL_RIGHT = "\033[1;5C";
    private static final String KEY_ALT_B = "\033b";
    private static final String KEY_ALT_F = "\033f";
    private static final String KEY_ALT_D = "\033d";
    private static final String KEY_ALT_Y = "\033y";

    // Shift+Enter / Alt+Enter sequences for newline in submit mode
    private static final String KEY_SHIFT_ENTER_KITTY = "\033[13;2u";
    private static final String KEY_ALT_ENTER = "\033\r";
    private static final String KEY_ALT_NEWLINE = "\033\n";

    // Internal state
    private List<String> lines;
    private int cursorLine;
    private int cursorCol;
    private boolean focused;

    // Submit mode: when true, Enter submits and Shift+Enter/Alt+Enter inserts newline
    private boolean submitOnEnter;

    // Undo/Redo
    private final UndoStack<EditorSnapshot> undoStack;

    // Kill ring
    private final KillRing killRing = new KillRing();
    private String lastAction; // "kill", "yank", "type-word", or null

    // Preferred visual column for vertical movement (sticky column)
    private int preferredVisualCol = -1;

    // Last render width for word-wrap calculations
    private int lastWidth = 80;

    // History
    private final List<String> history = new ArrayList<>();
    private int historyIndex = -1; // -1 = current (not browsing history)
    private String savedCurrentText = ""; // text before entering history
    private static final int MAX_HISTORY = 100;

    // Placeholder text when empty
    private String placeholder;

    // Callbacks
    private Consumer<String> onChange;
    private Consumer<String> onSubmit;

    public Editor() {
        this("");
    }

    public Editor(String initialText) {
        this.lines = new ArrayList<>();
        this.undoStack = new UndoStack<>(this::cloneSnapshot);
        setText(initialText != null ? initialText : "");
    }

    // -------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------

    /** Returns the full text content (lines joined with \n). */
    public String getText() {
        return String.join("\n", lines);
    }

    /** Sets the text content, resetting cursor to the end. */
    public void setText(String text) {
        String normalized = normalizeText(text != null ? text : "");
        String[] split = normalized.split("\n", -1);
        lines = new ArrayList<>();
        for (String s : split) {
            lines.add(s);
        }
        if (lines.isEmpty()) lines.add("");
        cursorLine = lines.size() - 1;
        cursorCol = lines.get(cursorLine).length();
        preferredVisualCol = -1;
        lastAction = null;
    }

    /** Returns the current cursor position as [line, column]. */
    public int[] getCursorPosition() {
        return new int[]{cursorLine, cursorCol};
    }

    /** Sets the cursor position, clamped to valid range. */
    public void setCursorPosition(int line, int col) {
        cursorLine = Math.max(0, Math.min(line, lines.size() - 1));
        cursorCol = Math.max(0, Math.min(col, lines.get(cursorLine).length()));
        preferredVisualCol = -1;
    }

    /** Returns the lines (defensive copy). */
    public List<String> getLines() {
        return new ArrayList<>(lines);
    }

    public void setOnChange(Consumer<String> onChange) {
        this.onChange = onChange;
    }

    public void setOnSubmit(Consumer<String> onSubmit) {
        this.onSubmit = onSubmit;
    }

    /** When true, Enter submits and Shift+Enter/Alt+Enter inserts newline. */
    public void setSubmitOnEnter(boolean submitOnEnter) {
        this.submitOnEnter = submitOnEnter;
    }

    public void setPlaceholder(String placeholder) {
        this.placeholder = placeholder;
    }

    /** Adds an entry to the command history. Deduplicates consecutive entries. */
    public void addToHistory(String text) {
        if (text == null || text.isBlank()) return;
        if (!history.isEmpty() && history.getLast().equals(text)) return;
        history.add(text);
        if (history.size() > MAX_HISTORY) history.removeFirst();
        historyIndex = -1;
    }

    // -------------------------------------------------------------------
    // Component interface
    // -------------------------------------------------------------------

    @Override
    public void invalidate() {
        // No cached rendering state — always renders fresh
    }

    @Override
    public List<String> render(int width) {
        int contentWidth = Math.max(1, width);
        lastWidth = contentWidth;

        // Show placeholder when empty and not focused
        if (getText().isEmpty() && placeholder != null && !placeholder.isEmpty() && !focused) {
            String ph = "\033[2m" + placeholder + "\033[0m";
            int visLen = AnsiUtils.visibleWidth(ph);
            int pad = Math.max(0, contentWidth - visLen);
            return List.of(ph + " ".repeat(pad));
        }

        List<LayoutLine> layoutLines = layoutText(contentWidth);

        List<String> result = new ArrayList<>();

        // Render each layout line
        for (LayoutLine ll : layoutLines) {
            StringBuilder sb = new StringBuilder();
            String displayText = ll.text;

            if (focused && ll.hasCursor && ll.cursorPos >= 0) {
                String before = displayText.substring(0, Math.min(ll.cursorPos, displayText.length()));
                String after = ll.cursorPos < displayText.length()
                        ? displayText.substring(ll.cursorPos) : "";

                if (!after.isEmpty()) {
                    // Cursor on a character — highlight it with inverse video
                    String firstGrapheme = firstGrapheme(after);
                    String rest = after.substring(firstGrapheme.length());
                    sb.append(before);
                    sb.append("\033[7m").append(firstGrapheme).append("\033[0m");
                    sb.append(rest);
                } else {
                    // Cursor at end — show inverse space
                    sb.append(before);
                    sb.append("\033[7m \033[0m");
                }
            } else {
                sb.append(displayText);
            }

            // Pad to full width
            int visLen = AnsiUtils.visibleWidth(sb.toString());
            int pad = Math.max(0, contentWidth - visLen);
            sb.append(" ".repeat(pad));

            result.add(sb.toString());
        }

        return result;
    }

    @Override
    public void handleInput(String data) {
        // --- Undo ---
        if (KEY_CTRL_Z.equals(data)) {
            undo();
            return;
        }

        // --- Kill ring: Ctrl+K (delete to line end) ---
        if (KEY_CTRL_K.equals(data)) {
            deleteToEndOfLine();
            return;
        }

        // --- Kill ring: Ctrl+U (delete to line start) ---
        if (KEY_CTRL_U.equals(data)) {
            deleteToStartOfLine();
            return;
        }

        // --- Kill ring: Ctrl+W (delete word backward) ---
        if (KEY_CTRL_W.equals(data)) {
            deleteWordBackward();
            return;
        }

        // --- Kill ring: Alt+D (delete word forward) ---
        if (KEY_ALT_D.equals(data)) {
            deleteWordForward();
            return;
        }

        // --- Yank: Ctrl+Y ---
        if (KEY_CTRL_Y.equals(data)) {
            yank();
            return;
        }

        // --- Yank-pop: Alt+Y ---
        if (KEY_ALT_Y.equals(data)) {
            yankPop();
            return;
        }

        // --- Newline in submit mode: Shift+Enter or Alt+Enter ---
        if (submitOnEnter && (KEY_SHIFT_ENTER_KITTY.equals(data)
                || KEY_ALT_ENTER.equals(data) || KEY_ALT_NEWLINE.equals(data))) {
            addNewLine();
            return;
        }

        // --- Enter handling ---
        if (KEY_ENTER.equals(data) || KEY_NEWLINE.equals(data)) {
            if (submitOnEnter) {
                // Backslash+Enter workaround: if char before cursor is \, delete it and insert newline
                String line = lines.get(cursorLine);
                if (cursorCol > 0 && line.charAt(cursorCol - 1) == '\\') {
                    pushUndoSnapshot();
                    lines.set(cursorLine, line.substring(0, cursorCol - 1) + line.substring(cursorCol));
                    cursorCol--;
                    addNewLine();
                    return;
                }
                // Submit
                if (onSubmit != null) {
                    String text = getText();
                    onSubmit.accept(text);
                }
            } else {
                addNewLine();
            }
            return;
        }

        // --- Cursor movement ---
        if (KEY_UP.equals(data)) {
            if (submitOnEnter && cursorLine == 0) {
                // Navigate history when at first line
                navigateHistory(-1);
                return;
            }
            moveCursorVertical(-1);
            return;
        }
        if (KEY_DOWN.equals(data)) {
            if (submitOnEnter && cursorLine == lines.size() - 1) {
                // Navigate history when at last line
                navigateHistory(1);
                return;
            }
            moveCursorVertical(1);
            return;
        }
        if (KEY_LEFT.equals(data) || KEY_CTRL_B.equals(data)) {
            moveCursorLeft();
            return;
        }
        if (KEY_RIGHT.equals(data) || KEY_CTRL_F.equals(data)) {
            moveCursorRight();
            return;
        }
        if (KEY_HOME.equals(data) || KEY_CTRL_A.equals(data)) {
            moveToLineStart();
            return;
        }
        if (KEY_END.equals(data) || KEY_CTRL_E.equals(data)) {
            moveToLineEnd();
            return;
        }

        // --- Word movement ---
        if (KEY_ALT_LEFT.equals(data) || KEY_CTRL_LEFT.equals(data) || KEY_ALT_B.equals(data)) {
            moveWordBackward();
            return;
        }
        if (KEY_ALT_RIGHT.equals(data) || KEY_CTRL_RIGHT.equals(data) || KEY_ALT_F.equals(data)) {
            moveWordForward();
            return;
        }

        // --- Backspace ---
        if (KEY_BACKSPACE.equals(data) || KEY_BACKSPACE_BS.equals(data)) {
            handleBackspace();
            return;
        }

        // --- Delete forward ---
        if (KEY_DELETE.equals(data)) {
            handleDelete();
            return;
        }

        // --- Bracketed paste ---
        if (data.startsWith("\033[200~")) {
            String pasteContent = data;
            // Strip paste markers
            if (pasteContent.startsWith("\033[200~")) {
                pasteContent = pasteContent.substring(6);
            }
            if (pasteContent.endsWith("\033[201~")) {
                pasteContent = pasteContent.substring(0, pasteContent.length() - 6);
            }
            if (!pasteContent.isEmpty()) {
                pushUndoSnapshot();
                insertTextInternal(pasteContent);
                lastAction = null;
                fireOnChange();
            }
            return;
        }

        // --- Regular character input ---
        if (!data.isEmpty() && data.charAt(0) >= 32 && !data.startsWith("\033")) {
            insertCharacter(data);
        }
    }

    // -------------------------------------------------------------------
    // Focusable interface
    // -------------------------------------------------------------------

    @Override
    public boolean isFocused() {
        return focused;
    }

    @Override
    public void setFocused(boolean focused) {
        this.focused = focused;
    }

    // -------------------------------------------------------------------
    // History navigation
    // -------------------------------------------------------------------

    private void navigateHistory(int direction) {
        if (history.isEmpty()) return;

        if (historyIndex == -1) {
            // Starting to browse history — save current text
            savedCurrentText = getText();
            if (direction < 0) {
                historyIndex = history.size() - 1;
            } else {
                return; // Already at newest
            }
        } else {
            int newIndex = historyIndex + direction;
            if (newIndex < 0) return; // Already at oldest
            if (newIndex >= history.size()) {
                // Return to current text
                historyIndex = -1;
                setText(savedCurrentText);
                return;
            }
            historyIndex = newIndex;
        }

        setText(history.get(historyIndex));
    }

    // -------------------------------------------------------------------
    // Text editing operations
    // -------------------------------------------------------------------

    private void insertCharacter(String ch) {
        // Undo coalescing: consecutive word chars coalesce, whitespace breaks the chain
        if (isWhitespace(ch) || !"type-word".equals(lastAction)) {
            pushUndoSnapshot();
        }
        lastAction = "type-word";

        String line = lines.get(cursorLine);
        lines.set(cursorLine, line.substring(0, cursorCol) + ch + line.substring(cursorCol));
        cursorCol += ch.length();
        preferredVisualCol = -1;
        fireOnChange();
    }

    private void addNewLine() {
        pushUndoSnapshot();
        lastAction = null;

        String line = lines.get(cursorLine);
        String before = line.substring(0, cursorCol);
        String after = line.substring(cursorCol);
        lines.set(cursorLine, before);
        lines.add(cursorLine + 1, after);
        cursorLine++;
        cursorCol = 0;
        preferredVisualCol = -1;
        fireOnChange();
    }

    private void handleBackspace() {
        lastAction = null;
        if (cursorCol > 0) {
            pushUndoSnapshot();
            String line = lines.get(cursorLine);
            String before = line.substring(0, cursorCol);
            int graphemeLen = lastGraphemeLength(before);
            lines.set(cursorLine, line.substring(0, cursorCol - graphemeLen) + line.substring(cursorCol));
            cursorCol -= graphemeLen;
            preferredVisualCol = -1;
            fireOnChange();
        } else if (cursorLine > 0) {
            // Merge with previous line
            pushUndoSnapshot();
            String currentLine = lines.remove(cursorLine);
            cursorLine--;
            cursorCol = lines.get(cursorLine).length();
            lines.set(cursorLine, lines.get(cursorLine) + currentLine);
            preferredVisualCol = -1;
            fireOnChange();
        }
    }

    private void handleDelete() {
        lastAction = null;
        String line = lines.get(cursorLine);
        if (cursorCol < line.length()) {
            pushUndoSnapshot();
            String after = line.substring(cursorCol);
            int graphemeLen = firstGrapheme(after).length();
            lines.set(cursorLine, line.substring(0, cursorCol) + line.substring(cursorCol + graphemeLen));
            fireOnChange();
        } else if (cursorLine < lines.size() - 1) {
            // Merge with next line
            pushUndoSnapshot();
            String nextLine = lines.remove(cursorLine + 1);
            lines.set(cursorLine, line + nextLine);
            fireOnChange();
        }
    }

    // -------------------------------------------------------------------
    // Kill ring operations
    // -------------------------------------------------------------------

    private void deleteToEndOfLine() {
        String line = lines.get(cursorLine);
        if (cursorCol >= line.length()) {
            // At end of line: merge with next line (like delete)
            if (cursorLine < lines.size() - 1) {
                pushUndoSnapshot();
                String nextLine = lines.remove(cursorLine + 1);
                String killed = "\n" + nextLine;
                killRing.push(killed, false, "kill".equals(lastAction));
                lastAction = "kill";
                lines.set(cursorLine, line + nextLine);
                fireOnChange();
            }
            return;
        }

        pushUndoSnapshot();
        String killed = line.substring(cursorCol);
        killRing.push(killed, false, "kill".equals(lastAction));
        lastAction = "kill";
        lines.set(cursorLine, line.substring(0, cursorCol));
        preferredVisualCol = -1;
        fireOnChange();
    }

    private void deleteToStartOfLine() {
        if (cursorCol == 0) return;

        pushUndoSnapshot();
        String line = lines.get(cursorLine);
        String killed = line.substring(0, cursorCol);
        killRing.push(killed, true, "kill".equals(lastAction));
        lastAction = "kill";
        lines.set(cursorLine, line.substring(cursorCol));
        cursorCol = 0;
        preferredVisualCol = -1;
        fireOnChange();
    }

    private void deleteWordBackward() {
        if (cursorCol == 0 && cursorLine == 0) return;

        boolean wasKill = "kill".equals(lastAction);
        pushUndoSnapshot();

        int oldLine = cursorLine;
        int oldCol = cursorCol;
        moveWordBackwardInternal();
        int newCol = cursorCol;
        int newLine = cursorLine;

        // Restore cursor to compute deleted range
        cursorLine = oldLine;
        cursorCol = oldCol;

        String deleted;
        if (newLine == oldLine) {
            String line = lines.get(cursorLine);
            deleted = line.substring(newCol, oldCol);
            lines.set(cursorLine, line.substring(0, newCol) + line.substring(oldCol));
            cursorCol = newCol;
        } else {
            // Cross-line delete: merge
            deleted = lines.get(newLine).substring(newCol) + "\n" + lines.get(oldLine).substring(0, oldCol);
            String merged = lines.get(newLine).substring(0, newCol) + lines.get(oldLine).substring(oldCol);
            lines.set(newLine, merged);
            lines.remove(oldLine);
            cursorLine = newLine;
            cursorCol = newCol;
        }

        killRing.push(deleted, true, wasKill);
        lastAction = "kill";
        preferredVisualCol = -1;
        fireOnChange();
    }

    private void deleteWordForward() {
        String line = lines.get(cursorLine);
        if (cursorCol >= line.length() && cursorLine >= lines.size() - 1) return;

        boolean wasKill = "kill".equals(lastAction);
        pushUndoSnapshot();

        int oldLine = cursorLine;
        int oldCol = cursorCol;
        moveWordForwardInternal();
        int newCol = cursorCol;
        int newLine = cursorLine;

        // Restore cursor
        cursorLine = oldLine;
        cursorCol = oldCol;

        String deleted;
        if (newLine == oldLine) {
            deleted = line.substring(oldCol, newCol);
            lines.set(cursorLine, line.substring(0, oldCol) + line.substring(newCol));
        } else {
            deleted = line.substring(oldCol) + "\n" + lines.get(newLine).substring(0, newCol);
            String merged = line.substring(0, oldCol) + lines.get(newLine).substring(newCol);
            lines.set(oldLine, merged);
            for (int i = oldLine + 1; i <= newLine; i++) {
                lines.remove(oldLine + 1);
            }
        }

        killRing.push(deleted, false, wasKill);
        lastAction = "kill";
        preferredVisualCol = -1;
        fireOnChange();
    }

    private void yank() {
        String text = killRing.peek();
        if (text == null) return;

        pushUndoSnapshot();
        insertTextInternal(text);
        lastAction = "yank";
        fireOnChange();
    }

    private void yankPop() {
        if (!"yank".equals(lastAction) || killRing.size() <= 1) return;

        pushUndoSnapshot();

        // Delete the previously yanked text
        String prevText = killRing.peek();
        if (prevText != null) {
            deleteBackward(prevText.length());
        }

        // Rotate and insert new entry
        killRing.rotate();
        String text = killRing.peek();
        if (text != null) {
            insertTextInternal(text);
        }
        lastAction = "yank";
        fireOnChange();
    }

    // -------------------------------------------------------------------
    // Undo
    // -------------------------------------------------------------------

    private void undo() {
        EditorSnapshot snapshot = undoStack.pop();
        if (snapshot == null) return;
        lines = snapshot.lines;
        cursorLine = snapshot.cursorLine;
        cursorCol = snapshot.cursorCol;
        lastAction = null;
        preferredVisualCol = -1;
        fireOnChange();
    }

    private void pushUndoSnapshot() {
        undoStack.push(new EditorSnapshot(lines, cursorLine, cursorCol));
    }

    // -------------------------------------------------------------------
    // Cursor movement
    // -------------------------------------------------------------------

    private void moveCursorLeft() {
        lastAction = null;
        preferredVisualCol = -1;
        if (cursorCol > 0) {
            String before = lines.get(cursorLine).substring(0, cursorCol);
            cursorCol -= lastGraphemeLength(before);
        } else if (cursorLine > 0) {
            cursorLine--;
            cursorCol = lines.get(cursorLine).length();
        }
    }

    private void moveCursorRight() {
        lastAction = null;
        preferredVisualCol = -1;
        String line = lines.get(cursorLine);
        if (cursorCol < line.length()) {
            cursorCol += firstGrapheme(line.substring(cursorCol)).length();
        } else if (cursorLine < lines.size() - 1) {
            cursorLine++;
            cursorCol = 0;
        }
    }

    private void moveCursorVertical(int direction) {
        lastAction = null;

        // Calculate preferred visual column
        if (preferredVisualCol < 0) {
            preferredVisualCol = AnsiUtils.visibleWidth(lines.get(cursorLine).substring(0, cursorCol));
        }

        int newLine = cursorLine + direction;
        if (newLine < 0 || newLine >= lines.size()) return;

        cursorLine = newLine;
        // Attempt to place cursor at the preferred visual column
        cursorCol = colFromVisualWidth(lines.get(cursorLine), preferredVisualCol);
    }

    private void moveToLineStart() {
        lastAction = null;
        cursorCol = 0;
        preferredVisualCol = -1;
    }

    private void moveToLineEnd() {
        lastAction = null;
        cursorCol = lines.get(cursorLine).length();
        preferredVisualCol = -1;
    }

    private void moveWordBackward() {
        lastAction = null;
        moveWordBackwardInternal();
    }

    private void moveWordForward() {
        lastAction = null;
        moveWordForwardInternal();
    }

    /**
     * Word backward: skip trailing whitespace, then skip a run of same-type chars
     * (punctuation or word chars).
     */
    private void moveWordBackwardInternal() {
        preferredVisualCol = -1;

        if (cursorCol == 0) {
            // Move to end of previous line
            if (cursorLine > 0) {
                cursorLine--;
                cursorCol = lines.get(cursorLine).length();
            }
            return;
        }

        String before = lines.get(cursorLine).substring(0, cursorCol);
        BreakIterator bi = BreakIterator.getCharacterInstance();
        bi.setText(before);

        // Collect graphemes
        List<String> graphemes = new ArrayList<>();
        int start = bi.first();
        int end = bi.next();
        while (end != BreakIterator.DONE) {
            graphemes.add(before.substring(start, end));
            start = end;
            end = bi.next();
        }

        int idx = graphemes.size() - 1;

        // Skip trailing whitespace
        while (idx >= 0 && isWhitespace(graphemes.get(idx))) {
            cursorCol -= graphemes.get(idx).length();
            idx--;
        }

        if (idx < 0) return;

        // Determine character type
        boolean isPunct = isPunctuation(graphemes.get(idx));

        // Skip run of same type
        while (idx >= 0) {
            String g = graphemes.get(idx);
            if (isWhitespace(g) || (isPunct != isPunctuation(g))) break;
            cursorCol -= g.length();
            idx--;
        }
    }

    /**
     * Word forward: skip leading whitespace, then skip a run of same-type chars.
     */
    private void moveWordForwardInternal() {
        preferredVisualCol = -1;

        String line = lines.get(cursorLine);
        if (cursorCol >= line.length()) {
            // Move to start of next line
            if (cursorLine < lines.size() - 1) {
                cursorLine++;
                cursorCol = 0;
            }
            return;
        }

        String after = line.substring(cursorCol);
        BreakIterator bi = BreakIterator.getCharacterInstance();
        bi.setText(after);

        List<String> graphemes = new ArrayList<>();
        int start = bi.first();
        int end = bi.next();
        while (end != BreakIterator.DONE) {
            graphemes.add(after.substring(start, end));
            start = end;
            end = bi.next();
        }

        int idx = 0;

        // Skip leading whitespace
        while (idx < graphemes.size() && isWhitespace(graphemes.get(idx))) {
            cursorCol += graphemes.get(idx).length();
            idx++;
        }

        if (idx >= graphemes.size()) return;

        // Determine character type
        boolean isPunct = isPunctuation(graphemes.get(idx));

        // Skip run of same type
        while (idx < graphemes.size()) {
            String g = graphemes.get(idx);
            if (isWhitespace(g) || (isPunct != isPunctuation(g))) break;
            cursorCol += g.length();
            idx++;
        }
    }

    // -------------------------------------------------------------------
    // Layout (word wrap for rendering)
    // -------------------------------------------------------------------

    private record LayoutLine(String text, boolean hasCursor, int cursorPos) {
    }

    private List<LayoutLine> layoutText(int contentWidth) {
        List<LayoutLine> result = new ArrayList<>();

        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);
            boolean isCurrentLine = (i == cursorLine);

            int lineWidth = AnsiUtils.visibleWidth(line);
            if (lineWidth <= contentWidth) {
                // Line fits in one layout line
                if (isCurrentLine) {
                    result.add(new LayoutLine(line, true, cursorCol));
                } else {
                    result.add(new LayoutLine(line, false, -1));
                }
            } else {
                // Word wrap needed
                List<TextChunk> chunks = wordWrapLine(line, contentWidth);
                for (int ci = 0; ci < chunks.size(); ci++) {
                    TextChunk chunk = chunks.get(ci);
                    boolean isLastChunk = (ci == chunks.size() - 1);

                    if (isCurrentLine) {
                        boolean hasCursorInChunk;
                        int adjustedPos;
                        if (isLastChunk) {
                            hasCursorInChunk = cursorCol >= chunk.startIndex;
                            adjustedPos = cursorCol - chunk.startIndex;
                        } else {
                            hasCursorInChunk = cursorCol >= chunk.startIndex && cursorCol < chunk.endIndex;
                            adjustedPos = cursorCol - chunk.startIndex;
                            if (adjustedPos > chunk.text.length()) {
                                adjustedPos = chunk.text.length();
                            }
                        }

                        if (hasCursorInChunk) {
                            result.add(new LayoutLine(chunk.text, true, adjustedPos));
                        } else {
                            result.add(new LayoutLine(chunk.text, false, -1));
                        }
                    } else {
                        result.add(new LayoutLine(chunk.text, false, -1));
                    }
                }
            }
        }

        if (result.isEmpty()) {
            result.add(new LayoutLine("", true, 0));
        }

        return result;
    }

    /** Text chunk for word wrapping — tracks text and position in original line. */
    private record TextChunk(String text, int startIndex, int endIndex) {
    }

    /**
     * Word-wrap a single line into chunks.
     * Wraps at word boundaries when possible, falls back to character-level breaks.
     */
    private List<TextChunk> wordWrapLine(String line, int maxWidth) {
        if (line == null || line.isEmpty() || maxWidth <= 0) {
            return List.of(new TextChunk("", 0, 0));
        }

        int lineWidth = AnsiUtils.visibleWidth(line);
        if (lineWidth <= maxWidth) {
            return List.of(new TextChunk(line, 0, line.length()));
        }

        List<TextChunk> chunks = new ArrayList<>();
        BreakIterator bi = BreakIterator.getCharacterInstance();
        bi.setText(line);

        // Collect grapheme boundaries and their properties
        List<int[]> boundaries = new ArrayList<>(); // [start, end]
        int start = bi.first();
        int end = bi.next();
        while (end != BreakIterator.DONE) {
            boundaries.add(new int[]{start, end});
            start = end;
            end = bi.next();
        }

        int currentWidth = 0;
        int chunkStart = 0;
        int wrapOppIndex = -1; // character index of last wrap opportunity
        int wrapOppWidth = 0;

        for (int i = 0; i < boundaries.size(); i++) {
            int[] seg = boundaries.get(i);
            String grapheme = line.substring(seg[0], seg[1]);
            int gWidth = AnsiUtils.visibleWidth(grapheme);
            boolean isWs = isWhitespace(grapheme);

            // Overflow check
            if (currentWidth + gWidth > maxWidth) {
                if (wrapOppIndex >= 0 && currentWidth - wrapOppWidth + gWidth <= maxWidth) {
                    // Backtrack to wrap opportunity
                    chunks.add(new TextChunk(line.substring(chunkStart, wrapOppIndex), chunkStart, wrapOppIndex));
                    chunkStart = wrapOppIndex;
                    currentWidth -= wrapOppWidth;
                } else if (chunkStart < seg[0]) {
                    // Force break at current position
                    chunks.add(new TextChunk(line.substring(chunkStart, seg[0]), chunkStart, seg[0]));
                    chunkStart = seg[0];
                    currentWidth = 0;
                }
                wrapOppIndex = -1;
            }

            currentWidth += gWidth;

            // Record wrap opportunity: whitespace followed by non-whitespace
            if (isWs && i + 1 < boundaries.size()) {
                int[] next = boundaries.get(i + 1);
                String nextGrapheme = line.substring(next[0], next[1]);
                if (!isWhitespace(nextGrapheme)) {
                    wrapOppIndex = next[0];
                    wrapOppWidth = currentWidth;
                }
            }
        }

        // Final chunk
        chunks.add(new TextChunk(line.substring(chunkStart), chunkStart, line.length()));

        return chunks;
    }

    // -------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------

    /** Insert text at cursor position (may be multi-line). Does NOT push undo snapshot. */
    private void insertTextInternal(String text) {
        if (text == null || text.isEmpty()) return;

        String normalized = normalizeText(text);
        String[] insertedLines = normalized.split("\n", -1);

        String currentLine = lines.get(cursorLine);
        String before = currentLine.substring(0, cursorCol);
        String after = currentLine.substring(cursorCol);

        if (insertedLines.length == 1) {
            lines.set(cursorLine, before + normalized + after);
            cursorCol += normalized.length();
        } else {
            // Multi-line insertion
            lines.set(cursorLine, before + insertedLines[0]);
            for (int i = 1; i < insertedLines.length - 1; i++) {
                lines.add(cursorLine + i, insertedLines[i]);
            }
            String lastInserted = insertedLines[insertedLines.length - 1];
            lines.add(cursorLine + insertedLines.length - 1, lastInserted + after);
            cursorLine += insertedLines.length - 1;
            cursorCol = lastInserted.length();
        }
        preferredVisualCol = -1;
    }

    /** Delete n characters backward from cursor. */
    private void deleteBackward(int count) {
        int remaining = count;
        while (remaining > 0) {
            if (cursorCol > 0) {
                int toDelete = Math.min(remaining, cursorCol);
                String line = lines.get(cursorLine);
                lines.set(cursorLine, line.substring(0, cursorCol - toDelete) + line.substring(cursorCol));
                cursorCol -= toDelete;
                remaining -= toDelete;
            } else if (cursorLine > 0) {
                // Merge with previous line
                String currentLine = lines.remove(cursorLine);
                cursorLine--;
                cursorCol = lines.get(cursorLine).length();
                lines.set(cursorLine, lines.get(cursorLine) + currentLine);
                remaining--; // consumed the \n
            } else {
                break;
            }
        }
        preferredVisualCol = -1;
    }

    /** Returns the first grapheme of a string, or the first char if BreakIterator fails. */
    private static String firstGrapheme(String text) {
        if (text == null || text.isEmpty()) return "";
        BreakIterator bi = BreakIterator.getCharacterInstance();
        bi.setText(text);
        bi.first();
        int end = bi.next();
        return end == BreakIterator.DONE ? text.substring(0, 1) : text.substring(0, end);
    }

    /** Returns the length of the last grapheme in text. */
    private static int lastGraphemeLength(String text) {
        if (text == null || text.isEmpty()) return 0;
        BreakIterator bi = BreakIterator.getCharacterInstance();
        bi.setText(text);
        int last = bi.last();
        int prev = bi.previous();
        return prev == BreakIterator.DONE ? text.length() : last - prev;
    }

    /** Find the column offset corresponding to a given visual width target. */
    private static int colFromVisualWidth(String line, int targetVisualWidth) {
        if (line.isEmpty()) return 0;
        BreakIterator bi = BreakIterator.getCharacterInstance();
        bi.setText(line);
        int col = 0;
        int width = 0;
        int start = bi.first();
        int end = bi.next();
        while (end != BreakIterator.DONE) {
            String grapheme = line.substring(start, end);
            int gw = AnsiUtils.visibleWidth(grapheme);
            if (width + gw > targetVisualWidth) break;
            width += gw;
            col = end;
            start = end;
            end = bi.next();
        }
        return col;
    }

    private static boolean isWhitespace(String s) {
        if (s == null || s.isEmpty()) return false;
        return Character.isWhitespace(s.codePointAt(0));
    }

    private static boolean isPunctuation(String s) {
        if (s == null || s.isEmpty()) return false;
        int type = Character.getType(s.codePointAt(0));
        return type == Character.CONNECTOR_PUNCTUATION
                || type == Character.DASH_PUNCTUATION
                || type == Character.START_PUNCTUATION
                || type == Character.END_PUNCTUATION
                || type == Character.INITIAL_QUOTE_PUNCTUATION
                || type == Character.FINAL_QUOTE_PUNCTUATION
                || type == Character.OTHER_PUNCTUATION
                || type == Character.MATH_SYMBOL;
    }

    private static String normalizeText(String text) {
        return text.replace("\r\n", "\n").replace("\r", "\n").replace("\t", "    ");
    }

    private void fireOnChange() {
        if (onChange != null) {
            onChange.accept(getText());
        }
    }

    // -------------------------------------------------------------------
    // Snapshot for undo
    // -------------------------------------------------------------------

    private record EditorSnapshot(List<String> lines, int cursorLine, int cursorCol) {
    }

    private EditorSnapshot cloneSnapshot(EditorSnapshot snapshot) {
        return new EditorSnapshot(new ArrayList<>(snapshot.lines), snapshot.cursorLine, snapshot.cursorCol);
    }
}
