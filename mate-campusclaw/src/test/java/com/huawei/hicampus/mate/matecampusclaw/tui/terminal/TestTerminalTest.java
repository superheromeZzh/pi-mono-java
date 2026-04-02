package com.huawei.hicampus.mate.matecampusclaw.tui.terminal;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class TestTerminalTest {

    private TestTerminal terminal;

    @BeforeEach
    void setUp() {
        terminal = new TestTerminal(80, 24);
    }

    // -------------------------------------------------------------------
    // Construction
    // -------------------------------------------------------------------

    @Nested
    class Construction {

        @Test
        void defaultSize() {
            var t = new TestTerminal();
            assertEquals(new TerminalSize(80, 24), t.getSize());
        }

        @Test
        void customSize() {
            var t = new TestTerminal(120, 40);
            assertEquals(new TerminalSize(120, 40), t.getSize());
        }

        @Test
        void initialStateNotRawNotClosed() {
            assertFalse(terminal.isRawMode());
            assertFalse(terminal.isClosed());
        }
    }

    // -------------------------------------------------------------------
    // Write
    // -------------------------------------------------------------------

    @Nested
    class Write {

        @Test
        void writeSingleString() {
            terminal.write("hello");
            assertEquals(List.of("hello"), terminal.getWrittenOutput());
        }

        @Test
        void writeMultipleStrings() {
            terminal.write("hello");
            terminal.write(" world");
            assertEquals(List.of("hello", " world"), terminal.getWrittenOutput());
        }

        @Test
        void getFullOutput() {
            terminal.write("hello");
            terminal.write(" world");
            assertEquals("hello world", terminal.getFullOutput());
        }

        @Test
        void clearOutput() {
            terminal.write("hello");
            terminal.clearOutput();
            assertTrue(terminal.getWrittenOutput().isEmpty());
        }

        @Test
        void writeAnsiCodes() {
            String ansi = "\033[31mred\033[0m";
            terminal.write(ansi);
            assertEquals(ansi, terminal.getFullOutput());
        }

        @Test
        void writeAfterCloseThrows() {
            terminal.close();
            assertThrows(IllegalStateException.class, () -> terminal.write("x"));
        }
    }

    // -------------------------------------------------------------------
    // Clear
    // -------------------------------------------------------------------

    @Nested
    class Clear {

        @Test
        void clearWritesEscapeSequence() {
            terminal.clear();
            assertTrue(terminal.getFullOutput().contains("\033[2J"));
            assertTrue(terminal.getFullOutput().contains("\033[H"));
        }

        @Test
        void clearResetsCursor() {
            terminal.moveCursor(5, 10);
            terminal.clear();
            assertEquals(0, terminal.getCursorRow());
            assertEquals(0, terminal.getCursorCol());
        }
    }

    // -------------------------------------------------------------------
    // Move cursor
    // -------------------------------------------------------------------

    @Nested
    class MoveCursor {

        @Test
        void moveCursorUpdatePosition() {
            terminal.moveCursor(3, 7);
            assertEquals(3, terminal.getCursorRow());
            assertEquals(7, terminal.getCursorCol());
        }

        @Test
        void moveCursorWritesAnsi() {
            terminal.moveCursor(3, 7);
            // ANSI CUP is 1-based: row 3 -> 4, col 7 -> 8
            assertTrue(terminal.getFullOutput().contains("\033[4;8H"));
        }

        @Test
        void moveCursorToOrigin() {
            terminal.moveCursor(5, 5);
            terminal.moveCursor(0, 0);
            assertEquals(0, terminal.getCursorRow());
            assertEquals(0, terminal.getCursorCol());
            assertTrue(terminal.getFullOutput().contains("\033[1;1H"));
        }
    }

    // -------------------------------------------------------------------
    // Size
    // -------------------------------------------------------------------

    @Nested
    class Size {

        @Test
        void getSizeReturnsInitialSize() {
            assertEquals(new TerminalSize(80, 24), terminal.getSize());
        }

        @Test
        void sizeAfterResize() {
            terminal.simulateResize(120, 40);
            assertEquals(new TerminalSize(120, 40), terminal.getSize());
        }
    }

    // -------------------------------------------------------------------
    // Input handling
    // -------------------------------------------------------------------

    @Nested
    class InputHandling {

        @Test
        void inputListenerReceivesData() {
            List<String> received = new ArrayList<>();
            terminal.onInput(received::add);
            terminal.simulateInput("a");
            assertEquals(List.of("a"), received);
        }

        @Test
        void multipleInputListeners() {
            List<String> first = new ArrayList<>();
            List<String> second = new ArrayList<>();
            terminal.onInput(first::add);
            terminal.onInput(second::add);
            terminal.simulateInput("key");
            assertEquals(List.of("key"), first);
            assertEquals(List.of("key"), second);
        }

        @Test
        void inputWithAnsiEscapeSequence() {
            List<String> received = new ArrayList<>();
            terminal.onInput(received::add);
            terminal.simulateInput("\033[A"); // Up arrow
            assertEquals(List.of("\033[A"), received);
        }

        @Test
        void multipleInputEvents() {
            List<String> received = new ArrayList<>();
            terminal.onInput(received::add);
            terminal.simulateInput("a");
            terminal.simulateInput("b");
            terminal.simulateInput("c");
            assertEquals(List.of("a", "b", "c"), received);
        }

        @Test
        void noListenersDoesNotThrow() {
            // Should not throw with no listeners
            assertDoesNotThrow(() -> terminal.simulateInput("x"));
        }
    }

    // -------------------------------------------------------------------
    // Resize handling
    // -------------------------------------------------------------------

    @Nested
    class ResizeHandling {

        @Test
        void resizeListenerNotified() {
            List<TerminalSize> sizes = new ArrayList<>();
            terminal.onResize(sizes::add);
            terminal.simulateResize(100, 30);
            assertEquals(1, sizes.size());
            assertEquals(new TerminalSize(100, 30), sizes.get(0));
        }

        @Test
        void multipleResizeListeners() {
            List<TerminalSize> first = new ArrayList<>();
            List<TerminalSize> second = new ArrayList<>();
            terminal.onResize(first::add);
            terminal.onResize(second::add);
            terminal.simulateResize(60, 20);
            assertEquals(1, first.size());
            assertEquals(1, second.size());
        }

        @Test
        void resizeUpdatesSize() {
            terminal.simulateResize(200, 50);
            assertEquals(new TerminalSize(200, 50), terminal.getSize());
        }

        @Test
        void multipleResizes() {
            List<TerminalSize> sizes = new ArrayList<>();
            terminal.onResize(sizes::add);
            terminal.simulateResize(100, 30);
            terminal.simulateResize(120, 40);
            assertEquals(2, sizes.size());
            assertEquals(new TerminalSize(120, 40), terminal.getSize());
        }
    }

    // -------------------------------------------------------------------
    // Raw mode
    // -------------------------------------------------------------------

    @Nested
    class RawMode {

        @Test
        void enterRawMode() {
            terminal.enterRawMode();
            assertTrue(terminal.isRawMode());
        }

        @Test
        void exitRawMode() {
            terminal.enterRawMode();
            terminal.exitRawMode();
            assertFalse(terminal.isRawMode());
        }

        @Test
        void enterExitMultipleTimes() {
            terminal.enterRawMode();
            assertTrue(terminal.isRawMode());
            terminal.exitRawMode();
            assertFalse(terminal.isRawMode());
            terminal.enterRawMode();
            assertTrue(terminal.isRawMode());
        }
    }

    // -------------------------------------------------------------------
    // Close
    // -------------------------------------------------------------------

    @Nested
    class Close {

        @Test
        void closeMarksTerminalClosed() {
            terminal.close();
            assertTrue(terminal.isClosed());
        }

        @Test
        void clearAfterCloseThrows() {
            terminal.close();
            assertThrows(IllegalStateException.class, () -> terminal.clear());
        }

        @Test
        void moveCursorAfterCloseThrows() {
            terminal.close();
            assertThrows(IllegalStateException.class, () -> terminal.moveCursor(0, 0));
        }

        @Test
        void enterRawModeAfterCloseThrows() {
            terminal.close();
            assertThrows(IllegalStateException.class, () -> terminal.enterRawMode());
        }
    }

    // -------------------------------------------------------------------
    // TerminalSize record
    // -------------------------------------------------------------------

    @Nested
    class TerminalSizeRecord {

        @Test
        void equality() {
            assertEquals(new TerminalSize(80, 24), new TerminalSize(80, 24));
        }

        @Test
        void inequality() {
            assertNotEquals(new TerminalSize(80, 24), new TerminalSize(120, 40));
        }

        @Test
        void accessors() {
            var size = new TerminalSize(100, 50);
            assertEquals(100, size.width());
            assertEquals(50, size.height());
        }
    }

    // -------------------------------------------------------------------
    // Integration: Write + Cursor + Clear
    // -------------------------------------------------------------------

    @Nested
    class Integration {

        @Test
        void writeMoveClearSequence() {
            terminal.write("hello");
            terminal.moveCursor(1, 0);
            terminal.write("world");
            terminal.clear();
            terminal.write("fresh");

            String full = terminal.getFullOutput();
            assertTrue(full.contains("hello"));
            assertTrue(full.contains("world"));
            assertTrue(full.contains("fresh"));
            assertTrue(full.contains("\033[2J"));
        }

        @Test
        void outputIsUnmodifiable() {
            terminal.write("test");
            assertThrows(UnsupportedOperationException.class,
                    () -> terminal.getWrittenOutput().add("hack"));
        }

        @Test
        void inputAndResizeListenersIndependent() {
            List<String> inputs = new ArrayList<>();
            List<TerminalSize> resizes = new ArrayList<>();
            terminal.onInput(inputs::add);
            terminal.onResize(resizes::add);

            terminal.simulateInput("key");
            terminal.simulateResize(100, 50);

            assertEquals(1, inputs.size());
            assertEquals(1, resizes.size());
        }
    }
}
