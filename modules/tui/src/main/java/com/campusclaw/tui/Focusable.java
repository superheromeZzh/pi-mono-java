package com.campusclaw.tui;

/**
 * Marks a {@link Component} as focusable, meaning it can receive keyboard input.
 * Components that implement both {@link Component} and {@link Focusable} participate
 * in the TUI focus management system.
 */
public interface Focusable {

    boolean isFocused();

    void setFocused(boolean focused);

    /**
     * Checks whether a component implements both {@link Component} and {@link Focusable}.
     *
     * @param component the component to check (may be null)
     * @return true if the component is non-null and implements Focusable
     */
    static boolean isFocusable(Component component) {
        return component instanceof Focusable;
    }
}
