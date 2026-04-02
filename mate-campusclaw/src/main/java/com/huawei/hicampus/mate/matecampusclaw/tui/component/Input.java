package com.huawei.hicampus.mate.matecampusclaw.tui.component;

import java.text.BreakIterator;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import com.huawei.hicampus.mate.matecampusclaw.tui.Component;
import com.huawei.hicampus.mate.matecampusclaw.tui.Focusable;
import com.huawei.hicampus.mate.matecampusclaw.tui.ansi.AnsiUtils;

/**
 * Single-line text input component with horizontal scrolling.
 * <p>
 * Features:
 * <ul>
 *   <li>Cursor movement (left, right, Home, End, word movement)</li>
 *   <li>Character insertion and deletion (backspace, delete)</li>
 *   <li>Kill ring (Ctrl+K, Ctrl+U, Ctrl+W, Alt+D, Ctrl+Y, Alt+Y)</li>
 *   <li>Undo (Ctrl+Z)</li>
 *   <li>Placeholder text when empty</li>
 *   <li>Horizontal scrolling to keep cursor visible</li>
 *   <li>Enter submits, Escape cancels</li>
 * </ul>
 */
public class Input implements Component, Focusable {

    // --- ANSI key sequences ---
    private static final String KEY_RIGHT = "\033[C";
    private static final String KEY_LEFT = "\033[D";
    private static final String KEY_HOME = "\033[H";
    private static final String KEY_END = "\033[F";
    private static final String KEY_DELETE = "\033[3~";
    private static final String KEY_BACKSPACE = "\177";
    private static final String KEY_BACKSPACE_BS = "\010";
    private static final String KEY_ENTER = "\r";
    private static final String KEY_NEWLINE = "\n";
    private static final String KEY_ESCAPE = "\033";
    private static final String KEY_CTRL_C = "\003";

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

    private static final String PROMPT = "> ";
    private static final int PROMPT_WIDTH = 2;

    private String value = "";
    private int cursor = 0;
    private String placeholder;
    private boolean focused;

    // Kill ring
    private final KillRing killRing = new KillRing();
    private String lastAction; // "kill", "yank", "type-word", or null

    // Undo
    private record InputSnapshot(String value, int cursor) {}
    private final UndoStack<InputSnapshot> undoStack = new UndoStack<>(
            s -> new InputSnapshot(s.value, s.cursor)
    );

    // Callbacks
    private Consumer<String> onSubmit;
    private Runnable onEscape;

    public Input() {
        this(null);
    }

    public Input(String placeholder) {
        this.placeholder = placeholder;
    }

    // -------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------

    public String getValue() {
        return value;
    }

    public void setValue(String value) {
        this.value = value != null ? value : "";
        this.cursor = Math.min(this.cursor, this.value.length());
    }

    public int getCursor() {
        return cursor;
    }

    public String getPlaceholder() {
        return placeholder;
    }

    public void setPlaceholder(String placeholder) {
        this.placeholder = placeholder;
    }

    public void setOnSubmit(Consumer<String> onSubmit) {
        this.onSubmit = onSubmit;
    }

    public void setOnEscape(Runnable onEscape) {
        this.onEscape = onEscape;
    }

    // -------------------------------------------------------------------
    // Component interface
    // -------------------------------------------------------------------

    @Override
    public void invalidate() {
        // No cached state
    }

    @Override
    public List<String> render(int width) {
        int availableWidth = width - PROMPT_WIDTH;
        if (availableWidth <= 0) {
            return List.of(PROMPT);
        }

        // Show placeholder when empty and not focused
        if (value.isEmpty() && placeholder != null && !placeholder.isEmpty() && !focused) {
            String placeholderText = "\033[2m" + truncateToWidth(placeholder, availableWidth) + "\033[0m";
            int visLen = AnsiUtils.visibleWidth(placeholderText);
            String padding = " ".repeat(Math.max(0, availableWidth - visLen));
            return List.of(PROMPT + placeholderText + padding);
        }

        // Calculate visible portion with horizontal scrolling
        String visibleText;
        int cursorDisplayPos;
        int totalWidth = AnsiUtils.visibleWidth(value);

        if (totalWidth < availableWidth) {
            // Everything fits
            visibleText = value;
            cursorDisplayPos = cursor;
        } else {
            // Horizontal scrolling needed
            int scrollWidth = cursor == value.length() ? availableWidth - 1 : availableWidth;
            int cursorCol = AnsiUtils.visibleWidth(value.substring(0, cursor));
            int halfWidth = scrollWidth / 2;

            int startCol;
            if (cursorCol < halfWidth) {
                startCol = 0;
            } else if (cursorCol > totalWidth - halfWidth) {
                startCol = Math.max(0, totalWidth - scrollWidth);
            } else {
                startCol = Math.max(0, cursorCol - halfWidth);
            }

            visibleText = AnsiUtils.sliceByColumn(value, startCol, startCol + scrollWidth);
            String beforeCursor = AnsiUtils.sliceByColumn(value, startCol, startCol + Math.max(0, cursorCol - startCol));
            cursorDisplayPos = beforeCursor.length();
        }

        // Build line with cursor
        StringBuilder sb = new StringBuilder(PROMPT);

        if (focused) {
            String before = visibleText.substring(0, Math.min(cursorDisplayPos, visibleText.length()));
            String after = cursorDisplayPos < visibleText.length()
                    ? visibleText.substring(cursorDisplayPos) : "";

            sb.append(before);
            if (!after.isEmpty()) {
                String firstG = firstGrapheme(after);
                sb.append("\033[7m").append(firstG).append("\033[27m");
                sb.append(after.substring(firstG.length()));
            } else {
                sb.append("\033[7m \033[27m");
            }
        } else {
            sb.append(visibleText);
        }

        // Pad to fill available width
        int visLen = AnsiUtils.visibleWidth(sb.toString()) - PROMPT_WIDTH;
        int padding = Math.max(0, availableWidth - visLen);
        sb.append(" ".repeat(padding));

        return List.of(sb.toString());
    }

    @Override
    public void handleInput(String data) {
        // Escape / Ctrl+C
        if (KEY_ESCAPE.equals(data) || KEY_CTRL_C.equals(data)) {
            if (onEscape != null) onEscape.run();
            return;
        }

        // Undo
        if (KEY_CTRL_Z.equals(data)) {
            undo();
            return;
        }

        // Submit
        if (KEY_ENTER.equals(data) || KEY_NEWLINE.equals(data)) {
            if (onSubmit != null) onSubmit.accept(value);
            return;
        }

        // Deletion
        if (KEY_BACKSPACE.equals(data) || KEY_BACKSPACE_BS.equals(data)) {
            handleBackspace();
            return;
        }
        if (KEY_DELETE.equals(data)) {
            handleForwardDelete();
            return;
        }
        if (KEY_CTRL_W.equals(data)) {
            deleteWordBackward();
            return;
        }
        if (KEY_ALT_D.equals(data)) {
            deleteWordForward();
            return;
        }
        if (KEY_CTRL_U.equals(data)) {
            deleteToLineStart();
            return;
        }
        if (KEY_CTRL_K.equals(data)) {
            deleteToLineEnd();
            return;
        }

        // Kill ring
        if (KEY_CTRL_Y.equals(data)) {
            yank();
            return;
        }
        if (KEY_ALT_Y.equals(data)) {
            yankPop();
            return;
        }

        // Cursor movement
        if (KEY_LEFT.equals(data) || KEY_CTRL_B.equals(data)) {
            lastAction = null;
            if (cursor > 0) {
                cursor -= lastGraphemeLength(value.substring(0, cursor));
            }
            return;
        }
        if (KEY_RIGHT.equals(data) || KEY_CTRL_F.equals(data)) {
            lastAction = null;
            if (cursor < value.length()) {
                cursor += firstGrapheme(value.substring(cursor)).length();
            }
            return;
        }
        if (KEY_HOME.equals(data) || KEY_CTRL_A.equals(data)) {
            lastAction = null;
            cursor = 0;
            return;
        }
        if (KEY_END.equals(data) || KEY_CTRL_E.equals(data)) {
            lastAction = null;
            cursor = value.length();
            return;
        }

        // Word movement
        if (KEY_ALT_LEFT.equals(data) || KEY_CTRL_LEFT.equals(data) || KEY_ALT_B.equals(data)) {
            moveWordBackward();
            return;
        }
        if (KEY_ALT_RIGHT.equals(data) || KEY_CTRL_RIGHT.equals(data) || KEY_ALT_F.equals(data)) {
            moveWordForward();
            return;
        }

        // Regular character input
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
    // Text editing operations
    // -------------------------------------------------------------------

    private void insertCharacter(String ch) {
        if (isWhitespace(ch) || !"type-word".equals(lastAction)) {
            pushUndo();
        }
        lastAction = "type-word";
        value = value.substring(0, cursor) + ch + value.substring(cursor);
        cursor += ch.length();
    }

    private void handleBackspace() {
        lastAction = null;
        if (cursor > 0) {
            pushUndo();
            int graphemeLen = lastGraphemeLength(value.substring(0, cursor));
            value = value.substring(0, cursor - graphemeLen) + value.substring(cursor);
            cursor -= graphemeLen;
        }
    }

    private void handleForwardDelete() {
        lastAction = null;
        if (cursor < value.length()) {
            pushUndo();
            int graphemeLen = firstGrapheme(value.substring(cursor)).length();
            value = value.substring(0, cursor) + value.substring(cursor + graphemeLen);
        }
    }

    private void deleteToLineStart() {
        if (cursor == 0) return;
        pushUndo();
        String deleted = value.substring(0, cursor);
        killRing.push(deleted, true, "kill".equals(lastAction));
        lastAction = "kill";
        value = value.substring(cursor);
        cursor = 0;
    }

    private void deleteToLineEnd() {
        if (cursor >= value.length()) return;
        pushUndo();
        String deleted = value.substring(cursor);
        killRing.push(deleted, false, "kill".equals(lastAction));
        lastAction = "kill";
        value = value.substring(0, cursor);
    }

    private void deleteWordBackward() {
        if (cursor == 0) return;
        boolean wasKill = "kill".equals(lastAction);
        pushUndo();

        int oldCursor = cursor;
        moveWordBackwardInternal();
        int newCursor = cursor;
        cursor = oldCursor;

        String deleted = value.substring(newCursor, cursor);
        killRing.push(deleted, true, wasKill);
        lastAction = "kill";
        value = value.substring(0, newCursor) + value.substring(cursor);
        cursor = newCursor;
    }

    private void deleteWordForward() {
        if (cursor >= value.length()) return;
        boolean wasKill = "kill".equals(lastAction);
        pushUndo();

        int oldCursor = cursor;
        moveWordForwardInternal();
        int newCursor = cursor;
        cursor = oldCursor;

        String deleted = value.substring(cursor, newCursor);
        killRing.push(deleted, false, wasKill);
        lastAction = "kill";
        value = value.substring(0, cursor) + value.substring(newCursor);
    }

    private void yank() {
        String text = killRing.peek();
        if (text == null) return;
        pushUndo();
        // Strip newlines for single-line input
        String cleaned = text.replace("\n", "").replace("\r", "");
        value = value.substring(0, cursor) + cleaned + value.substring(cursor);
        cursor += cleaned.length();
        lastAction = "yank";
    }

    private void yankPop() {
        if (!"yank".equals(lastAction) || killRing.size() <= 1) return;
        pushUndo();

        String prevText = killRing.peek();
        if (prevText != null) {
            String cleaned = prevText.replace("\n", "").replace("\r", "");
            value = value.substring(0, cursor - cleaned.length()) + value.substring(cursor);
            cursor -= cleaned.length();
        }

        killRing.rotate();
        String text = killRing.peek();
        if (text != null) {
            String cleaned = text.replace("\n", "").replace("\r", "");
            value = value.substring(0, cursor) + cleaned + value.substring(cursor);
            cursor += cleaned.length();
        }
        lastAction = "yank";
    }

    // -------------------------------------------------------------------
    // Undo
    // -------------------------------------------------------------------

    private void pushUndo() {
        undoStack.push(new InputSnapshot(value, cursor));
    }

    private void undo() {
        InputSnapshot snapshot = undoStack.pop();
        if (snapshot == null) return;
        value = snapshot.value;
        cursor = snapshot.cursor;
        lastAction = null;
    }

    // -------------------------------------------------------------------
    // Word movement
    // -------------------------------------------------------------------

    private void moveWordBackward() {
        lastAction = null;
        moveWordBackwardInternal();
    }

    private void moveWordForward() {
        lastAction = null;
        moveWordForwardInternal();
    }

    private void moveWordBackwardInternal() {
        if (cursor == 0) return;

        String before = value.substring(0, cursor);
        List<String> graphemes = graphemeList(before);
        int idx = graphemes.size() - 1;

        // Skip trailing whitespace
        while (idx >= 0 && isWhitespace(graphemes.get(idx))) {
            cursor -= graphemes.get(idx).length();
            idx--;
        }
        if (idx < 0) return;

        boolean isPunct = isPunctuation(graphemes.get(idx));
        while (idx >= 0) {
            String g = graphemes.get(idx);
            if (isWhitespace(g) || (isPunct != isPunctuation(g))) break;
            cursor -= g.length();
            idx--;
        }
    }

    private void moveWordForwardInternal() {
        if (cursor >= value.length()) return;

        String after = value.substring(cursor);
        List<String> graphemes = graphemeList(after);
        int idx = 0;

        // Skip leading whitespace
        while (idx < graphemes.size() && isWhitespace(graphemes.get(idx))) {
            cursor += graphemes.get(idx).length();
            idx++;
        }
        if (idx >= graphemes.size()) return;

        boolean isPunct = isPunctuation(graphemes.get(idx));
        while (idx < graphemes.size()) {
            String g = graphemes.get(idx);
            if (isWhitespace(g) || (isPunct != isPunctuation(g))) break;
            cursor += g.length();
            idx++;
        }
    }

    // -------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------

    private static String firstGrapheme(String text) {
        if (text == null || text.isEmpty()) return "";
        BreakIterator bi = BreakIterator.getCharacterInstance();
        bi.setText(text);
        bi.first();
        int end = bi.next();
        return end == BreakIterator.DONE ? text.substring(0, 1) : text.substring(0, end);
    }

    private static int lastGraphemeLength(String text) {
        if (text == null || text.isEmpty()) return 0;
        BreakIterator bi = BreakIterator.getCharacterInstance();
        bi.setText(text);
        int last = bi.last();
        int prev = bi.previous();
        return prev == BreakIterator.DONE ? text.length() : last - prev;
    }

    private static List<String> graphemeList(String text) {
        List<String> result = new ArrayList<>();
        BreakIterator bi = BreakIterator.getCharacterInstance();
        bi.setText(text);
        int start = bi.first();
        int end = bi.next();
        while (end != BreakIterator.DONE) {
            result.add(text.substring(start, end));
            start = end;
            end = bi.next();
        }
        return result;
    }

    private static String truncateToWidth(String text, int maxWidth) {
        if (text == null || text.isEmpty()) return "";
        if (AnsiUtils.visibleWidth(text) <= maxWidth) return text;
        return AnsiUtils.sliceByColumn(text, 0, maxWidth);
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
}
