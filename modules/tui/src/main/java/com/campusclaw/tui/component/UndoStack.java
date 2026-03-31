package com.campusclaw.tui.component;

import java.util.ArrayList;
import java.util.List;
import java.util.function.UnaryOperator;

/**
 * Generic undo stack with clone-on-push semantics.
 * <p>
 * Stores deep clones of state snapshots via a user-provided clone function.
 * Popped snapshots are returned directly since they are already detached.
 *
 * @param <S> the state type
 */
public class UndoStack<S> {

    private final List<S> stack = new ArrayList<>();
    private final UnaryOperator<S> cloneFn;

    /**
     * Creates an UndoStack with a custom clone function.
     *
     * @param cloneFn function to deep-clone a state before pushing
     */
    public UndoStack(UnaryOperator<S> cloneFn) {
        this.cloneFn = cloneFn;
    }

    /** Push a deep clone of the given state onto the stack. */
    public void push(S state) {
        stack.add(cloneFn.apply(state));
    }

    /** Pop and return the most recent snapshot, or null if empty. */
    public S pop() {
        if (stack.isEmpty()) return null;
        return stack.remove(stack.size() - 1);
    }

    /** Remove all snapshots. */
    public void clear() {
        stack.clear();
    }

    /** Returns the number of snapshots in the stack. */
    public int size() {
        return stack.size();
    }

    /** Returns true if the stack has no snapshots. */
    public boolean isEmpty() {
        return stack.isEmpty();
    }
}
