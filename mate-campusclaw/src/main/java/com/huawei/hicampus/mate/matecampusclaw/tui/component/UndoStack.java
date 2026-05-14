/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.hicampus.mate.matecampusclaw.tui.component;

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
 *
 * @version [br_eCampusCore 25.1.0_Next, 2026/05/06]
 * @since [br_eCampusCore 25.1.0_Next]
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

    /**
     * Push a deep clone of the given state onto the stack.
     *
     * @param state the state snapshot to clone and store
     */
    public void push(S state) {
        stack.add(cloneFn.apply(state));
    }

    /**
     * Pop and return the most recent snapshot, or null if empty.
     *
     * @return the most recent snapshot, or {@code null} when the stack is empty
     */
    public S pop() {
        if (stack.isEmpty()) {
            return null;
        }
        return stack.remove(stack.size() - 1);
    }

    /**
     * Remove all snapshots.
     */
    public void clear() {
        stack.clear();
    }

    /**
     * Returns the number of snapshots in the stack.
     *
     * @return current snapshot count
     */
    public int size() {
        return stack.size();
    }

    /**
     * Returns true if the stack has no snapshots.
     *
     * @return {@code true} when there are no snapshots
     */
    public boolean isEmpty() {
        return stack.isEmpty();
    }
}
