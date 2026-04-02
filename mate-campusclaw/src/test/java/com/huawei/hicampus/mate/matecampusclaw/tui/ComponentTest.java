package com.huawei.hicampus.mate.matecampusclaw.tui;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class ComponentTest {

    // -------------------------------------------------------------------
    // Component interface
    // -------------------------------------------------------------------

    @Nested
    class ComponentInterface {

        @Test
        void renderReturnsLines() {
            Component component = new SimpleComponent("hello", "world");
            List<String> lines = component.render(80);
            assertEquals(List.of("hello", "world"), lines);
        }

        @Test
        void handleInputDefaultDoesNothing() {
            Component component = new SimpleComponent("test");
            // Should not throw
            component.handleInput("a");
        }

        @Test
        void wantsKeyReleaseDefaultIsFalse() {
            Component component = new SimpleComponent("test");
            assertFalse(component.wantsKeyRelease());
        }

        @Test
        void invalidateClearsCache() {
            SimpleComponent component = new SimpleComponent("cached");
            component.render(80); // Populate cache
            assertTrue(component.hasRendered());
            component.invalidate();
            assertFalse(component.hasRendered());
        }
    }

    // -------------------------------------------------------------------
    // Focusable interface
    // -------------------------------------------------------------------

    @Nested
    class FocusableInterface {

        @Test
        void isFocusableReturnsTrueForFocusableComponent() {
            FocusableComponent fc = new FocusableComponent();
            assertTrue(Focusable.isFocusable(fc));
        }

        @Test
        void isFocusableReturnsFalseForNonFocusableComponent() {
            SimpleComponent sc = new SimpleComponent("test");
            assertFalse(Focusable.isFocusable(sc));
        }

        @Test
        void isFocusableReturnsFalseForNull() {
            assertFalse(Focusable.isFocusable(null));
        }

        @Test
        void focusedStateToggle() {
            FocusableComponent fc = new FocusableComponent();
            assertFalse(fc.isFocused());
            fc.setFocused(true);
            assertTrue(fc.isFocused());
            fc.setFocused(false);
            assertFalse(fc.isFocused());
        }

        @Test
        void castToFocusable() {
            Component component = new FocusableComponent();
            assertTrue(Focusable.isFocusable(component));
            Focusable focusable = (Focusable) component;
            focusable.setFocused(true);
            assertTrue(focusable.isFocused());
        }
    }

    // -------------------------------------------------------------------
    // Test helpers
    // -------------------------------------------------------------------

    /** Minimal Component implementation for testing. */
    static class SimpleComponent implements Component {
        private final List<String> lines;
        private boolean rendered = false;

        SimpleComponent(String... lines) {
            this.lines = List.of(lines);
        }

        @Override
        public List<String> render(int width) {
            rendered = true;
            return lines;
        }

        @Override
        public void invalidate() {
            rendered = false;
        }

        boolean hasRendered() {
            return rendered;
        }
    }

    /** Component that also implements Focusable. */
    static class FocusableComponent implements Component, Focusable {
        private boolean focused = false;

        @Override
        public List<String> render(int width) {
            return List.of(focused ? "[focused]" : "[unfocused]");
        }

        @Override
        public void invalidate() {
        }

        @Override
        public boolean isFocused() {
            return focused;
        }

        @Override
        public void setFocused(boolean focused) {
            this.focused = focused;
        }
    }
}
