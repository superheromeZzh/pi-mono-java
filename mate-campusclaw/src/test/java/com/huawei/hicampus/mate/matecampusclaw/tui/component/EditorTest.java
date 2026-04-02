package com.huawei.hicampus.mate.matecampusclaw.tui.component;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import com.huawei.hicampus.mate.matecampusclaw.tui.Focusable;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class EditorTest {

    // Key constants
    private static final String KEY_UP = "\033[A";
    private static final String KEY_DOWN = "\033[B";
    private static final String KEY_RIGHT = "\033[C";
    private static final String KEY_LEFT = "\033[D";
    private static final String KEY_HOME = "\033[H";
    private static final String KEY_END = "\033[F";
    private static final String KEY_DELETE = "\033[3~";
    private static final String KEY_BACKSPACE = "\177";
    private static final String KEY_ENTER = "\r";

    private static final String KEY_CTRL_A = "\001";
    private static final String KEY_CTRL_E = "\005";
    private static final String KEY_CTRL_K = "\013";
    private static final String KEY_CTRL_U = "\025";
    private static final String KEY_CTRL_W = "\027";
    private static final String KEY_CTRL_Y = "\031";
    private static final String KEY_CTRL_Z = "\032";
    private static final String KEY_ALT_D = "\033d";
    private static final String KEY_ALT_Y = "\033y";
    private static final String KEY_ALT_LEFT = "\033[1;3D";
    private static final String KEY_ALT_RIGHT = "\033[1;3C";

    // -------------------------------------------------------------------
    // Construction and basic accessors
    // -------------------------------------------------------------------

    @Nested
    class Construction {

        @Test
        void emptyEditor() {
            var editor = new Editor();
            assertEquals("", editor.getText());
            assertArrayEquals(new int[]{0, 0}, editor.getCursorPosition());
        }

        @Test
        void initialText() {
            var editor = new Editor("hello world");
            assertEquals("hello world", editor.getText());
            // Cursor at end
            assertArrayEquals(new int[]{0, 11}, editor.getCursorPosition());
        }

        @Test
        void multiLineInitialText() {
            var editor = new Editor("line1\nline2\nline3");
            assertEquals("line1\nline2\nline3", editor.getText());
            assertArrayEquals(new int[]{2, 5}, editor.getCursorPosition());
        }

        @Test
        void nullInitialTextTreatedAsEmpty() {
            var editor = new Editor(null);
            assertEquals("", editor.getText());
        }

        @Test
        void tabsNormalized() {
            var editor = new Editor("hello\tworld");
            assertEquals("hello    world", editor.getText());
        }

        @Test
        void crlfNormalized() {
            var editor = new Editor("line1\r\nline2");
            assertEquals("line1\nline2", editor.getText());
        }
    }

    // -------------------------------------------------------------------
    // Text editing: character insertion
    // -------------------------------------------------------------------

    @Nested
    class CharacterInsertion {

        @Test
        void insertSingleCharacter() {
            var editor = new Editor();
            editor.setCursorPosition(0, 0);
            editor.handleInput("a");
            assertEquals("a", editor.getText());
            assertArrayEquals(new int[]{0, 1}, editor.getCursorPosition());
        }

        @Test
        void insertMultipleCharacters() {
            var editor = new Editor();
            editor.setCursorPosition(0, 0);
            editor.handleInput("h");
            editor.handleInput("i");
            assertEquals("hi", editor.getText());
        }

        @Test
        void insertAtMiddle() {
            var editor = new Editor("ac");
            editor.setCursorPosition(0, 1);
            editor.handleInput("b");
            assertEquals("abc", editor.getText());
        }

        @Test
        void controlCharsIgnored() {
            var editor = new Editor("test");
            int beforeLen = editor.getText().length();
            editor.handleInput("\033[X"); // Unknown escape
            assertEquals(beforeLen, editor.getText().length());
        }
    }

    // -------------------------------------------------------------------
    // New line / Enter
    // -------------------------------------------------------------------

    @Nested
    class NewLine {

        @Test
        void enterSplitsLine() {
            var editor = new Editor("hello world");
            editor.setCursorPosition(0, 5);
            editor.handleInput(KEY_ENTER);
            assertEquals("hello\n world", editor.getText());
            assertArrayEquals(new int[]{1, 0}, editor.getCursorPosition());
        }

        @Test
        void enterAtBeginning() {
            var editor = new Editor("hello");
            editor.setCursorPosition(0, 0);
            editor.handleInput(KEY_ENTER);
            assertEquals("\nhello", editor.getText());
        }

        @Test
        void enterAtEnd() {
            var editor = new Editor("hello");
            editor.setCursorPosition(0, 5);
            editor.handleInput(KEY_ENTER);
            assertEquals("hello\n", editor.getText());
        }
    }

    // -------------------------------------------------------------------
    // Backspace and Delete
    // -------------------------------------------------------------------

    @Nested
    class Deletion {

        @Test
        void backspaceDeletesCharBefore() {
            var editor = new Editor("abc");
            editor.setCursorPosition(0, 2);
            editor.handleInput(KEY_BACKSPACE);
            assertEquals("ac", editor.getText());
            assertArrayEquals(new int[]{0, 1}, editor.getCursorPosition());
        }

        @Test
        void backspaceAtLineStartMergesLines() {
            var editor = new Editor("line1\nline2");
            editor.setCursorPosition(1, 0);
            editor.handleInput(KEY_BACKSPACE);
            assertEquals("line1line2", editor.getText());
            assertArrayEquals(new int[]{0, 5}, editor.getCursorPosition());
        }

        @Test
        void backspaceAtDocStartDoesNothing() {
            var editor = new Editor("abc");
            editor.setCursorPosition(0, 0);
            editor.handleInput(KEY_BACKSPACE);
            assertEquals("abc", editor.getText());
        }

        @Test
        void deleteRemovesCharAfter() {
            var editor = new Editor("abc");
            editor.setCursorPosition(0, 1);
            editor.handleInput(KEY_DELETE);
            assertEquals("ac", editor.getText());
            assertArrayEquals(new int[]{0, 1}, editor.getCursorPosition());
        }

        @Test
        void deleteAtLineEndMergesLines() {
            var editor = new Editor("line1\nline2");
            editor.setCursorPosition(0, 5);
            editor.handleInput(KEY_DELETE);
            assertEquals("line1line2", editor.getText());
        }

        @Test
        void deleteAtDocEndDoesNothing() {
            var editor = new Editor("abc");
            editor.setCursorPosition(0, 3);
            editor.handleInput(KEY_DELETE);
            assertEquals("abc", editor.getText());
        }
    }

    // -------------------------------------------------------------------
    // Cursor movement
    // -------------------------------------------------------------------

    @Nested
    class CursorMovement {

        @Test
        void leftMovement() {
            var editor = new Editor("abc");
            editor.setCursorPosition(0, 2);
            editor.handleInput(KEY_LEFT);
            assertArrayEquals(new int[]{0, 1}, editor.getCursorPosition());
        }

        @Test
        void rightMovement() {
            var editor = new Editor("abc");
            editor.setCursorPosition(0, 1);
            editor.handleInput(KEY_RIGHT);
            assertArrayEquals(new int[]{0, 2}, editor.getCursorPosition());
        }

        @Test
        void leftAtLineStartMovesToPreviousLine() {
            var editor = new Editor("abc\ndef");
            editor.setCursorPosition(1, 0);
            editor.handleInput(KEY_LEFT);
            assertArrayEquals(new int[]{0, 3}, editor.getCursorPosition());
        }

        @Test
        void rightAtLineEndMovesToNextLine() {
            var editor = new Editor("abc\ndef");
            editor.setCursorPosition(0, 3);
            editor.handleInput(KEY_RIGHT);
            assertArrayEquals(new int[]{1, 0}, editor.getCursorPosition());
        }

        @Test
        void leftAtDocStartStays() {
            var editor = new Editor("abc");
            editor.setCursorPosition(0, 0);
            editor.handleInput(KEY_LEFT);
            assertArrayEquals(new int[]{0, 0}, editor.getCursorPosition());
        }

        @Test
        void rightAtDocEndStays() {
            var editor = new Editor("abc");
            editor.setCursorPosition(0, 3);
            editor.handleInput(KEY_RIGHT);
            assertArrayEquals(new int[]{0, 3}, editor.getCursorPosition());
        }

        @Test
        void upMovement() {
            var editor = new Editor("line1\nline2");
            editor.setCursorPosition(1, 3);
            editor.handleInput(KEY_UP);
            assertArrayEquals(new int[]{0, 3}, editor.getCursorPosition());
        }

        @Test
        void downMovement() {
            var editor = new Editor("line1\nline2");
            editor.setCursorPosition(0, 3);
            editor.handleInput(KEY_DOWN);
            assertArrayEquals(new int[]{1, 3}, editor.getCursorPosition());
        }

        @Test
        void upAtFirstLineStays() {
            var editor = new Editor("abc");
            editor.setCursorPosition(0, 2);
            editor.handleInput(KEY_UP);
            assertArrayEquals(new int[]{0, 2}, editor.getCursorPosition());
        }

        @Test
        void downAtLastLineStays() {
            var editor = new Editor("abc");
            editor.setCursorPosition(0, 2);
            editor.handleInput(KEY_DOWN);
            assertArrayEquals(new int[]{0, 2}, editor.getCursorPosition());
        }

        @Test
        void homeMovesToLineStart() {
            var editor = new Editor("hello");
            editor.setCursorPosition(0, 3);
            editor.handleInput(KEY_HOME);
            assertArrayEquals(new int[]{0, 0}, editor.getCursorPosition());
        }

        @Test
        void endMovesToLineEnd() {
            var editor = new Editor("hello");
            editor.setCursorPosition(0, 0);
            editor.handleInput(KEY_END);
            assertArrayEquals(new int[]{0, 5}, editor.getCursorPosition());
        }

        @Test
        void ctrlAMovesToLineStart() {
            var editor = new Editor("hello");
            editor.setCursorPosition(0, 3);
            editor.handleInput(KEY_CTRL_A);
            assertArrayEquals(new int[]{0, 0}, editor.getCursorPosition());
        }

        @Test
        void ctrlEMovesToLineEnd() {
            var editor = new Editor("hello");
            editor.setCursorPosition(0, 0);
            editor.handleInput(KEY_CTRL_E);
            assertArrayEquals(new int[]{0, 5}, editor.getCursorPosition());
        }

        @Test
        void verticalMovementPreservesVisualColumn() {
            var editor = new Editor("abcdef\nab\nabcdef");
            editor.setCursorPosition(0, 5);
            editor.handleInput(KEY_DOWN); // line 1 has only 2 chars, clamp to 2
            assertArrayEquals(new int[]{1, 2}, editor.getCursorPosition());
            editor.handleInput(KEY_DOWN); // line 2 has 6 chars, restore to 5
            assertArrayEquals(new int[]{2, 5}, editor.getCursorPosition());
        }
    }

    // -------------------------------------------------------------------
    // Word movement
    // -------------------------------------------------------------------

    @Nested
    class WordMovement {

        @Test
        void wordForward() {
            var editor = new Editor("hello world foo");
            editor.setCursorPosition(0, 0);
            editor.handleInput(KEY_ALT_RIGHT);
            // Skip "hello", cursor after "hello"
            assertArrayEquals(new int[]{0, 5}, editor.getCursorPosition());
        }

        @Test
        void wordBackward() {
            var editor = new Editor("hello world foo");
            editor.setCursorPosition(0, 11);
            editor.handleInput(KEY_ALT_LEFT);
            // Skip back past "world" to position 6
            assertArrayEquals(new int[]{0, 6}, editor.getCursorPosition());
        }

        @Test
        void wordForwardSkipsWhitespace() {
            var editor = new Editor("hello   world");
            editor.setCursorPosition(0, 5);
            editor.handleInput(KEY_ALT_RIGHT);
            // Skip whitespace, then "world"
            assertArrayEquals(new int[]{0, 13}, editor.getCursorPosition());
        }

        @Test
        void wordBackwardSkipsWhitespace() {
            var editor = new Editor("hello   world");
            editor.setCursorPosition(0, 8);
            editor.handleInput(KEY_ALT_LEFT);
            // Skip whitespace back, then "hello" → position 0
            assertArrayEquals(new int[]{0, 0}, editor.getCursorPosition());
        }

        @Test
        void wordForwardAtEnd() {
            var editor = new Editor("abc\ndef");
            editor.setCursorPosition(0, 3);
            editor.handleInput(KEY_ALT_RIGHT);
            // Moves to start of next line
            assertArrayEquals(new int[]{1, 0}, editor.getCursorPosition());
        }

        @Test
        void wordBackwardAtStart() {
            var editor = new Editor("abc\ndef");
            editor.setCursorPosition(1, 0);
            editor.handleInput(KEY_ALT_LEFT);
            // Moves to end of previous line
            assertArrayEquals(new int[]{0, 3}, editor.getCursorPosition());
        }
    }

    // -------------------------------------------------------------------
    // Kill ring: Ctrl+K, Ctrl+U, Ctrl+W, Alt+D
    // -------------------------------------------------------------------

    @Nested
    class KillRingOperations {

        @Test
        void ctrlKDeletesFromCursorToLineEnd() {
            var editor = new Editor("hello world");
            editor.setCursorPosition(0, 5);
            editor.handleInput(KEY_CTRL_K);
            assertEquals("hello", editor.getText());
        }

        @Test
        void ctrlUDeletesFromLineStartToCursor() {
            var editor = new Editor("hello world");
            editor.setCursorPosition(0, 5);
            editor.handleInput(KEY_CTRL_U);
            assertEquals(" world", editor.getText());
            assertArrayEquals(new int[]{0, 0}, editor.getCursorPosition());
        }

        @Test
        void ctrlWDeletesWordBackward() {
            var editor = new Editor("hello world");
            editor.setCursorPosition(0, 11);
            editor.handleInput(KEY_CTRL_W);
            assertEquals("hello ", editor.getText());
        }

        @Test
        void altDDeletesWordForward() {
            var editor = new Editor("hello world");
            editor.setCursorPosition(0, 0);
            editor.handleInput(KEY_ALT_D);
            assertEquals(" world", editor.getText());
        }

        @Test
        void ctrlYYanksKilledText() {
            var editor = new Editor("hello world");
            editor.setCursorPosition(0, 5);
            editor.handleInput(KEY_CTRL_K); // kills " world"
            editor.setCursorPosition(0, 0);
            editor.handleInput(KEY_CTRL_Y); // yanks " world" at position 0
            assertEquals(" worldhello", editor.getText());
        }

        @Test
        void ctrlYWhenEmptyKillRingDoesNothing() {
            var editor = new Editor("hello");
            editor.setCursorPosition(0, 5);
            editor.handleInput(KEY_CTRL_Y);
            assertEquals("hello", editor.getText());
        }

        @Test
        void consecutiveKillsAccumulate() {
            var editor = new Editor("abc def ghi");
            editor.setCursorPosition(0, 7);
            editor.handleInput(KEY_CTRL_K); // kills " ghi" → kill ring: [" ghi"]
            // cursor stays at 7 but line is now "abc def"
            editor.handleInput(KEY_CTRL_K); // at end of line, merges next (no next line)
            // Now yank should give accumulated text
            editor.setCursorPosition(0, 0);
            editor.handleInput(KEY_CTRL_Y);
            assertEquals(" ghiabc def", editor.getText());
        }
    }

    // -------------------------------------------------------------------
    // Yank-pop
    // -------------------------------------------------------------------

    @Nested
    class YankPop {

        @Test
        void altYCyclesThroughKillRing() {
            var editor = new Editor("aaa bbb ccc");
            editor.setCursorPosition(0, 0);
            editor.handleInput(KEY_CTRL_K); // kill "aaa bbb ccc"
            editor.setText("xxx yyy");
            editor.setCursorPosition(0, 0);
            editor.handleInput(KEY_CTRL_K); // kill "xxx yyy"

            // Kill ring: ["aaa bbb ccc", "xxx yyy"]
            editor.setText("");
            editor.setCursorPosition(0, 0);
            editor.handleInput(KEY_CTRL_Y); // yanks "xxx yyy"
            assertEquals("xxx yyy", editor.getText());

            editor.handleInput(KEY_ALT_Y); // cycles to "aaa bbb ccc"
            assertEquals("aaa bbb ccc", editor.getText());
        }

        @Test
        void altYWithoutPriorYankDoesNothing() {
            var editor = new Editor("test");
            editor.setCursorPosition(0, 0);
            editor.handleInput(KEY_ALT_Y);
            assertEquals("test", editor.getText());
        }
    }

    // -------------------------------------------------------------------
    // Undo
    // -------------------------------------------------------------------

    @Nested
    class Undo {

        @Test
        void undoReversesCharacterInsertion() {
            var editor = new Editor();
            editor.setCursorPosition(0, 0);
            editor.handleInput("a");
            editor.handleInput(" "); // space breaks coalescing
            editor.handleInput(KEY_CTRL_Z); // undo space
            assertEquals("a", editor.getText());
        }

        @Test
        void undoReversesBackspace() {
            var editor = new Editor("abc");
            editor.setCursorPosition(0, 3);
            editor.handleInput(KEY_BACKSPACE);
            assertEquals("ab", editor.getText());
            editor.handleInput(KEY_CTRL_Z);
            assertEquals("abc", editor.getText());
        }

        @Test
        void undoReversesNewLine() {
            var editor = new Editor("abc");
            editor.setCursorPosition(0, 1);
            editor.handleInput(KEY_ENTER);
            assertEquals("a\nbc", editor.getText());
            editor.handleInput(KEY_CTRL_Z);
            assertEquals("abc", editor.getText());
        }

        @Test
        void undoReversesKill() {
            var editor = new Editor("hello world");
            editor.setCursorPosition(0, 5);
            editor.handleInput(KEY_CTRL_K);
            assertEquals("hello", editor.getText());
            editor.handleInput(KEY_CTRL_Z);
            assertEquals("hello world", editor.getText());
        }

        @Test
        void multipleUndos() {
            var editor = new Editor();
            editor.setCursorPosition(0, 0);
            editor.handleInput("a");
            editor.handleInput(" ");
            editor.handleInput("b");
            editor.handleInput(" ");
            // Text: "a b "
            // Undo groups: [""] (before "a"), ["a"] (before " b"), ["a b"] (before final " ")
            // Whitespace pushes snapshot, then "b" coalesces with " " into one group
            editor.handleInput(KEY_CTRL_Z); // undo final " "
            assertEquals("a b", editor.getText());
            editor.handleInput(KEY_CTRL_Z); // undo " b" (space+b coalesced)
            assertEquals("a", editor.getText());
            editor.handleInput(KEY_CTRL_Z); // undo "a"
            assertEquals("", editor.getText());
        }

        @Test
        void undoWhenEmptyDoesNothing() {
            var editor = new Editor("hello");
            editor.handleInput(KEY_CTRL_Z);
            assertEquals("hello", editor.getText());
        }

        @Test
        void wordCharactersCoalesce() {
            var editor = new Editor();
            editor.setCursorPosition(0, 0);
            editor.handleInput("h");
            editor.handleInput("e");
            editor.handleInput("l");
            editor.handleInput("l");
            editor.handleInput("o");
            // All these should coalesce into one undo unit
            editor.handleInput(KEY_CTRL_Z);
            assertEquals("", editor.getText());
        }
    }

    // -------------------------------------------------------------------
    // setText / getCursorPosition / setCursorPosition
    // -------------------------------------------------------------------

    @Nested
    class TextAndCursorAccessors {

        @Test
        void setTextResetsCursorToEnd() {
            var editor = new Editor();
            editor.setText("hello");
            assertArrayEquals(new int[]{0, 5}, editor.getCursorPosition());
        }

        @Test
        void setTextMultiLine() {
            var editor = new Editor();
            editor.setText("line1\nline2");
            assertArrayEquals(new int[]{1, 5}, editor.getCursorPosition());
        }

        @Test
        void setCursorPositionClamped() {
            var editor = new Editor("hi");
            editor.setCursorPosition(99, 99);
            assertArrayEquals(new int[]{0, 2}, editor.getCursorPosition());
        }

        @Test
        void setCursorPositionNegativeClamped() {
            var editor = new Editor("hi");
            editor.setCursorPosition(-1, -1);
            assertArrayEquals(new int[]{0, 0}, editor.getCursorPosition());
        }

        @Test
        void getLinesReturnsDefensiveCopy() {
            var editor = new Editor("a\nb");
            List<String> lines = editor.getLines();
            lines.add("c");
            assertEquals(2, editor.getLines().size());
        }
    }

    // -------------------------------------------------------------------
    // Rendering
    // -------------------------------------------------------------------

    @Nested
    class Rendering {

        @Test
        void emptyEditorRenders() {
            var editor = new Editor();
            editor.setFocused(true);
            List<String> lines = editor.render(40);
            assertFalse(lines.isEmpty());
        }

        @Test
        void renderShowsText() {
            var editor = new Editor("hello");
            editor.setFocused(false);
            List<String> lines = editor.render(40);
            assertTrue(lines.get(0).contains("hello"));
        }

        @Test
        void renderWithCursorShowsInverseVideo() {
            var editor = new Editor("abc");
            editor.setFocused(true);
            editor.setCursorPosition(0, 1);
            List<String> lines = editor.render(40);
            // Should contain inverse video escape code
            assertTrue(lines.get(0).contains("\033[7m"), "Expected inverse video marker");
        }

        @Test
        void multiLineRender() {
            var editor = new Editor("line1\nline2\nline3");
            List<String> lines = editor.render(40);
            assertEquals(3, lines.size());
        }

        @Test
        void wordWrapProducesMultipleLines() {
            var editor = new Editor("this is a very long line that should definitely wrap at some point");
            editor.setFocused(false);
            List<String> lines = editor.render(20);
            assertTrue(lines.size() > 1, "Expected word wrapping to produce multiple lines");
        }
    }

    // -------------------------------------------------------------------
    // Focusable
    // -------------------------------------------------------------------

    @Nested
    class FocusableTests {

        @Test
        void defaultNotFocused() {
            var editor = new Editor();
            assertFalse(editor.isFocused());
        }

        @Test
        void setFocused() {
            var editor = new Editor();
            editor.setFocused(true);
            assertTrue(editor.isFocused());
        }

        @Test
        void isFocusable() {
            var editor = new Editor();
            assertTrue(Focusable.isFocusable(editor));
        }
    }

    // -------------------------------------------------------------------
    // onChange callback
    // -------------------------------------------------------------------

    @Nested
    class OnChangeCallback {

        @Test
        void onChangeFiresOnInsertion() {
            var editor = new Editor();
            editor.setCursorPosition(0, 0);
            AtomicReference<String> changed = new AtomicReference<>();
            editor.setOnChange(changed::set);
            editor.handleInput("a");
            assertEquals("a", changed.get());
        }

        @Test
        void onChangeFiresOnDelete() {
            var editor = new Editor("ab");
            editor.setCursorPosition(0, 2);
            AtomicReference<String> changed = new AtomicReference<>();
            editor.setOnChange(changed::set);
            editor.handleInput(KEY_BACKSPACE);
            assertEquals("a", changed.get());
        }
    }
}
