package com.huawei.hicampus.mate.matecampusclaw.tui.component;

import java.util.ArrayList;
import java.util.List;

/**
 * Ring buffer for Emacs-style kill/yank operations.
 * <p>
 * Tracks killed (deleted) text entries. Consecutive kills can accumulate
 * into a single entry. Supports yank (paste most recent) and yank-pop
 * (cycle through older entries).
 */
public class KillRing {

    private final List<String> ring = new ArrayList<>();

    /**
     * Add text to the kill ring.
     *
     * @param text       the killed text to add
     * @param prepend    if accumulating, prepend (backward deletion) or append (forward deletion)
     * @param accumulate merge with the most recent entry instead of creating a new one
     */
    public void push(String text, boolean prepend, boolean accumulate) {
        if (text == null || text.isEmpty()) return;

        if (accumulate && !ring.isEmpty()) {
            String last = ring.remove(ring.size() - 1);
            ring.add(prepend ? text + last : last + text);
        } else {
            ring.add(text);
        }
    }

    /** Get the most recent entry without modifying the ring. Returns null if empty. */
    public String peek() {
        return ring.isEmpty() ? null : ring.get(ring.size() - 1);
    }

    /** Move the last entry to the front (for yank-pop cycling). */
    public void rotate() {
        if (ring.size() > 1) {
            String last = ring.remove(ring.size() - 1);
            ring.add(0, last);
        }
    }

    /** Returns the number of entries in the ring. */
    public int size() {
        return ring.size();
    }

    /** Returns true if the ring is empty. */
    public boolean isEmpty() {
        return ring.isEmpty();
    }

    /** Remove all entries. */
    public void clear() {
        ring.clear();
    }
}
