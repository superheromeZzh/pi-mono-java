package com.huawei.hicampus.mate.matecampusclaw.tui.component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.UnaryOperator;

import com.huawei.hicampus.mate.matecampusclaw.tui.Component;
import com.huawei.hicampus.mate.matecampusclaw.tui.Focusable;
import com.huawei.hicampus.mate.matecampusclaw.tui.ansi.AnsiUtils;

/**
 * SettingsList component — displays a list of key-value setting entries with keyboard
 * navigation and selection support.
 * <p>
 * Each entry is rendered as {@code key: value} on a single line. The selected entry
 * is highlighted. Supports up/down navigation (with wrap-around), Enter to confirm,
 * and Escape/Ctrl+C to cancel.
 *
 * @param <T> the type of value in each setting entry
 */
public class SettingsList<T> implements Component, Focusable {

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

    /**
     * Represents a single setting entry with a key (label) and a value.
     */
    public record Entry<T>(String key, T value, String displayValue) {

        /**
         * Creates an entry using {@code value.toString()} as the display value.
         */
        public Entry(String key, T value) {
            this(key, value, value != null ? value.toString() : "");
        }
    }

    private List<Entry<T>> entries;
    private int selectedIndex;
    private boolean focused;

    // Styling
    private UnaryOperator<String> keyStyleFn;
    private UnaryOperator<String> valueStyleFn;
    private UnaryOperator<String> selectedStyleFn;
    private UnaryOperator<String> separatorStyleFn;

    // Callbacks
    private Consumer<Entry<T>> onSelect;
    private Runnable onCancel;
    private Consumer<Entry<T>> onSelectionChange;

    // Cache
    private List<Entry<T>> cachedEntries;
    private int cachedSelectedIndex = -1;
    private int cachedWidth = -1;
    private List<String> cachedLines;

    /**
     * Creates a SettingsList with the given entries and default styling.
     */
    public SettingsList(List<Entry<T>> entries) {
        this.entries = entries != null ? new ArrayList<>(entries) : new ArrayList<>();
        this.selectedIndex = 0;
        // Default styling: dim keys, bold selected line
        this.keyStyleFn = text -> "\033[2m" + text + "\033[0m";       // dim
        this.valueStyleFn = UnaryOperator.identity();
        this.selectedStyleFn = text -> "\033[1m" + text + "\033[0m";  // bold
        this.separatorStyleFn = text -> "\033[2m" + text + "\033[0m"; // dim
    }

    // -------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------

    public List<Entry<T>> getEntries() {
        return Collections.unmodifiableList(entries);
    }

    public void setEntries(List<Entry<T>> entries) {
        this.entries = entries != null ? new ArrayList<>(entries) : new ArrayList<>();
        this.selectedIndex = 0;
        invalidate();
    }

    public int getSelectedIndex() {
        return selectedIndex;
    }

    public void setSelectedIndex(int index) {
        if (entries.isEmpty()) {
            selectedIndex = 0;
            return;
        }
        selectedIndex = Math.max(0, Math.min(index, entries.size() - 1));
        invalidate();
    }

    public Entry<T> getSelectedEntry() {
        if (entries.isEmpty()) return null;
        return entries.get(selectedIndex);
    }

    public void setKeyStyleFn(UnaryOperator<String> keyStyleFn) {
        this.keyStyleFn = keyStyleFn;
        invalidate();
    }

    public void setValueStyleFn(UnaryOperator<String> valueStyleFn) {
        this.valueStyleFn = valueStyleFn;
        invalidate();
    }

    public void setSelectedStyleFn(UnaryOperator<String> selectedStyleFn) {
        this.selectedStyleFn = selectedStyleFn;
        invalidate();
    }

    public void setSeparatorStyleFn(UnaryOperator<String> separatorStyleFn) {
        this.separatorStyleFn = separatorStyleFn;
        invalidate();
    }

    public void setOnSelect(Consumer<Entry<T>> onSelect) {
        this.onSelect = onSelect;
    }

    public void setOnCancel(Runnable onCancel) {
        this.onCancel = onCancel;
    }

    public void setOnSelectionChange(Consumer<Entry<T>> onSelectionChange) {
        this.onSelectionChange = onSelectionChange;
    }

    // -------------------------------------------------------------------
    // Component interface
    // -------------------------------------------------------------------

    @Override
    public void invalidate() {
        cachedEntries = null;
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
                && entries.equals(cachedEntries)) {
            return cachedLines;
        }

        List<String> result = renderEntries(width);

        cachedEntries = new ArrayList<>(entries);
        cachedSelectedIndex = selectedIndex;
        cachedWidth = width;
        cachedLines = result;
        return result;
    }

    @Override
    public void handleInput(String data) {
        if (entries.isEmpty()) return;

        if (KEY_UP.equals(data)) {
            selectedIndex = selectedIndex == 0 ? entries.size() - 1 : selectedIndex - 1;
            invalidate();
            notifySelectionChange();
            return;
        }

        if (KEY_DOWN.equals(data)) {
            selectedIndex = selectedIndex == entries.size() - 1 ? 0 : selectedIndex + 1;
            invalidate();
            notifySelectionChange();
            return;
        }

        if (KEY_ENTER.equals(data) || KEY_NEWLINE.equals(data)) {
            if (onSelect != null) {
                onSelect.accept(entries.get(selectedIndex));
            }
            return;
        }

        if (KEY_ESCAPE.equals(data) || KEY_CTRL_C.equals(data)) {
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

    private List<String> renderEntries(int width) {
        if (entries.isEmpty()) {
            return List.of("\033[2m  No settings\033[0m");
        }

        List<String> lines = new ArrayList<>();
        String separator = ": ";

        for (int i = 0; i < entries.size(); i++) {
            Entry<T> entry = entries.get(i);
            boolean isSelected = (i == selectedIndex);

            String prefix = isSelected ? SELECTED_PREFIX : NORMAL_PREFIX;
            int availableWidth = Math.max(1, width - PREFIX_WIDTH);

            String keyText = entry.key();
            String valText = entry.displayValue();
            String combined = keyText + separator + valText;

            // Truncate if necessary
            String displayLine;
            int combinedWidth = AnsiUtils.visibleWidth(combined);
            if (combinedWidth <= availableWidth) {
                displayLine = combined;
            } else if (availableWidth <= 1) {
                displayLine = AnsiUtils.sliceByColumn(combined, 0, availableWidth);
            } else {
                displayLine = AnsiUtils.sliceByColumn(combined, 0, availableWidth - 1) + "\u2026";
            }

            // Apply styling
            if (isSelected && selectedStyleFn != null) {
                String styledPrefix = "\033[34m" + prefix + "\033[0m"; // blue prefix
                lines.add(selectedStyleFn.apply(styledPrefix + displayLine));
            } else {
                // Apply key/value styling separately for unselected items
                if (keyStyleFn != null || valueStyleFn != null || separatorStyleFn != null) {
                    String styledKey = keyStyleFn != null ? keyStyleFn.apply(keyText) : keyText;
                    String styledSep = separatorStyleFn != null ? separatorStyleFn.apply(separator) : separator;
                    String styledVal = valueStyleFn != null ? valueStyleFn.apply(valText) : valText;
                    String styledLine = styledKey + styledSep + styledVal;

                    // Truncate styled line by visible width
                    int visWidth = AnsiUtils.visibleWidth(prefix + styledLine);
                    if (visWidth > width) {
                        // Fall back to plain truncation
                        lines.add(prefix + displayLine);
                    } else {
                        lines.add(prefix + styledLine);
                    }
                } else {
                    lines.add(prefix + displayLine);
                }
            }
        }

        return lines;
    }

    private void notifySelectionChange() {
        if (onSelectionChange != null && !entries.isEmpty()) {
            onSelectionChange.accept(entries.get(selectedIndex));
        }
    }
}
