package com.campusclaw.tui;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Computes the minimal difference between consecutive rendered frames.
 * <p>
 * Compares the previous frame's lines with the current frame's lines and produces
 * either a {@link RenderDiff.LineUpdates} (when only some lines changed) or a
 * {@link RenderDiff.FullRerender} (when structural changes require a complete redraw).
 * <p>
 * Usage:
 * <pre>{@code
 * DiffRenderer renderer = new DiffRenderer();
 * List<String> lines = component.render(width);
 * RenderDiff diff = renderer.computeDiff(lines);
 * // Apply diff to terminal...
 * }</pre>
 */
public class DiffRenderer {

    private List<String> lastRendered = Collections.emptyList();
    private boolean firstRender = true;

    /**
     * Computes the diff between the previously rendered lines and the new lines.
     * After this call, the new lines become the "previous" frame for the next diff.
     *
     * @param newLines the newly rendered lines
     * @return the minimal diff to apply
     */
    public RenderDiff computeDiff(List<String> newLines) {
        Objects.requireNonNull(newLines, "newLines must not be null");

        if (firstRender) {
            firstRender = false;
            lastRendered = List.copyOf(newLines);
            return new RenderDiff.FullRerender();
        }

        // If line count changed, a full rerender is needed because the terminal
        // layout has structurally changed (lines added/removed require cursor
        // repositioning and clearing).
        if (newLines.size() != lastRendered.size()) {
            lastRendered = List.copyOf(newLines);
            return new RenderDiff.FullRerender();
        }

        // Same line count — find individual changed lines
        List<RenderDiff.LineUpdate> updates = new ArrayList<>();
        for (int i = 0; i < newLines.size(); i++) {
            String oldLine = lastRendered.get(i);
            String newLine = newLines.get(i);
            if (!oldLine.equals(newLine)) {
                updates.add(new RenderDiff.LineUpdate(i, newLine));
            }
        }

        lastRendered = List.copyOf(newLines);

        if (updates.isEmpty()) {
            return new RenderDiff.LineUpdates(List.of());
        }
        return new RenderDiff.LineUpdates(updates);
    }

    /**
     * Returns the last rendered frame.
     */
    public List<String> getLastRendered() {
        return lastRendered;
    }

    /**
     * Resets the renderer state, forcing a full rerender on the next {@link #computeDiff} call.
     */
    public void reset() {
        lastRendered = Collections.emptyList();
        firstRender = true;
    }
}
