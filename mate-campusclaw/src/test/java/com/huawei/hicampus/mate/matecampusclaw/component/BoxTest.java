package com.huawei.hicampus.mate.matecampusclaw.tui.component;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.function.UnaryOperator;

import com.huawei.hicampus.mate.matecampusclaw.tui.ansi.AnsiUtils;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class BoxTest {

    private static final String RED = "\033[31m";
    private static final String BG_BLUE = "\033[44m";
    private static final String RESET = "\033[0m";

    // -------------------------------------------------------------------
    // No border
    // -------------------------------------------------------------------

    @Nested
    class NoBorder {

        @Test
        void noBorderJustRendersChild() {
            var box = new Box(new Text("hello"));
            List<String> lines = box.render(20);
            assertEquals(1, lines.size());
            assertTrue(lines.get(0).contains("hello"));
        }

        @Test
        void noBorderWithPadding() {
            var box = new Box(new Text("hi"), null, 1, 2, 1, null, null);
            List<String> lines = box.render(20);
            // 1 top pad + 1 content + 1 bottom pad = 3
            assertEquals(3, lines.size());
            // Content line should have 2-char left padding
            assertTrue(lines.get(1).startsWith("  "));
        }

        @Test
        void noBorderWithBackground() {
            UnaryOperator<String> bg = s -> BG_BLUE + s + RESET;
            var box = new Box(new Text("hi"), null, 0, 0, 0, bg, null);
            List<String> lines = box.render(20);
            assertTrue(lines.get(0).contains(BG_BLUE));
        }
    }

    // -------------------------------------------------------------------
    // Single border
    // -------------------------------------------------------------------

    @Nested
    class SingleBorder {

        @Test
        void singleBorderStructure() {
            var box = new Box(new Text("hi"), BorderStyle.SINGLE);
            List<String> lines = box.render(20);
            // top border + content + bottom border = 3
            assertEquals(3, lines.size());

            // Top border: ┌──...──┐
            assertTrue(lines.get(0).startsWith("┌"));
            assertTrue(lines.get(0).endsWith("┐"));
            assertTrue(lines.get(0).contains("─"));

            // Content: │ ... │
            assertTrue(lines.get(1).startsWith("│"));
            assertTrue(lines.get(1).endsWith("│"));
            assertTrue(lines.get(1).contains("hi"));

            // Bottom border: └──...──┘
            assertTrue(lines.get(2).startsWith("└"));
            assertTrue(lines.get(2).endsWith("┘"));
        }

        @Test
        void singleBorderWidthMatchesTotal() {
            var box = new Box(new Text("hi"), BorderStyle.SINGLE);
            List<String> lines = box.render(20);
            for (String line : lines) {
                assertEquals(20, AnsiUtils.visibleWidth(line),
                        "Line width mismatch: '" + line + "'");
            }
        }

        @Test
        void singleBorderWithPadding() {
            var box = new Box(new Text("hi"), BorderStyle.SINGLE, 1);
            List<String> lines = box.render(20);
            // top border + 1 pad + content + 1 pad + bottom border = 5
            assertEquals(5, lines.size());

            // Border lines
            assertTrue(lines.get(0).startsWith("┌"));
            assertTrue(lines.get(4).startsWith("└"));

            // Padding lines have vertical borders
            assertTrue(lines.get(1).startsWith("│"));
            assertTrue(lines.get(1).endsWith("│"));
            assertTrue(lines.get(3).startsWith("│"));
        }
    }

    // -------------------------------------------------------------------
    // Double border
    // -------------------------------------------------------------------

    @Nested
    class DoubleBorder {

        @Test
        void doubleBorderCharacters() {
            var box = new Box(new Text("hi"), BorderStyle.DOUBLE);
            List<String> lines = box.render(20);
            assertTrue(lines.get(0).startsWith("╔"));
            assertTrue(lines.get(0).endsWith("╗"));
            assertTrue(lines.get(0).contains("═"));
            assertTrue(lines.get(1).startsWith("║"));
            assertTrue(lines.get(1).endsWith("║"));
            assertTrue(lines.get(2).startsWith("╚"));
            assertTrue(lines.get(2).endsWith("╝"));
        }
    }

    // -------------------------------------------------------------------
    // Rounded border
    // -------------------------------------------------------------------

    @Nested
    class RoundedBorder {

        @Test
        void roundedBorderCharacters() {
            var box = new Box(new Text("hi"), BorderStyle.ROUNDED);
            List<String> lines = box.render(20);
            assertTrue(lines.get(0).startsWith("╭"));
            assertTrue(lines.get(0).endsWith("╮"));
            assertTrue(lines.get(1).startsWith("│"));
            assertTrue(lines.get(2).startsWith("╰"));
            assertTrue(lines.get(2).endsWith("╯"));
        }
    }

    // -------------------------------------------------------------------
    // Border color
    // -------------------------------------------------------------------

    @Nested
    class BorderColor {

        @Test
        void borderColorApplied() {
            UnaryOperator<String> color = s -> RED + s + RESET;
            var box = new Box(new Text("hi"), BorderStyle.SINGLE, 0, 0, 0, null, color);
            List<String> lines = box.render(20);
            // Top border should contain RED
            assertTrue(lines.get(0).contains(RED));
            // Content vertical borders should contain RED
            assertTrue(lines.get(1).contains(RED));
        }

        @Test
        void borderColorAndBackgroundBothApplied() {
            UnaryOperator<String> borderColor = s -> RED + s + RESET;
            UnaryOperator<String> bg = s -> BG_BLUE + s + RESET;
            var box = new Box(new Text("hi"), BorderStyle.SINGLE, 0, 0, 0, bg, borderColor);
            List<String> lines = box.render(20);
            // Border has color
            assertTrue(lines.get(0).contains(RED));
            // Content has background
            assertTrue(lines.get(1).contains(BG_BLUE));
        }
    }

    // -------------------------------------------------------------------
    // Background
    // -------------------------------------------------------------------

    @Nested
    class Background {

        @Test
        void backgroundAppliedToContent() {
            UnaryOperator<String> bg = s -> BG_BLUE + s + RESET;
            var box = new Box(new Text("hello"), BorderStyle.SINGLE, 0, 0, 0, bg, null);
            List<String> lines = box.render(20);
            // Content line should have background
            assertTrue(lines.get(1).contains(BG_BLUE));
        }

        @Test
        void backgroundAppliedToPadding() {
            UnaryOperator<String> bg = s -> BG_BLUE + s + RESET;
            var box = new Box(new Text("hi"), BorderStyle.SINGLE, 0, 0, 1, bg, null);
            List<String> lines = box.render(20);
            // Padding lines (index 1 and 3) should have background
            assertTrue(lines.get(1).contains(BG_BLUE));
            assertTrue(lines.get(3).contains(BG_BLUE));
        }
    }

    // -------------------------------------------------------------------
    // Width consistency
    // -------------------------------------------------------------------

    @Nested
    class WidthConsistency {

        @Test
        void allLinesHaveSameVisibleWidth() {
            var box = new Box(new Text("hello world"), BorderStyle.SINGLE, 1);
            List<String> lines = box.render(30);
            for (String line : lines) {
                assertEquals(30, AnsiUtils.visibleWidth(line),
                        "Width mismatch on: '" + line + "'");
            }
        }

        @Test
        void allLinesWithDoubleBorder() {
            var box = new Box(new Text("test"), BorderStyle.DOUBLE, 2);
            List<String> lines = box.render(40);
            for (String line : lines) {
                assertEquals(40, AnsiUtils.visibleWidth(line),
                        "Width mismatch on: '" + line + "'");
            }
        }

        @Test
        void narrowWidth() {
            var box = new Box(new Text("hi"), BorderStyle.SINGLE);
            List<String> lines = box.render(6);
            for (String line : lines) {
                assertEquals(6, AnsiUtils.visibleWidth(line),
                        "Width mismatch on: '" + line + "'");
            }
        }
    }

    // -------------------------------------------------------------------
    // Multi-line child
    // -------------------------------------------------------------------

    @Nested
    class MultiLineChild {

        @Test
        void multiLineChildInBox() {
            var box = new Box(new Text("line1\nline2"), BorderStyle.SINGLE);
            List<String> lines = box.render(20);
            // top + 2 content + bottom = 4
            assertEquals(4, lines.size());
            assertTrue(lines.get(0).startsWith("┌"));
            assertTrue(lines.get(1).contains("line1"));
            assertTrue(lines.get(2).contains("line2"));
            assertTrue(lines.get(3).startsWith("└"));
        }

        @Test
        void containerChildInBox() {
            var container = new Container();
            container.addChild(new Text("first"));
            container.addChild(new Text("second"));
            var box = new Box(container, BorderStyle.ROUNDED);
            List<String> lines = box.render(20);
            // top + 2 content + bottom = 4
            assertEquals(4, lines.size());
            assertTrue(lines.get(0).startsWith("╭"));
            assertTrue(lines.get(3).startsWith("╰"));
        }
    }

    // -------------------------------------------------------------------
    // Invalidation
    // -------------------------------------------------------------------

    @Nested
    class Invalidation {

        @Test
        void invalidatePropagatesToChild() {
            var text = new Text("cached");
            var box = new Box(text, BorderStyle.SINGLE);

            List<String> first = text.render(20);
            box.invalidate();
            List<String> second = text.render(20);
            assertNotSame(first, second);
        }

        @Test
        void setBorderStyleChangesOutput() {
            var box = new Box(new Text("hi"), BorderStyle.SINGLE);
            List<String> single = box.render(20);
            box.setBorderStyle(BorderStyle.DOUBLE);
            List<String> dbl = box.render(20);
            assertNotEquals(single.get(0), dbl.get(0));
        }
    }

    // -------------------------------------------------------------------
    // Edge cases
    // -------------------------------------------------------------------

    @Nested
    class EdgeCases {

        @Test
        void emptyChildText() {
            var box = new Box(new Text(""), BorderStyle.SINGLE);
            List<String> lines = box.render(20);
            // Empty text returns no lines → box has top + bottom border only
            assertEquals(2, lines.size());
        }

        @Test
        void veryNarrowBoxStillRenders() {
            var box = new Box(new Text("hello"), BorderStyle.SINGLE);
            List<String> lines = box.render(4);
            // Should not crash; content width clamped to 1
            assertFalse(lines.isEmpty());
        }
    }
}
