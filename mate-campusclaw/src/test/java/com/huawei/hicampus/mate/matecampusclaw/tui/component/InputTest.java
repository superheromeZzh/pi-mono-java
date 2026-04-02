package com.huawei.hicampus.mate.matecampusclaw.tui.component;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import com.huawei.hicampus.mate.matecampusclaw.tui.Focusable;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class InputTest {

    // Key constants
    private static final String KEY_RIGHT = "\033[C";
    private static final String KEY_LEFT = "\033[D";
    private static final String KEY_HOME = "\033[H";
    private static final String KEY_END = "\033[F";
    private static final String KEY_DELETE = "\033[3~";
    private static final String KEY_BACKSPACE = "\177";
    private static final String KEY_ENTER = "\r";
    private static final String KEY_ESCAPE = "\033";
    private static final String KEY_CTRL_C = "\003";

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
    // Construction
    // -------------------------------------------------------------------

    @Nested
    class Construction {

        @Test
        void defaultEmpty() {
            var input = new Input();
            assertEquals("", input.getValue());
            assertEquals(0, input.getCursor());
        }

        @Test
        void withPlaceholder() {
            var input = new Input("Type here...");
            assertEquals("Type here...", input.getPlaceholder());
            assertEquals("", input.getValue());
        }
    }

    // -------------------------------------------------------------------
    // Character insertion
    // -------------------------------------------------------------------

    @Nested
    class CharacterInsertion {

        @Test
        void insertCharacters() {
            var input = new Input();
            input.handleInput("h");
            input.handleInput("i");
            assertEquals("hi", input.getValue());
            assertEquals(2, input.getCursor());
        }

        @Test
        void insertAtMiddle() {
            var input = new Input();
            input.setValue("ac");
            // Need to set cursor manually — setValue preserves cursor but clamps
            input.handleInput(KEY_HOME);
            input.handleInput(KEY_RIGHT);
            input.handleInput("b");
            assertEquals("abc", input.getValue());
        }

        @Test
        void controlCharsIgnored() {
            var input = new Input();
            input.handleInput("\033[Z"); // Unknown sequence
            assertEquals("", input.getValue());
        }
    }

    // -------------------------------------------------------------------
    // Backspace and Delete
    // -------------------------------------------------------------------

    @Nested
    class Deletion {

        @Test
        void backspace() {
            var input = new Input();
            input.setValue("abc");
            input.handleInput(KEY_END);
            input.handleInput(KEY_BACKSPACE);
            assertEquals("ab", input.getValue());
        }

        @Test
        void backspaceAtStartDoesNothing() {
            var input = new Input();
            input.setValue("abc");
            input.handleInput(KEY_HOME);
            input.handleInput(KEY_BACKSPACE);
            assertEquals("abc", input.getValue());
        }

        @Test
        void forwardDelete() {
            var input = new Input();
            input.setValue("abc");
            input.handleInput(KEY_HOME);
            input.handleInput(KEY_DELETE);
            assertEquals("bc", input.getValue());
        }

        @Test
        void forwardDeleteAtEndDoesNothing() {
            var input = new Input();
            input.setValue("abc");
            input.handleInput(KEY_END);
            input.handleInput(KEY_DELETE);
            assertEquals("abc", input.getValue());
        }
    }

    // -------------------------------------------------------------------
    // Cursor movement
    // -------------------------------------------------------------------

    @Nested
    class CursorMovement {

        @Test
        void leftRight() {
            var input = new Input();
            input.setValue("abc");
            input.handleInput(KEY_HOME);
            assertEquals(0, input.getCursor());
            input.handleInput(KEY_RIGHT);
            assertEquals(1, input.getCursor());
            input.handleInput(KEY_RIGHT);
            assertEquals(2, input.getCursor());
            input.handleInput(KEY_LEFT);
            assertEquals(1, input.getCursor());
        }

        @Test
        void homeEnd() {
            var input = new Input();
            input.setValue("hello");
            input.handleInput(KEY_HOME);
            assertEquals(0, input.getCursor());
            input.handleInput(KEY_END);
            assertEquals(5, input.getCursor());
        }

        @Test
        void ctrlACtrlE() {
            var input = new Input();
            input.setValue("hello");
            input.handleInput(KEY_CTRL_E);
            assertEquals(5, input.getCursor());
            input.handleInput(KEY_CTRL_A);
            assertEquals(0, input.getCursor());
        }

        @Test
        void leftAtStartStays() {
            var input = new Input();
            input.setValue("hi");
            input.handleInput(KEY_HOME);
            input.handleInput(KEY_LEFT);
            assertEquals(0, input.getCursor());
        }

        @Test
        void rightAtEndStays() {
            var input = new Input();
            input.setValue("hi");
            input.handleInput(KEY_END);
            input.handleInput(KEY_RIGHT);
            assertEquals(2, input.getCursor());
        }
    }

    // -------------------------------------------------------------------
    // Word movement
    // -------------------------------------------------------------------

    @Nested
    class WordMovement {

        @Test
        void wordForward() {
            var input = new Input();
            input.setValue("hello world foo");
            input.handleInput(KEY_HOME);
            input.handleInput(KEY_ALT_RIGHT);
            assertEquals(5, input.getCursor()); // after "hello"
        }

        @Test
        void wordBackward() {
            var input = new Input();
            input.setValue("hello world");
            input.handleInput(KEY_END);
            input.handleInput(KEY_ALT_LEFT);
            assertEquals(6, input.getCursor()); // before "world"
        }
    }

    // -------------------------------------------------------------------
    // Kill ring operations
    // -------------------------------------------------------------------

    @Nested
    class KillRingOperations {

        @Test
        void ctrlKDeletesFromCursorToEnd() {
            var input = new Input();
            input.setValue("hello world");
            input.handleInput(KEY_HOME);
            for (int i = 0; i < 5; i++) input.handleInput(KEY_RIGHT);
            input.handleInput(KEY_CTRL_K);
            assertEquals("hello", input.getValue());
        }

        @Test
        void ctrlUDeletesFromStartToCursor() {
            var input = new Input();
            input.setValue("hello world");
            input.handleInput(KEY_HOME);
            for (int i = 0; i < 5; i++) input.handleInput(KEY_RIGHT);
            input.handleInput(KEY_CTRL_U);
            assertEquals(" world", input.getValue());
            assertEquals(0, input.getCursor());
        }

        @Test
        void ctrlWDeletesWordBackward() {
            var input = new Input();
            input.setValue("hello world");
            input.handleInput(KEY_END);
            input.handleInput(KEY_CTRL_W);
            assertEquals("hello ", input.getValue());
        }

        @Test
        void altDDeletesWordForward() {
            var input = new Input();
            input.setValue("hello world");
            input.handleInput(KEY_HOME);
            input.handleInput(KEY_ALT_D);
            assertEquals(" world", input.getValue());
        }

        @Test
        void ctrlYYanksKilledText() {
            var input = new Input();
            input.setValue("hello world");
            input.handleInput(KEY_HOME);
            for (int i = 0; i < 5; i++) input.handleInput(KEY_RIGHT);
            input.handleInput(KEY_CTRL_K); // kills " world"
            input.handleInput(KEY_HOME);
            input.handleInput(KEY_CTRL_Y); // yanks " world" at start
            assertEquals(" worldhello", input.getValue());
        }

        @Test
        void altYCyclesKillRing() {
            var input = new Input();
            input.setValue("aaa");
            input.handleInput(KEY_END);
            input.handleInput(KEY_CTRL_U); // kills "aaa"
            input.setValue("bbb");
            input.handleInput(KEY_END);
            input.handleInput(KEY_CTRL_U); // kills "bbb"

            input.setValue("");
            input.handleInput(KEY_CTRL_Y); // yanks "bbb"
            assertEquals("bbb", input.getValue());
            input.handleInput(KEY_ALT_Y); // cycles to "aaa"
            assertEquals("aaa", input.getValue());
        }

        @Test
        void consecutiveKillsAccumulate() {
            var input = new Input();
            input.setValue("hello world");
            input.handleInput(KEY_HOME);
            for (int i = 0; i < 5; i++) input.handleInput(KEY_RIGHT);
            input.handleInput(KEY_CTRL_K); // kills " world"
            input.handleInput(KEY_CTRL_U); // kills "hello", accumulates with prepend

            // Now yank the accumulated text
            input.handleInput(KEY_CTRL_Y);
            assertEquals("hello world", input.getValue());
        }
    }

    // -------------------------------------------------------------------
    // Undo
    // -------------------------------------------------------------------

    @Nested
    class Undo {

        @Test
        void undoReversesInsertion() {
            var input = new Input();
            input.handleInput("a");
            input.handleInput(" "); // break coalescing
            input.handleInput(KEY_CTRL_Z); // undo space
            assertEquals("a", input.getValue());
        }

        @Test
        void undoReversesBackspace() {
            var input = new Input();
            input.setValue("abc");
            input.handleInput(KEY_END);
            input.handleInput(KEY_BACKSPACE);
            input.handleInput(KEY_CTRL_Z);
            assertEquals("abc", input.getValue());
        }

        @Test
        void undoReversesKill() {
            var input = new Input();
            input.setValue("hello world");
            input.handleInput(KEY_HOME);
            for (int i = 0; i < 5; i++) input.handleInput(KEY_RIGHT);
            input.handleInput(KEY_CTRL_K);
            input.handleInput(KEY_CTRL_Z);
            assertEquals("hello world", input.getValue());
        }

        @Test
        void wordCharactersCoalesce() {
            var input = new Input();
            input.handleInput("h");
            input.handleInput("e");
            input.handleInput("l");
            input.handleInput("l");
            input.handleInput("o");
            input.handleInput(KEY_CTRL_Z);
            assertEquals("", input.getValue());
        }

        @Test
        void undoWhenEmptyDoesNothing() {
            var input = new Input();
            input.setValue("test");
            input.handleInput(KEY_CTRL_Z);
            assertEquals("test", input.getValue());
        }
    }

    // -------------------------------------------------------------------
    // Submit and Escape
    // -------------------------------------------------------------------

    @Nested
    class SubmitAndEscape {

        @Test
        void enterTriggersOnSubmit() {
            var input = new Input();
            input.setValue("hello");
            AtomicReference<String> submitted = new AtomicReference<>();
            input.setOnSubmit(submitted::set);
            input.handleInput(KEY_ENTER);
            assertEquals("hello", submitted.get());
        }

        @Test
        void escapeTriggersOnEscape() {
            var input = new Input();
            var escaped = new AtomicReference<>(false);
            input.setOnEscape(() -> escaped.set(true));
            input.handleInput(KEY_ESCAPE);
            assertTrue(escaped.get());
        }

        @Test
        void ctrlCTriggersOnEscape() {
            var input = new Input();
            var escaped = new AtomicReference<>(false);
            input.setOnEscape(() -> escaped.set(true));
            input.handleInput(KEY_CTRL_C);
            assertTrue(escaped.get());
        }
    }

    // -------------------------------------------------------------------
    // Rendering
    // -------------------------------------------------------------------

    @Nested
    class Rendering {

        @Test
        void rendersPromptAndValue() {
            var input = new Input();
            input.setValue("hello");
            input.setFocused(false);
            List<String> lines = input.render(40);
            assertEquals(1, lines.size());
            assertTrue(lines.get(0).startsWith("> "));
            assertTrue(lines.get(0).contains("hello"));
        }

        @Test
        void rendersPlaceholderWhenEmptyAndNotFocused() {
            var input = new Input("Type here...");
            input.setFocused(false);
            List<String> lines = input.render(40);
            assertTrue(lines.get(0).contains("Type here..."));
        }

        @Test
        void doesNotRenderPlaceholderWhenFocused() {
            var input = new Input("Type here...");
            input.setFocused(true);
            List<String> lines = input.render(40);
            // When focused with empty value, should show cursor, not placeholder
            assertTrue(lines.get(0).contains("\033[7m"), "Should show cursor when focused");
        }

        @Test
        void rendersExactlyOneLine() {
            var input = new Input();
            input.setValue("some text");
            List<String> lines = input.render(40);
            assertEquals(1, lines.size());
        }

        @Test
        void cursorShownWhenFocused() {
            var input = new Input();
            input.setValue("abc");
            input.setFocused(true);
            List<String> lines = input.render(40);
            assertTrue(lines.get(0).contains("\033[7m"));
        }

        @Test
        void cursorNotShownWhenNotFocused() {
            var input = new Input();
            input.setValue("abc");
            input.setFocused(false);
            List<String> lines = input.render(40);
            assertFalse(lines.get(0).contains("\033[7m"));
        }
    }

    // -------------------------------------------------------------------
    // Focusable
    // -------------------------------------------------------------------

    @Nested
    class FocusableTests {

        @Test
        void defaultNotFocused() {
            var input = new Input();
            assertFalse(input.isFocused());
        }

        @Test
        void setFocused() {
            var input = new Input();
            input.setFocused(true);
            assertTrue(input.isFocused());
        }

        @Test
        void isFocusable() {
            assertTrue(Focusable.isFocusable(new Input()));
        }
    }

    // -------------------------------------------------------------------
    // setValue
    // -------------------------------------------------------------------

    @Nested
    class SetValue {

        @Test
        void setValueUpdatesCursor() {
            var input = new Input();
            input.setValue("hello");
            input.handleInput(KEY_END); // cursor at 5
            input.setValue("hi"); // shorter — cursor should clamp
            assertEquals("hi", input.getValue());
            assertTrue(input.getCursor() <= 2);
        }

        @Test
        void setNullValueTreatedAsEmpty() {
            var input = new Input();
            input.setValue(null);
            assertEquals("", input.getValue());
        }
    }
}
