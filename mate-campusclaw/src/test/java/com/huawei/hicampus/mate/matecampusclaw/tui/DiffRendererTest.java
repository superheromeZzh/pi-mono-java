package com.huawei.hicampus.mate.matecampusclaw.tui;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class DiffRendererTest {

    private DiffRenderer renderer;

    @BeforeEach
    void setUp() {
        renderer = new DiffRenderer();
    }

    // -------------------------------------------------------------------
    // First render
    // -------------------------------------------------------------------

    @Nested
    class FirstRender {

        @Test
        void returnsFullRerender() {
            RenderDiff diff = renderer.computeDiff(List.of("hello"));
            assertInstanceOf(RenderDiff.FullRerender.class, diff);
        }

        @Test
        void storesRenderedLines() {
            renderer.computeDiff(List.of("hello", "world"));
            assertEquals(List.of("hello", "world"), renderer.getLastRendered());
        }

        @Test
        void emptyFirstRender() {
            RenderDiff diff = renderer.computeDiff(List.of());
            assertInstanceOf(RenderDiff.FullRerender.class, diff);
        }
    }

    // -------------------------------------------------------------------
    // No changes
    // -------------------------------------------------------------------

    @Nested
    class NoChanges {

        @Test
        void identicalFramesReturnEmptyUpdates() {
            renderer.computeDiff(List.of("hello", "world"));
            RenderDiff diff = renderer.computeDiff(List.of("hello", "world"));
            assertInstanceOf(RenderDiff.LineUpdates.class, diff);
            var updates = ((RenderDiff.LineUpdates) diff).updates();
            assertTrue(updates.isEmpty());
        }

        @Test
        void emptyToEmpty() {
            renderer.computeDiff(List.of());
            RenderDiff diff = renderer.computeDiff(List.of());
            assertInstanceOf(RenderDiff.LineUpdates.class, diff);
            assertTrue(((RenderDiff.LineUpdates) diff).updates().isEmpty());
        }
    }

    // -------------------------------------------------------------------
    // Line content changes (same line count)
    // -------------------------------------------------------------------

    @Nested
    class LineContentChanges {

        @Test
        void singleLineChange() {
            renderer.computeDiff(List.of("hello", "world"));
            RenderDiff diff = renderer.computeDiff(List.of("hello", "WORLD"));

            assertInstanceOf(RenderDiff.LineUpdates.class, diff);
            var updates = ((RenderDiff.LineUpdates) diff).updates();
            assertEquals(1, updates.size());
            assertEquals(1, updates.get(0).row());
            assertEquals("WORLD", updates.get(0).content());
        }

        @Test
        void multipleLineChanges() {
            renderer.computeDiff(List.of("a", "b", "c"));
            RenderDiff diff = renderer.computeDiff(List.of("A", "b", "C"));

            assertInstanceOf(RenderDiff.LineUpdates.class, diff);
            var updates = ((RenderDiff.LineUpdates) diff).updates();
            assertEquals(2, updates.size());
            assertEquals(0, updates.get(0).row());
            assertEquals("A", updates.get(0).content());
            assertEquals(2, updates.get(1).row());
            assertEquals("C", updates.get(1).content());
        }

        @Test
        void allLinesChange() {
            renderer.computeDiff(List.of("a", "b", "c"));
            RenderDiff diff = renderer.computeDiff(List.of("x", "y", "z"));

            assertInstanceOf(RenderDiff.LineUpdates.class, diff);
            var updates = ((RenderDiff.LineUpdates) diff).updates();
            assertEquals(3, updates.size());
        }

        @Test
        void firstLineChange() {
            renderer.computeDiff(List.of("old", "same"));
            RenderDiff diff = renderer.computeDiff(List.of("new", "same"));

            var updates = ((RenderDiff.LineUpdates) diff).updates();
            assertEquals(1, updates.size());
            assertEquals(0, updates.get(0).row());
        }

        @Test
        void lastLineChange() {
            renderer.computeDiff(List.of("same", "old"));
            RenderDiff diff = renderer.computeDiff(List.of("same", "new"));

            var updates = ((RenderDiff.LineUpdates) diff).updates();
            assertEquals(1, updates.size());
            assertEquals(1, updates.get(0).row());
        }
    }

    // -------------------------------------------------------------------
    // Line count changes (structural)
    // -------------------------------------------------------------------

    @Nested
    class LineCountChanges {

        @Test
        void linesAdded() {
            renderer.computeDiff(List.of("a"));
            RenderDiff diff = renderer.computeDiff(List.of("a", "b"));
            assertInstanceOf(RenderDiff.FullRerender.class, diff);
        }

        @Test
        void linesRemoved() {
            renderer.computeDiff(List.of("a", "b"));
            RenderDiff diff = renderer.computeDiff(List.of("a"));
            assertInstanceOf(RenderDiff.FullRerender.class, diff);
        }

        @Test
        void emptyToNonEmpty() {
            renderer.computeDiff(List.of());
            RenderDiff diff = renderer.computeDiff(List.of("a"));
            assertInstanceOf(RenderDiff.FullRerender.class, diff);
        }

        @Test
        void nonEmptyToEmpty() {
            renderer.computeDiff(List.of("a"));
            RenderDiff diff = renderer.computeDiff(List.of());
            assertInstanceOf(RenderDiff.FullRerender.class, diff);
        }
    }

    // -------------------------------------------------------------------
    // ANSI content
    // -------------------------------------------------------------------

    @Nested
    class AnsiContent {
        private static final String RED = "\033[31m";
        private static final String RESET = "\033[0m";

        @Test
        void ansiChangeDetected() {
            renderer.computeDiff(List.of("hello"));
            RenderDiff diff = renderer.computeDiff(List.of(RED + "hello" + RESET));

            assertInstanceOf(RenderDiff.LineUpdates.class, diff);
            var updates = ((RenderDiff.LineUpdates) diff).updates();
            assertEquals(1, updates.size());
            assertEquals(RED + "hello" + RESET, updates.get(0).content());
        }

        @Test
        void sameAnsiContentNoChange() {
            String line = RED + "styled" + RESET;
            renderer.computeDiff(List.of(line));
            RenderDiff diff = renderer.computeDiff(List.of(line));

            assertInstanceOf(RenderDiff.LineUpdates.class, diff);
            assertTrue(((RenderDiff.LineUpdates) diff).updates().isEmpty());
        }
    }

    // -------------------------------------------------------------------
    // Reset
    // -------------------------------------------------------------------

    @Nested
    class Reset {

        @Test
        void resetForcesFullRerender() {
            renderer.computeDiff(List.of("a"));
            renderer.reset();
            RenderDiff diff = renderer.computeDiff(List.of("a"));
            assertInstanceOf(RenderDiff.FullRerender.class, diff);
        }

        @Test
        void resetClearsLastRendered() {
            renderer.computeDiff(List.of("a", "b"));
            renderer.reset();
            assertTrue(renderer.getLastRendered().isEmpty());
        }
    }

    // -------------------------------------------------------------------
    // Multiple consecutive diffs
    // -------------------------------------------------------------------

    @Nested
    class ConsecutiveDiffs {

        @Test
        void threeConsecutiveRenders() {
            // First: full rerender
            assertInstanceOf(RenderDiff.FullRerender.class,
                    renderer.computeDiff(List.of("a", "b")));

            // Second: single change
            RenderDiff diff2 = renderer.computeDiff(List.of("A", "b"));
            assertInstanceOf(RenderDiff.LineUpdates.class, diff2);
            assertEquals(1, ((RenderDiff.LineUpdates) diff2).updates().size());

            // Third: no change
            RenderDiff diff3 = renderer.computeDiff(List.of("A", "b"));
            assertInstanceOf(RenderDiff.LineUpdates.class, diff3);
            assertTrue(((RenderDiff.LineUpdates) diff3).updates().isEmpty());
        }

        @Test
        void fullRerenderThenIncrementalThenStructural() {
            // First: full rerender
            assertInstanceOf(RenderDiff.FullRerender.class,
                    renderer.computeDiff(List.of("a", "b")));

            // Second: incremental
            assertInstanceOf(RenderDiff.LineUpdates.class,
                    renderer.computeDiff(List.of("a", "B")));

            // Third: structural (line count change)
            assertInstanceOf(RenderDiff.FullRerender.class,
                    renderer.computeDiff(List.of("a", "B", "c")));
        }
    }

    // -------------------------------------------------------------------
    // RenderDiff types
    // -------------------------------------------------------------------

    @Nested
    class RenderDiffTypes {

        @Test
        void lineUpdatesIsImmutable() {
            var updates = List.of(new RenderDiff.LineUpdate(0, "hi"));
            var lineUpdates = new RenderDiff.LineUpdates(updates);
            assertThrows(UnsupportedOperationException.class,
                    () -> lineUpdates.updates().add(new RenderDiff.LineUpdate(1, "x")));
        }

        @Test
        void lineUpdateRecordEquality() {
            var a = new RenderDiff.LineUpdate(0, "hello");
            var b = new RenderDiff.LineUpdate(0, "hello");
            assertEquals(a, b);
        }

        @Test
        void fullRerenderEquality() {
            assertEquals(new RenderDiff.FullRerender(), new RenderDiff.FullRerender());
        }

        @Test
        void sealedInterfacePermits() {
            // Verify sealed interface hierarchy via pattern matching
            RenderDiff diff = new RenderDiff.FullRerender();
            String result = switch (diff) {
                case RenderDiff.LineUpdates lu -> "updates:" + lu.updates().size();
                case RenderDiff.FullRerender fr -> "full";
            };
            assertEquals("full", result);
        }

        @Test
        void patternMatchingOnLineUpdates() {
            RenderDiff diff = new RenderDiff.LineUpdates(
                    List.of(new RenderDiff.LineUpdate(2, "changed")));
            String result = switch (diff) {
                case RenderDiff.LineUpdates lu -> "updates:" + lu.updates().size();
                case RenderDiff.FullRerender fr -> "full";
            };
            assertEquals("updates:1", result);
        }
    }

    // -------------------------------------------------------------------
    // Null safety
    // -------------------------------------------------------------------

    @Nested
    class NullSafety {

        @Test
        void nullInputThrows() {
            assertThrows(NullPointerException.class, () -> renderer.computeDiff(null));
        }
    }
}
