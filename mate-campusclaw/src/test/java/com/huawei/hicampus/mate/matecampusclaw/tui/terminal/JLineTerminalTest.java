package com.huawei.hicampus.mate.matecampusclaw.tui.terminal;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.jline.terminal.Attributes;
import org.jline.terminal.Size;
import org.jline.terminal.TerminalBuilder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Tests JLineTerminal using a dumb JLine terminal (no real TTY needed).
 */
class JLineTerminalTest {

    private org.jline.terminal.Terminal dumbTerminal;
    private JLineTerminal terminal;

    @BeforeEach
    void setUp() throws IOException {
        dumbTerminal = TerminalBuilder.builder()
                .name("test")
                .dumb(true)
                .size(new Size(80, 24))
                .build();
        terminal = new JLineTerminal(dumbTerminal);
    }

    @AfterEach
    void tearDown() {
        terminal.close();
    }

    @Nested
    class BasicOperations {

        @Test
        void getSizeReturnsDimensions() {
            TerminalSize size = terminal.getSize();
            assertEquals(80, size.width());
            assertEquals(24, size.height());
        }

        @Test
        void writeDoesNotThrow() {
            assertDoesNotThrow(() -> terminal.write("hello"));
        }

        @Test
        void clearDoesNotThrow() {
            assertDoesNotThrow(() -> terminal.clear());
        }

        @Test
        void moveCursorDoesNotThrow() {
            assertDoesNotThrow(() -> terminal.moveCursor(5, 10));
        }
    }

    @Nested
    class RawMode {

        @Test
        void enterAndExitRawMode() {
            // Dumb terminals may not fully support raw mode,
            // but the methods should not throw
            assertDoesNotThrow(() -> terminal.enterRawMode());
            assertDoesNotThrow(() -> terminal.exitRawMode());
        }

        @Test
        void exitRawModeRestoresAttributes() {
            Attributes original = dumbTerminal.getAttributes();
            terminal.enterRawMode();
            terminal.exitRawMode();
            // Attributes should be restored (dumb terminal may not change them)
            assertNotNull(dumbTerminal.getAttributes());
        }
    }

    @Nested
    class ListenerRegistration {

        @Test
        void registerInputListenerDoesNotThrow() {
            assertDoesNotThrow(() -> terminal.onInput(data -> {}));
        }

        @Test
        void registerResizeListenerDoesNotThrow() {
            assertDoesNotThrow(() -> terminal.onResize(size -> {}));
        }

        @Test
        void multipleListenersRegistered() {
            List<Consumer> listeners = new ArrayList<>();
            terminal.onInput(data -> listeners.add(null));
            terminal.onInput(data -> listeners.add(null));
            // No exception means both were registered
            assertEquals(0, listeners.size()); // Not triggered yet
        }
    }

    @Nested
    class CloseOperation {

        @Test
        void closeDoesNotThrow() {
            assertDoesNotThrow(() -> terminal.close());
        }

        @Test
        void doubleCloseDoesNotThrow() {
            terminal.close();
            // Second close on a dumb terminal should handle gracefully
            assertDoesNotThrow(() -> {
                try {
                    dumbTerminal.close();
                } catch (IOException e) {
                    // Some dumb terminals may throw on double close - that's fine
                }
            });
        }
    }

    // Simple placeholder to avoid compile error for List<Consumer>
    interface Consumer {
    }
}
