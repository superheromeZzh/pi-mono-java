package com.huawei.hicampus.mate.matecampusclaw.tui.component;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import com.huawei.hicampus.mate.matecampusclaw.tui.Focusable;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Comprehensive tests for {@link SelectList}.
 */
class SelectListTest {

    // Plain theme — identity functions, no ANSI codes
    private static final SelectListTheme plainTheme = SelectListTheme.plainTheme();

    // Key constants
    private static final String KEY_UP = "\033[A";
    private static final String KEY_DOWN = "\033[B";
    private static final String KEY_ENTER = "\r";
    private static final String KEY_ESCAPE = "\033";
    private static final String KEY_CTRL_C = "\003";

    private static List<String> items(String... values) {
        return List.of(values);
    }

    private static SelectList<String> createList(List<String> items) {
        return new SelectList<>(items, s -> s, Integer.MAX_VALUE, plainTheme);
    }

    private static SelectList<String> createList(List<String> items, int maxHeight) {
        return new SelectList<>(items, s -> s, maxHeight, plainTheme);
    }

    // -------------------------------------------------------------------
    // Construction & accessors
    // -------------------------------------------------------------------

    @Nested
    class Construction {

        @Test
        void defaultConstructionWithItems() {
            var list = createList(items("a", "b", "c"));
            assertEquals(3, list.getItems().size());
            assertEquals(0, list.getSelectedIndex());
            assertEquals("a", list.getSelectedItem());
        }

        @Test
        void emptyListReturnsNullSelectedItem() {
            var list = createList(List.of());
            assertNull(list.getSelectedItem());
            assertEquals(0, list.getSelectedIndex());
        }

        @Test
        void nullItemsListTreatedAsEmpty() {
            var list = new SelectList<String>(null, s -> s, 5, plainTheme);
            assertNull(list.getSelectedItem());
            assertTrue(list.getItems().isEmpty());
        }

        @Test
        void itemsListIsDefensivelyCopied() {
            var mutableList = new ArrayList<>(List.of("a", "b"));
            var list = new SelectList<>(mutableList, s -> s, 5, plainTheme);
            mutableList.add("c");
            assertEquals(2, list.getItems().size()); // Not affected by external mutation
        }

        @Test
        void getItemsReturnsUnmodifiableView() {
            var list = createList(items("a", "b"));
            assertThrows(UnsupportedOperationException.class, () -> list.getItems().add("c"));
        }
    }

    // -------------------------------------------------------------------
    // Selection
    // -------------------------------------------------------------------

    @Nested
    class Selection {

        @Test
        void setSelectedIndexClampsToValidRange() {
            var list = createList(items("a", "b", "c"));
            list.setSelectedIndex(5);
            assertEquals(2, list.getSelectedIndex());
            assertEquals("c", list.getSelectedItem());
        }

        @Test
        void setSelectedIndexClampsNegative() {
            var list = createList(items("a", "b", "c"));
            list.setSelectedIndex(-1);
            assertEquals(0, list.getSelectedIndex());
        }

        @Test
        void setSelectedIndexOnEmptyList() {
            var list = createList(List.of());
            list.setSelectedIndex(3);
            assertEquals(0, list.getSelectedIndex());
        }

        @Test
        void setItemsResetsSelection() {
            var list = createList(items("a", "b", "c"));
            list.setSelectedIndex(2);
            list.setItems(List.of("x", "y"));
            assertEquals(0, list.getSelectedIndex());
            assertEquals("x", list.getSelectedItem());
        }
    }

    // -------------------------------------------------------------------
    // Keyboard navigation
    // -------------------------------------------------------------------

    @Nested
    class Navigation {

        @Test
        void downArrowMovesSelectionDown() {
            var list = createList(items("a", "b", "c"));
            list.handleInput(KEY_DOWN);
            assertEquals(1, list.getSelectedIndex());
            assertEquals("b", list.getSelectedItem());
        }

        @Test
        void upArrowMovesSelectionUp() {
            var list = createList(items("a", "b", "c"));
            list.setSelectedIndex(2);
            list.handleInput(KEY_UP);
            assertEquals(1, list.getSelectedIndex());
        }

        @Test
        void downArrowWrapsToTop() {
            var list = createList(items("a", "b", "c"));
            list.setSelectedIndex(2);
            list.handleInput(KEY_DOWN);
            assertEquals(0, list.getSelectedIndex());
        }

        @Test
        void upArrowWrapsToBottom() {
            var list = createList(items("a", "b", "c"));
            // selectedIndex starts at 0
            list.handleInput(KEY_UP);
            assertEquals(2, list.getSelectedIndex());
        }

        @Test
        void navigateFullCycleDown() {
            var list = createList(items("a", "b", "c"));
            list.handleInput(KEY_DOWN); // 1
            list.handleInput(KEY_DOWN); // 2
            list.handleInput(KEY_DOWN); // 0 (wrap)
            assertEquals(0, list.getSelectedIndex());
        }

        @Test
        void navigateFullCycleUp() {
            var list = createList(items("a", "b", "c"));
            list.handleInput(KEY_UP); // 2 (wrap)
            list.handleInput(KEY_UP); // 1
            list.handleInput(KEY_UP); // 0
            assertEquals(0, list.getSelectedIndex());
        }

        @Test
        void inputOnEmptyListDoesNothing() {
            var list = createList(List.of());
            list.handleInput(KEY_DOWN);
            assertEquals(0, list.getSelectedIndex());
            list.handleInput(KEY_UP);
            assertEquals(0, list.getSelectedIndex());
        }

        @Test
        void singleItemNavigationStaysInPlace() {
            var list = createList(items("only"));
            list.handleInput(KEY_DOWN);
            assertEquals(0, list.getSelectedIndex()); // wraps back to 0
            list.handleInput(KEY_UP);
            assertEquals(0, list.getSelectedIndex()); // wraps back to 0
        }
    }

    // -------------------------------------------------------------------
    // Callbacks
    // -------------------------------------------------------------------

    @Nested
    class Callbacks {

        @Test
        void enterTriggersOnSelect() {
            var list = createList(items("a", "b", "c"));
            list.setSelectedIndex(1);
            AtomicReference<String> selected = new AtomicReference<>();
            list.setOnSelect(selected::set);
            list.handleInput(KEY_ENTER);
            assertEquals("b", selected.get());
        }

        @Test
        void escapeTriggersOnCancel() {
            var list = createList(items("a", "b"));
            var cancelled = new AtomicReference<>(false);
            list.setOnCancel(() -> cancelled.set(true));
            list.handleInput(KEY_ESCAPE);
            assertTrue(cancelled.get());
        }

        @Test
        void ctrlCTriggersOnCancel() {
            var list = createList(items("a", "b"));
            var cancelled = new AtomicReference<>(false);
            list.setOnCancel(() -> cancelled.set(true));
            list.handleInput(KEY_CTRL_C);
            assertTrue(cancelled.get());
        }

        @Test
        void navigationTriggersOnSelectionChange() {
            var list = createList(items("a", "b", "c"));
            AtomicReference<String> changed = new AtomicReference<>();
            list.setOnSelectionChange(changed::set);
            list.handleInput(KEY_DOWN);
            assertEquals("b", changed.get());
        }

        @Test
        void noCallbackDoesNotThrow() {
            var list = createList(items("a", "b"));
            // No callbacks set — should not throw
            assertDoesNotThrow(() -> {
                list.handleInput(KEY_ENTER);
                list.handleInput(KEY_ESCAPE);
                list.handleInput(KEY_CTRL_C);
                list.handleInput(KEY_DOWN);
            });
        }
    }

    // -------------------------------------------------------------------
    // Rendering basics
    // -------------------------------------------------------------------

    @Nested
    class BasicRendering {

        @Test
        void rendersAllItemsWithinMaxHeight() {
            var list = createList(items("alpha", "beta", "gamma"));
            List<String> lines = list.render(40);
            assertEquals(3, lines.size());
        }

        @Test
        void selectedItemHasArrowPrefix() {
            var list = createList(items("alpha", "beta"));
            List<String> lines = list.render(40);
            assertTrue(lines.get(0).startsWith("→ "), "Selected item should have arrow prefix");
            assertTrue(lines.get(1).startsWith("  "), "Non-selected item should have space prefix");
        }

        @Test
        void navigationChangesRenderedSelection() {
            var list = createList(items("alpha", "beta"));
            list.handleInput(KEY_DOWN);
            List<String> lines = list.render(40);
            assertTrue(lines.get(0).startsWith("  "), "First item should no longer be selected");
            assertTrue(lines.get(1).startsWith("→ "), "Second item should now be selected");
        }

        @Test
        void emptyListRendersNoItemsMessage() {
            var list = createList(List.of());
            List<String> lines = list.render(40);
            assertEquals(1, lines.size());
            assertTrue(lines.get(0).contains("No items"));
        }

        @Test
        void itemLabelsAreRendered() {
            var list = createList(items("foo", "bar", "baz"));
            List<String> lines = list.render(40);
            assertTrue(lines.get(0).contains("foo"));
            assertTrue(lines.get(1).contains("bar"));
            assertTrue(lines.get(2).contains("baz"));
        }

        @Test
        void customRenderItemFunction() {
            record Entry(String name, int count) {}
            var entries = List.of(new Entry("apples", 5), new Entry("oranges", 3));
            var list = new SelectList<>(entries, e -> e.name() + " (" + e.count() + ")", 10, plainTheme);
            List<String> lines = list.render(40);
            assertTrue(lines.get(0).contains("apples (5)"));
            assertTrue(lines.get(1).contains("oranges (3)"));
        }
    }

    // -------------------------------------------------------------------
    // Scrolling
    // -------------------------------------------------------------------

    @Nested
    class Scrolling {

        @Test
        void allItemsVisibleWhenWithinMaxHeight() {
            var list = createList(items("a", "b", "c"), 5);
            List<String> lines = list.render(40);
            // 3 items, no scroll indicator
            assertEquals(3, lines.size());
        }

        @Test
        void scrollIndicatorShownWhenExceedsMaxHeight() {
            var list = createList(items("a", "b", "c", "d", "e"), 3);
            List<String> lines = list.render(40);
            // 3 visible items + 1 scroll indicator
            assertEquals(4, lines.size());
            assertTrue(lines.get(3).contains("(1/5)"));
        }

        @Test
        void scrollIndicatorUpdatesWithSelection() {
            var list = createList(items("a", "b", "c", "d", "e"), 3);
            list.handleInput(KEY_DOWN);
            list.handleInput(KEY_DOWN);
            List<String> lines = list.render(40);
            // Scroll indicator should show (3/5)
            String lastLine = lines.get(lines.size() - 1);
            assertTrue(lastLine.contains("(3/5)"), "Expected (3/5) but got: " + lastLine);
        }

        @Test
        void scrollWindowCentersSelectedItem() {
            var list = createList(items("a", "b", "c", "d", "e", "f", "g"), 3);
            list.setSelectedIndex(3); // "d"
            List<String> lines = list.render(40);
            // Window should center around index 3: shows c(2), d(3), e(4)
            assertTrue(lines.get(0).contains("c"));
            assertTrue(lines.get(1).contains("d")); // selected
            assertTrue(lines.get(2).contains("e"));
        }

        @Test
        void scrollWindowClampedAtStart() {
            var list = createList(items("a", "b", "c", "d", "e"), 3);
            list.setSelectedIndex(0);
            List<String> lines = list.render(40);
            // Window starts at 0: shows a, b, c
            assertTrue(lines.get(0).contains("a"));
            assertTrue(lines.get(1).contains("b"));
            assertTrue(lines.get(2).contains("c"));
        }

        @Test
        void scrollWindowClampedAtEnd() {
            var list = createList(items("a", "b", "c", "d", "e"), 3);
            list.setSelectedIndex(4);
            List<String> lines = list.render(40);
            // Window ends at items.size(): shows c, d, e + scroll indicator
            assertTrue(lines.get(0).contains("c"));
            assertTrue(lines.get(1).contains("d"));
            assertTrue(lines.get(2).contains("e"));
        }

        @Test
        void maxHeightOfOneShowsSingleItem() {
            var list = createList(items("a", "b", "c"), 1);
            list.setSelectedIndex(1);
            List<String> lines = list.render(40);
            // 1 item + scroll indicator
            assertEquals(2, lines.size());
            assertTrue(lines.get(0).contains("b"));
            assertTrue(lines.get(1).contains("(2/3)"));
        }

        @Test
        void navigatingDownScrollsWindow() {
            var list = createList(items("a", "b", "c", "d", "e"), 3);
            // Move to last item
            list.setSelectedIndex(4);
            List<String> lines = list.render(40);
            // Last 3 items visible
            assertTrue(lines.get(2).contains("e"));
            assertTrue(lines.get(2).startsWith("→ "), "Last visible item should be selected");
        }
    }

    // -------------------------------------------------------------------
    // Truncation
    // -------------------------------------------------------------------

    @Nested
    class Truncation {

        @Test
        void longLabelIsTruncatedToFitWidth() {
            var list = createList(items("this-is-a-very-long-item-name-that-exceeds-terminal-width"));
            List<String> lines = list.render(20);
            assertEquals(1, lines.size());
            // The rendered line should not exceed 20 visible columns
            // (prefix "→ " is 2 chars, leaving 18 for the label)
            String line = lines.get(0);
            int visWidth = com.huawei.hicampus.mate.matecampusclaw.tui.ansi.AnsiUtils.visibleWidth(line);
            assertTrue(visWidth <= 20, "Line width " + visWidth + " exceeds max 20");
        }

        @Test
        void shortLabelIsNotTruncated() {
            var list = createList(items("hi"));
            List<String> lines = list.render(40);
            assertTrue(lines.get(0).contains("hi"));
        }
    }

    // -------------------------------------------------------------------
    // Focusable interface
    // -------------------------------------------------------------------

    @Nested
    class FocusableTests {

        @Test
        void defaultNotFocused() {
            var list = createList(items("a"));
            assertFalse(list.isFocused());
        }

        @Test
        void setFocused() {
            var list = createList(items("a"));
            list.setFocused(true);
            assertTrue(list.isFocused());
            list.setFocused(false);
            assertFalse(list.isFocused());
        }

        @Test
        void isFocusableReturnsTrue() {
            var list = createList(items("a"));
            assertTrue(Focusable.isFocusable(list));
        }
    }

    // -------------------------------------------------------------------
    // Theme
    // -------------------------------------------------------------------

    @Nested
    class ThemeTests {

        @Test
        void defaultThemeAppliesAnsiCodes() {
            var list = new SelectList<>(items("a", "b"), s -> s, 10, SelectListTheme.defaultTheme());
            List<String> lines = list.render(40);
            // Selected line should contain ANSI escape codes
            assertTrue(lines.get(0).contains("\033["), "Default theme should apply ANSI codes");
        }

        @Test
        void plainThemeProducesNoAnsiCodes() {
            var list = createList(items("a", "b"));
            List<String> lines = list.render(40);
            for (String line : lines) {
                assertFalse(line.contains("\033"), "Plain theme should not produce ANSI codes, got: " + line);
            }
        }

        @Test
        void scrollIndicatorUsesDimThemeWithDefaultTheme() {
            var list = new SelectList<>(items("a", "b", "c"), s -> s, 2, SelectListTheme.defaultTheme());
            List<String> lines = list.render(40);
            String scrollLine = lines.get(lines.size() - 1);
            assertTrue(scrollLine.contains("\033["), "Scroll indicator should be themed");
        }

        @Test
        void customThemeApplied() {
            var customTheme = SelectListTheme.builder()
                    .selectedText(text -> "[SEL]" + text + "[/SEL]")
                    .selectedPrefix(text -> "[PRE]" + text + "[/PRE]")
                    .build();
            var list = new SelectList<>(items("a", "b"), s -> s, 10, customTheme);
            List<String> lines = list.render(40);
            assertTrue(lines.get(0).contains("[SEL]"));
            assertTrue(lines.get(0).contains("[PRE]"));
        }
    }

    // -------------------------------------------------------------------
    // Caching
    // -------------------------------------------------------------------

    @Nested
    class Caching {

        @Test
        void sameParametersReturnsCachedResult() {
            var list = createList(items("a", "b"));
            List<String> first = list.render(40);
            List<String> second = list.render(40);
            assertSame(first, second, "Should return cached instance");
        }

        @Test
        void differentWidthInvalidatesCache() {
            var list = createList(items("a", "b"));
            List<String> first = list.render(40);
            List<String> second = list.render(50);
            assertNotSame(first, second);
        }

        @Test
        void navigationInvalidatesCache() {
            var list = createList(items("a", "b"));
            List<String> first = list.render(40);
            list.handleInput(KEY_DOWN);
            List<String> second = list.render(40);
            assertNotSame(first, second);
        }

        @Test
        void invalidateForcesCacheRefresh() {
            var list = createList(items("a", "b"));
            List<String> first = list.render(40);
            list.invalidate();
            List<String> second = list.render(40);
            assertNotSame(first, second);
        }
    }

    // -------------------------------------------------------------------
    // setMaxHeight / setTheme
    // -------------------------------------------------------------------

    @Nested
    class MutationMethods {

        @Test
        void setMaxHeightChangesVisibleCount() {
            var list = createList(items("a", "b", "c", "d"), 10);
            // All items visible, no scroll indicator
            assertEquals(4, list.render(40).size());

            list.setMaxHeight(2);
            // 2 items + scroll indicator
            assertEquals(3, list.render(40).size());
        }

        @Test
        void setThemeChangesRendering() {
            var list = createList(items("a"));
            List<String> plainLines = list.render(40);
            assertFalse(plainLines.get(0).contains("\033"));

            list.setTheme(SelectListTheme.defaultTheme());
            List<String> themedLines = list.render(40);
            assertTrue(themedLines.get(0).contains("\033"));
        }

        @Test
        void setItemsUpdatesRendering() {
            var list = createList(items("a", "b"));
            List<String> lines1 = list.render(40);
            assertTrue(lines1.get(0).contains("a"));

            list.setItems(List.of("x", "y", "z"));
            List<String> lines2 = list.render(40);
            assertEquals(3, lines2.size());
            assertTrue(lines2.get(0).contains("x"));
        }
    }

    // -------------------------------------------------------------------
    // Generic type support
    // -------------------------------------------------------------------

    @Nested
    class GenericTypes {

        @Test
        void integerItems() {
            var list = new SelectList<>(List.of(1, 2, 3), i -> "Item " + i, 10, plainTheme);
            assertEquals(1, list.getSelectedItem());
            List<String> lines = list.render(40);
            assertTrue(lines.get(0).contains("Item 1"));
        }

        @Test
        void recordItems() {
            record Fruit(String name, double price) {}
            var fruits = List.of(
                    new Fruit("Apple", 1.50),
                    new Fruit("Banana", 0.75),
                    new Fruit("Cherry", 3.00)
            );
            var list = new SelectList<>(fruits, f -> f.name() + " $" + f.price(), 10, plainTheme);
            assertEquals("Apple", list.getSelectedItem().name());
            List<String> lines = list.render(40);
            assertTrue(lines.get(1).contains("Banana $0.75"));
        }
    }

    // -------------------------------------------------------------------
    // Edge cases
    // -------------------------------------------------------------------

    @Nested
    class EdgeCases {

        @Test
        void unknownKeyInputIsIgnored() {
            var list = createList(items("a", "b"));
            list.handleInput("x");
            assertEquals(0, list.getSelectedIndex()); // unchanged
        }

        @Test
        void veryNarrowWidthStillRenders() {
            var list = createList(items("hello world"));
            List<String> lines = list.render(5);
            assertFalse(lines.isEmpty());
            int visWidth = com.huawei.hicampus.mate.matecampusclaw.tui.ansi.AnsiUtils.visibleWidth(lines.get(0));
            assertTrue(visWidth <= 5, "Line width " + visWidth + " exceeds max 5");
        }

        @Test
        void widthOfOneStillRenders() {
            var list = createList(items("hello"));
            List<String> lines = list.render(1);
            assertFalse(lines.isEmpty());
        }

        @Test
        void largeItemCountWithSmallMaxHeight() {
            List<String> bigList = new ArrayList<>();
            for (int i = 0; i < 1000; i++) {
                bigList.add("item-" + i);
            }
            var list = new SelectList<>(bigList, s -> s, 5, plainTheme);
            list.setSelectedIndex(500);
            List<String> lines = list.render(40);
            // 5 items + scroll indicator
            assertEquals(6, lines.size());
            assertTrue(lines.get(5).contains("(501/1000)"));
        }

        @Test
        void newlineInKeyEnterAlsoConfirms() {
            var list = createList(items("a"));
            AtomicReference<String> selected = new AtomicReference<>();
            list.setOnSelect(selected::set);
            list.handleInput("\n");
            assertEquals("a", selected.get());
        }
    }
}
