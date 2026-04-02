package com.huawei.hicampus.mate.matecampusclaw.tui.component;

import java.util.ArrayList;
import java.util.List;

import com.huawei.hicampus.mate.matecampusclaw.tui.Component;

/**
 * Spacer component — renders a fixed number of empty lines.
 * Useful for adding vertical spacing between other components.
 */
public class Spacer implements Component {

    private int lines;

    // Cache
    private int cachedLines = -1;
    private int cachedWidth = -1;
    private List<String> cachedResult;

    /**
     * Creates a spacer that renders one empty line.
     */
    public Spacer() {
        this(1);
    }

    /**
     * Creates a spacer that renders the specified number of empty lines.
     *
     * @param lines the number of empty lines to render (clamped to 0 minimum)
     */
    public Spacer(int lines) {
        this.lines = Math.max(0, lines);
    }

    public int getLines() {
        return lines;
    }

    public void setLines(int lines) {
        this.lines = Math.max(0, lines);
        invalidate();
    }

    @Override
    public void invalidate() {
        cachedLines = -1;
        cachedWidth = -1;
        cachedResult = null;
    }

    @Override
    public List<String> render(int width) {
        // Cache hit
        if (cachedResult != null && cachedLines == lines && cachedWidth == width) {
            return cachedResult;
        }

        List<String> result = new ArrayList<>(lines);
        String emptyLine = " ".repeat(width);
        for (int i = 0; i < lines; i++) {
            result.add(emptyLine);
        }

        cachedLines = lines;
        cachedWidth = width;
        cachedResult = result;
        return result;
    }
}
