package com.campusclaw.tui;

import java.util.List;

/**
 * Represents the difference between two rendered frames.
 * Used by {@link DiffRenderer} to compute the minimum set of terminal updates.
 */
public sealed interface RenderDiff permits RenderDiff.LineUpdates, RenderDiff.FullRerender {

    /**
     * Incremental update: only specific lines have changed.
     *
     * @param updates the list of individual line updates
     */
    record LineUpdates(List<LineUpdate> updates) implements RenderDiff {
        public LineUpdates {
            updates = List.copyOf(updates);
        }
    }

    /**
     * Full rerender required — too many changes or structural differences
     * (e.g. line count changed, first render, width changed).
     */
    record FullRerender() implements RenderDiff {
    }

    /**
     * A single line update within a {@link LineUpdates} diff.
     *
     * @param row     the 0-based row index
     * @param content the new content for that row
     */
    record LineUpdate(int row, String content) {
    }
}
