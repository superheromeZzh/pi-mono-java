package com.huawei.hicampus.mate.matecampusclaw.tui.component;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class ContainerTest {

    @Nested
    class EmptyContainer {

        @Test
        void emptyContainerRendersNothing() {
            var container = new Container();
            assertTrue(container.render(80).isEmpty());
        }

        @Test
        void getChildrenReturnsEmptyList() {
            var container = new Container();
            assertTrue(container.getChildren().isEmpty());
        }
    }

    @Nested
    class AddRemoveChildren {

        @Test
        void addChild() {
            var container = new Container();
            container.addChild(new Text("hello"));
            assertEquals(1, container.getChildren().size());
        }

        @Test
        void addMultipleChildren() {
            var container = new Container();
            container.addChild(new Text("first"));
            container.addChild(new Text("second"));
            assertEquals(2, container.getChildren().size());
        }

        @Test
        void removeChild() {
            var container = new Container();
            var child = new Text("hello");
            container.addChild(child);
            container.removeChild(child);
            assertTrue(container.getChildren().isEmpty());
        }

        @Test
        void removeNonExistentChildDoesNothing() {
            var container = new Container();
            container.addChild(new Text("hello"));
            container.removeChild(new Text("other"));
            assertEquals(1, container.getChildren().size());
        }

        @Test
        void clear() {
            var container = new Container();
            container.addChild(new Text("a"));
            container.addChild(new Text("b"));
            container.clear();
            assertTrue(container.getChildren().isEmpty());
        }

        @Test
        void getChildrenIsUnmodifiable() {
            var container = new Container();
            container.addChild(new Text("x"));
            assertThrows(UnsupportedOperationException.class,
                    () -> container.getChildren().add(new Text("y")));
        }
    }

    @Nested
    class Rendering {

        @Test
        void verticalLayout() {
            var container = new Container();
            container.addChild(new Text("first"));
            container.addChild(new Text("second"));
            List<String> lines = container.render(20);
            assertEquals(2, lines.size());
            assertTrue(lines.get(0).contains("first"));
            assertTrue(lines.get(1).contains("second"));
        }

        @Test
        void childrenGetFullWidth() {
            var container = new Container();
            var text = new Text("hi");
            container.addChild(text);
            List<String> lines = container.render(40);
            // Text component pads to full width
            assertEquals(40, lines.get(0).length());
        }

        @Test
        void multiLineChild() {
            var container = new Container();
            container.addChild(new Text("line1\nline2"));
            container.addChild(new Text("line3"));
            List<String> lines = container.render(20);
            assertEquals(3, lines.size());
        }

        @Test
        void emptyChildRendersNoLines() {
            var container = new Container();
            container.addChild(new Text(""));
            container.addChild(new Text("visible"));
            List<String> lines = container.render(20);
            // Empty text returns no lines, so only "visible" appears
            assertEquals(1, lines.size());
            assertTrue(lines.get(0).contains("visible"));
        }
    }

    @Nested
    class Invalidation {

        @Test
        void invalidatePropagatesToChildren() {
            var container = new Container();
            var text = new Text("cached");
            container.addChild(text);

            // Render to populate cache
            List<String> first = text.render(20);
            container.invalidate();
            // After invalidation, text should re-render fresh
            List<String> second = text.render(20);
            assertNotSame(first, second);
        }

        @Test
        void invalidateWithNestedContainers() {
            var outer = new Container();
            var inner = new Container();
            var text = new Text("deep");
            inner.addChild(text);
            outer.addChild(inner);

            List<String> first = text.render(20);
            outer.invalidate();
            List<String> second = text.render(20);
            assertNotSame(first, second);
        }
    }
}
