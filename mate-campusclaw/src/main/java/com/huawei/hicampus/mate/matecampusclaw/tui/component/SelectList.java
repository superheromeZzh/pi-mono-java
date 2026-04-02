package com.huawei.hicampus.mate.matecampusclaw.tui.component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;

import com.huawei.hicampus.mate.matecampusclaw.tui.Component;
import com.huawei.hicampus.mate.matecampusclaw.tui.Focusable;
import com.huawei.hicampus.mate.matecampusclaw.tui.ansi.AnsiUtils;

/**
 * A selectable list component with keyboard navigation and scrolling.
 * <p>
 * Renders items as vertical lines. The selected item is highlighted with an arrow prefix ("→ ")
 * and styled via the theme. When the list exceeds {@code maxHeight}, a scrolling window keeps
 * the selected item visible, and a scroll indicator is shown.
 * <p>
 * Keyboard handling:
 * <ul>
 *   <li>↑ / ↓ — navigate (wraps around)</li>
 *   <li>Enter — confirm selection</li>
 *   <li>Escape / Ctrl+C — cancel</li>
 * </ul>
 *
 * @param <T> the item type
 */
public class SelectList<T> implements Component, Focusable {

    // ANSI key sequences
    private static final String KEY_UP = "\033[A";
    private static final String KEY_DOWN = "\033[B";
    private static final String KEY_ENTER = "\r";
    private static final String KEY_NEWLINE = "\n";
    private static final String KEY_ESCAPE = "\033";
    private static final String KEY_CTRL_C = "\003";

    private static final String SELECTED_PREFIX = "→ ";
    private static final String NORMAL_PREFIX = "  ";
    private static final int PREFIX_WIDTH = 2;

    private List<T> items;
    private final Function<T, String> renderItem;
    private int maxHeight;
    private SelectListTheme theme;
    private boolean focused;
    private int selectedIndex;

    // Callbacks
    private Consumer<T> onSelect;
    private Runnable onCancel;
    private Consumer<T> onSelectionChange;

    // Cache
    private List<T> cachedItems;
    private int cachedSelectedIndex = -1;
    private int cachedWidth = -1;
    private List<String> cachedLines;

    /**
     * Creates a SelectList with default theme and no maxHeight constraint.
     *
     * @param items      the items to display
     * @param renderItem function that produces a display string for each item
     */
    public SelectList(List<T> items, Function<T, String> renderItem) {
        this(items, renderItem, Integer.MAX_VALUE, SelectListTheme.defaultTheme());
    }

    /**
     * Creates a SelectList with a maxHeight constraint and default theme.
     *
     * @param items      the items to display
     * @param renderItem function that produces a display string for each item
     * @param maxHeight  maximum number of visible items (scrolls if exceeded)
     */
    public SelectList(List<T> items, Function<T, String> renderItem, int maxHeight) {
        this(items, renderItem, maxHeight, SelectListTheme.defaultTheme());
    }

    /**
     * Creates a SelectList with full configuration.
     *
     * @param items      the items to display
     * @param renderItem function that produces a display string for each item
     * @param maxHeight  maximum number of visible items (scrolls if exceeded)
     * @param theme      the styling theme
     */
    public SelectList(List<T> items, Function<T, String> renderItem, int maxHeight, SelectListTheme theme) {
        this.items = items != null ? new ArrayList<>(items) : new ArrayList<>();
        this.renderItem = renderItem;
        this.maxHeight = maxHeight > 0 ? maxHeight : Integer.MAX_VALUE;
        this.theme = theme != null ? theme : SelectListTheme.defaultTheme();
        this.selectedIndex = 0;
    }

    // -------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------

    /** Returns the currently selected item, or null if the list is empty. */
    public T getSelectedItem() {
        if (items.isEmpty()) return null;
        return items.get(selectedIndex);
    }

    /** Returns the current selected index. */
    public int getSelectedIndex() {
        return selectedIndex;
    }

    /** Sets the selected index, clamped to valid range. */
    public void setSelectedIndex(int index) {
        if (items.isEmpty()) {
            selectedIndex = 0;
            return;
        }
        selectedIndex = Math.max(0, Math.min(index, items.size() - 1));
        invalidate();
    }

    /** Replaces the item list and resets the selection to 0. */
    public void setItems(List<T> items) {
        this.items = items != null ? new ArrayList<>(items) : new ArrayList<>();
        this.selectedIndex = 0;
        invalidate();
    }

    /** Returns the current item list (unmodifiable view). */
    public List<T> getItems() {
        return Collections.unmodifiableList(items);
    }

    /** Sets the maximum number of visible items. */
    public void setMaxHeight(int maxHeight) {
        this.maxHeight = maxHeight > 0 ? maxHeight : Integer.MAX_VALUE;
        invalidate();
    }

    /** Returns the current maxHeight. */
    public int getMaxHeight() {
        return maxHeight;
    }

    /** Sets the theme. */
    public void setTheme(SelectListTheme theme) {
        this.theme = theme != null ? theme : SelectListTheme.defaultTheme();
        invalidate();
    }

    /** Sets the callback invoked when Enter is pressed. */
    public void setOnSelect(Consumer<T> onSelect) {
        this.onSelect = onSelect;
    }

    /** Sets the callback invoked when Escape or Ctrl+C is pressed. */
    public void setOnCancel(Runnable onCancel) {
        this.onCancel = onCancel;
    }

    /** Sets the callback invoked when the selection changes via navigation. */
    public void setOnSelectionChange(Consumer<T> onSelectionChange) {
        this.onSelectionChange = onSelectionChange;
    }

    // -------------------------------------------------------------------
    // Component interface
    // -------------------------------------------------------------------

    @Override
    public void invalidate() {
        cachedItems = null;
        cachedSelectedIndex = -1;
        cachedWidth = -1;
        cachedLines = null;
    }

    @Override
    public List<String> render(int width) {
        // Cache hit
        if (cachedLines != null
                && cachedWidth == width
                && cachedSelectedIndex == selectedIndex
                && items.equals(cachedItems)) {
            return cachedLines;
        }

        List<String> result = renderList(width);

        cachedItems = new ArrayList<>(items);
        cachedSelectedIndex = selectedIndex;
        cachedWidth = width;
        cachedLines = result;
        return result;
    }

    @Override
    public void handleInput(String data) {
        if (items.isEmpty()) return;

        // Up arrow — wrap to bottom when at top
        if (KEY_UP.equals(data)) {
            selectedIndex = selectedIndex == 0 ? items.size() - 1 : selectedIndex - 1;
            invalidate();
            notifySelectionChange();
            return;
        }

        // Down arrow — wrap to top when at bottom
        if (KEY_DOWN.equals(data)) {
            selectedIndex = selectedIndex == items.size() - 1 ? 0 : selectedIndex + 1;
            invalidate();
            notifySelectionChange();
            return;
        }

        // Enter — confirm
        if (KEY_ENTER.equals(data) || KEY_NEWLINE.equals(data)) {
            if (onSelect != null) {
                onSelect.accept(items.get(selectedIndex));
            }
            return;
        }

        // Escape — cancel (note: bare ESC is a single \033 char)
        if (data.equals(KEY_ESCAPE) || data.equals(KEY_CTRL_C)) {
            if (onCancel != null) {
                onCancel.run();
            }
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
    // Internal rendering
    // -------------------------------------------------------------------

    private List<String> renderList(int width) {
        if (items.isEmpty()) {
            return List.of(theme.noMatch("  No items"));
        }

        List<String> lines = new ArrayList<>();

        // Calculate visible window
        int visibleCount = Math.min(maxHeight, items.size());
        int startIndex = calculateStartIndex(visibleCount);
        int endIndex = Math.min(startIndex + visibleCount, items.size());

        // Render visible items
        for (int i = startIndex; i < endIndex; i++) {
            T item = items.get(i);
            boolean isSelected = (i == selectedIndex);
            String label = renderItem.apply(item);
            lines.add(renderItemLine(label, isSelected, width));
        }

        // Scroll indicator (when not all items visible)
        if (items.size() > visibleCount) {
            String scrollText = "  (" + (selectedIndex + 1) + "/" + items.size() + ")";
            lines.add(theme.scrollInfo(truncateToWidth(scrollText, width)));
        }

        return lines;
    }

    /**
     * Calculates the start index of the visible window, centering the selected item.
     */
    private int calculateStartIndex(int visibleCount) {
        if (visibleCount >= items.size()) return 0;

        // Try to center the selected item in the visible window
        int start = selectedIndex - visibleCount / 2;
        // Clamp to valid range
        start = Math.max(0, Math.min(start, items.size() - visibleCount));
        return start;
    }

    /**
     * Renders a single item line with prefix, label, and selection styling.
     */
    private String renderItemLine(String label, boolean isSelected, int width) {
        String prefix;
        if (isSelected) {
            prefix = theme.selectedPrefix(SELECTED_PREFIX);
        } else {
            prefix = NORMAL_PREFIX;
        }

        // Truncate label to fit within available width
        int availableWidth = Math.max(1, width - PREFIX_WIDTH);
        String truncatedLabel = truncateToWidth(label, availableWidth);

        if (isSelected) {
            return theme.selectedText(prefix + truncatedLabel);
        }
        return prefix + truncatedLabel;
    }

    /**
     * Truncates a string to fit within maxWidth visible columns.
     */
    private static String truncateToWidth(String text, int maxWidth) {
        if (text == null || text.isEmpty()) return "";
        int visWidth = AnsiUtils.visibleWidth(text);
        if (visWidth <= maxWidth) return text;

        // Truncate by visible characters — strip ANSI, iterate, and rebuild
        // For simplicity, use sliceByColumn which handles ANSI correctly
        return AnsiUtils.sliceByColumn(text, 0, maxWidth);
    }

    private void notifySelectionChange() {
        if (onSelectionChange != null && !items.isEmpty()) {
            onSelectionChange.accept(items.get(selectedIndex));
        }
    }
}
