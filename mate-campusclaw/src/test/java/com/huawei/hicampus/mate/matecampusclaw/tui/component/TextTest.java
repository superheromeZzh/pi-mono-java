package com.huawei.hicampus.mate.matecampusclaw.tui.component;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.function.UnaryOperator;

import com.huawei.hicampus.mate.matecampusclaw.tui.ansi.AnsiUtils;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class TextTest {

    private static final String RED = "\033[31m";
    private static final String BG_BLUE = "\033[44m";
    private static final String RESET = "\033[0m";

    @Nested
    class BasicRendering {

        @Test
        void simpleText() {
            var text = new Text("hello");
            List<String> lines = text.render(20);
            assertEquals(1, lines.size());
            assertTrue(lines.get(0).contains("hello"));
        }

        @Test
        void emptyTextReturnsNoLines() {
            var text = new Text("");
            assertTrue(text.render(20).isEmpty());
        }

        @Test
        void blankTextReturnsNoLines() {
            var text = new Text("   ");
            assertTrue(text.render(20).isEmpty());
        }

        @Test
        void nullTextReturnsNoLines() {
            var text = new Text(null);
            assertTrue(text.render(20).isEmpty());
        }

        @Test
        void linesPaddedToFullWidth() {
            var text = new Text("hi");
            List<String> lines = text.render(10);
            assertEquals(1, lines.size());
            assertEquals(10, AnsiUtils.visibleWidth(lines.get(0)));
        }

        @Test
        void multilineText() {
            var text = new Text("line1\nline2");
            List<String> lines = text.render(20);
            assertEquals(2, lines.size());
            assertTrue(lines.get(0).contains("line1"));
            assertTrue(lines.get(1).contains("line2"));
        }
    }

    @Nested
    class WordWrapping {

        @Test
        void wrapsLongText() {
            var text = new Text("hello world foo");
            List<String> lines = text.render(10);
            assertTrue(lines.size() >= 2, "Expected wrapping, got " + lines.size() + " lines");
            for (String line : lines) {
                assertTrue(AnsiUtils.visibleWidth(line) <= 10);
            }
        }

        @Test
        void wrapsWithAnsiCodes() {
            var text = new Text(RED + "hello world foo" + RESET);
            List<String> lines = text.render(10);
            assertTrue(lines.size() >= 2);
            for (String line : lines) {
                assertTrue(AnsiUtils.visibleWidth(line) <= 10);
            }
        }

        @Test
        void tabsNormalizedToSpaces() {
            var text = new Text("a\tb");
            List<String> lines = text.render(20);
            assertEquals(1, lines.size());
            // Tab replaced with 3 spaces: "a   b"
            assertTrue(lines.get(0).contains("a   b"));
        }
    }

    @Nested
    class Padding {

        @Test
        void horizontalPadding() {
            var text = new Text("hi", 2, 0);
            List<String> lines = text.render(20);
            assertEquals(1, lines.size());
            // Should start with 2 spaces (left padding)
            assertTrue(lines.get(0).startsWith("  "));
        }

        @Test
        void verticalPaddingAddsEmptyLines() {
            var text = new Text("hi", 0, 2);
            List<String> lines = text.render(20);
            // 2 top + 1 content + 2 bottom = 5
            assertEquals(5, lines.size());
        }

        @Test
        void bothPaddings() {
            var text = new Text("hi", 3, 1);
            List<String> lines = text.render(20);
            // 1 top + 1 content + 1 bottom = 3
            assertEquals(3, lines.size());
            // Content line starts with 3 spaces
            assertTrue(lines.get(1).startsWith("   "));
        }

        @Test
        void largePaddingClampsContentWidth() {
            var text = new Text("hello world", 15, 0);
            List<String> lines = text.render(20);
            // contentWidth = max(1, 20 - 30) = 1, so heavy wrapping
            assertTrue(lines.size() > 1);
        }
    }

    @Nested
    class Background {

        @Test
        void backgroundApplied() {
            UnaryOperator<String> bg = s -> BG_BLUE + s + RESET;
            var text = new Text("hi", 0, 0, bg);
            List<String> lines = text.render(10);
            assertEquals(1, lines.size());
            assertTrue(lines.get(0).contains(BG_BLUE));
        }

        @Test
        void backgroundOnPaddingLines() {
            UnaryOperator<String> bg = s -> BG_BLUE + s + RESET;
            var text = new Text("hi", 0, 1, bg);
            List<String> lines = text.render(10);
            // All lines (top pad, content, bottom pad) should have background
            for (String line : lines) {
                assertTrue(line.contains(BG_BLUE), "Line missing background: " + line);
            }
        }

        @Test
        void setBgFnInvalidatesCache() {
            var text = new Text("hi");
            text.render(10);

            UnaryOperator<String> bg = s -> BG_BLUE + s + RESET;
            text.setBgFn(bg);
            List<String> lines = text.render(10);
            assertTrue(lines.get(0).contains(BG_BLUE));
        }
    }

    @Nested
    class SetText {

        @Test
        void setTextUpdatesOutput() {
            var text = new Text("old");
            text.render(20);
            text.setText("new");
            List<String> lines = text.render(20);
            assertTrue(lines.get(0).contains("new"));
            assertFalse(lines.get(0).contains("old"));
        }

        @Test
        void setTextToNull() {
            var text = new Text("hello");
            text.setText(null);
            assertTrue(text.render(20).isEmpty());
        }

        @Test
        void getTextReturnsCurrentText() {
            var text = new Text("hello");
            assertEquals("hello", text.getText());
            text.setText("world");
            assertEquals("world", text.getText());
        }
    }

    @Nested
    class Caching {

        @Test
        void cachedResultReturnedOnSameInput() {
            var text = new Text("hello");
            List<String> first = text.render(20);
            List<String> second = text.render(20);
            assertSame(first, second);
        }

        @Test
        void cacheInvalidatedOnWidthChange() {
            var text = new Text("hello");
            List<String> first = text.render(20);
            List<String> second = text.render(30);
            assertNotSame(first, second);
        }

        @Test
        void invalidateForcesFreshRender() {
            var text = new Text("hello");
            List<String> first = text.render(20);
            text.invalidate();
            List<String> second = text.render(20);
            assertNotSame(first, second);
        }
    }
}
