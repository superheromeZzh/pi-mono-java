package com.huawei.hicampus.mate.matecampusclaw.tui.component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.UnaryOperator;

import com.huawei.hicampus.mate.matecampusclaw.tui.Component;

/**
 * KeybindingsComponent — displays configurable keybinding help info.
 * <p>
 * Renders key combinations and their descriptions in a compact horizontal layout.
 * Multiple keybindings are shown on a single line separated by a configurable separator.
 * When keybindings don't fit on a single line, they wrap to additional lines.
 * <p>
 * Example output: {@code  Ctrl+C Exit  |  Tab Complete  |  Enter Submit}
 */
public class KeybindingsComponent implements Component {

    /**
     * A single keybinding entry: a key combination and its description.
     */
    public record Keybinding(String key, String description) {
    }

    private List<Keybinding> keybindings;
    private String separator;
    private UnaryOperator<String> keyStyleFn;
    private UnaryOperator<String> descStyleFn;
    private UnaryOperator<String> separatorStyleFn;

    // Cache
    private List<Keybinding> cachedKeybindings;
    private int cachedWidth = -1;
    private List<String> cachedLines;

    /**
     * Creates a KeybindingsComponent with default styling.
     */
    public KeybindingsComponent() {
        this(Collections.emptyList());
    }

    /**
     * Creates a KeybindingsComponent with the given keybindings and default styling.
     */
    public KeybindingsComponent(List<Keybinding> keybindings) {
        this.keybindings = keybindings != null ? new ArrayList<>(keybindings) : new ArrayList<>();
        this.separator = "  |  ";
        // Default styling: bold keys, dim descriptions, dim separator
        this.keyStyleFn = text -> "\033[1m" + text + "\033[0m";       // bold
        this.descStyleFn = text -> "\033[2m" + text + "\033[0m";      // dim
        this.separatorStyleFn = text -> "\033[2m" + text + "\033[0m"; // dim
    }

    // -------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------

    public List<Keybinding> getKeybindings() {
        return Collections.unmodifiableList(keybindings);
    }

    public void setKeybindings(List<Keybinding> keybindings) {
        this.keybindings = keybindings != null ? new ArrayList<>(keybindings) : new ArrayList<>();
        invalidate();
    }

    public void setSeparator(String separator) {
        this.separator = separator != null ? separator : "  |  ";
        invalidate();
    }

    public void setKeyStyleFn(UnaryOperator<String> keyStyleFn) {
        this.keyStyleFn = keyStyleFn;
        invalidate();
    }

    public void setDescStyleFn(UnaryOperator<String> descStyleFn) {
        this.descStyleFn = descStyleFn;
        invalidate();
    }

    public void setSeparatorStyleFn(UnaryOperator<String> separatorStyleFn) {
        this.separatorStyleFn = separatorStyleFn;
        invalidate();
    }

    // -------------------------------------------------------------------
    // Component interface
    // -------------------------------------------------------------------

    @Override
    public void invalidate() {
        cachedKeybindings = null;
        cachedWidth = -1;
        cachedLines = null;
    }

    @Override
    public List<String> render(int width) {
        // Cache hit
        if (cachedLines != null
                && cachedWidth == width
                && keybindings.equals(cachedKeybindings)) {
            return cachedLines;
        }

        List<String> result = renderKeybindings(width);

        cachedKeybindings = new ArrayList<>(keybindings);
        cachedWidth = width;
        cachedLines = result;
        return result;
    }

    // -------------------------------------------------------------------
    // Internal rendering
    // -------------------------------------------------------------------

    private List<String> renderKeybindings(int width) {
        if (keybindings.isEmpty()) {
            return Collections.emptyList();
        }

        // Build styled segments for each keybinding
        List<String> segments = new ArrayList<>();
        List<Integer> segmentWidths = new ArrayList<>();

        for (Keybinding kb : keybindings) {
            String styledKey = keyStyleFn != null ? keyStyleFn.apply(kb.key()) : kb.key();
            String styledDesc = descStyleFn != null ? descStyleFn.apply(kb.description()) : kb.description();
            String segment = styledKey + " " + styledDesc;
            segments.add(segment);
            // Visible width: key + space + description
            segmentWidths.add(kb.key().length() + 1 + kb.description().length());
        }

        String styledSep = separatorStyleFn != null ? separatorStyleFn.apply(separator) : separator;
        int sepWidth = separator.length();

        // Pack segments into lines, wrapping when width is exceeded
        List<String> lines = new ArrayList<>();
        StringBuilder currentLine = new StringBuilder();
        int currentLineWidth = 0;
        boolean firstOnLine = true;

        for (int i = 0; i < segments.size(); i++) {
            int segWidth = segmentWidths.get(i);
            int needed = firstOnLine ? segWidth : sepWidth + segWidth;

            if (!firstOnLine && currentLineWidth + needed > width) {
                // Wrap to next line
                lines.add(currentLine.toString());
                currentLine = new StringBuilder();
                currentLineWidth = 0;
                firstOnLine = true;
            }

            if (!firstOnLine) {
                currentLine.append(styledSep);
                currentLineWidth += sepWidth;
            }

            currentLine.append(segments.get(i));
            currentLineWidth += segmentWidths.get(i);
            firstOnLine = false;
        }

        if (currentLine.length() > 0) {
            lines.add(currentLine.toString());
        }

        return lines;
    }
}
