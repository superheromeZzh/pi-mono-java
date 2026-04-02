package com.huawei.hicampus.mate.matecampusclaw.tui.component;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class KillRingTest {

    @Nested
    class PushAndPeek {

        @Test
        void peekEmptyReturnsNull() {
            var ring = new KillRing();
            assertNull(ring.peek());
        }

        @Test
        void pushAndPeekReturnsLatest() {
            var ring = new KillRing();
            ring.push("hello", false, false);
            assertEquals("hello", ring.peek());
        }

        @Test
        void pushMultipleReturnsLatest() {
            var ring = new KillRing();
            ring.push("first", false, false);
            ring.push("second", false, false);
            assertEquals("second", ring.peek());
        }

        @Test
        void pushEmptyIsIgnored() {
            var ring = new KillRing();
            ring.push("", false, false);
            ring.push(null, false, false);
            assertTrue(ring.isEmpty());
        }
    }

    @Nested
    class Accumulate {

        @Test
        void accumulateAppendsMergesWithLast() {
            var ring = new KillRing();
            ring.push("hello", false, false);
            ring.push(" world", false, true); // accumulate, append
            assertEquals(1, ring.size());
            assertEquals("hello world", ring.peek());
        }

        @Test
        void accumulatePrependMergesWithLast() {
            var ring = new KillRing();
            ring.push("world", false, false);
            ring.push("hello ", true, true); // accumulate, prepend
            assertEquals(1, ring.size());
            assertEquals("hello world", ring.peek());
        }

        @Test
        void accumulateOnEmptyRingJustPushes() {
            var ring = new KillRing();
            ring.push("hello", false, true); // accumulate but nothing to merge with
            assertEquals(1, ring.size());
            assertEquals("hello", ring.peek());
        }

        @Test
        void noAccumulateCreatesNewEntry() {
            var ring = new KillRing();
            ring.push("first", false, false);
            ring.push("second", false, false);
            assertEquals(2, ring.size());
        }
    }

    @Nested
    class Rotate {

        @Test
        void rotateMovesLastToFront() {
            var ring = new KillRing();
            ring.push("a", false, false);
            ring.push("b", false, false);
            ring.push("c", false, false);
            // Ring: [a, b, c] → peek = c
            ring.rotate();
            // Ring: [c, a, b] → peek = b
            assertEquals("b", ring.peek());
        }

        @Test
        void rotateWithSingleEntryDoesNothing() {
            var ring = new KillRing();
            ring.push("only", false, false);
            ring.rotate();
            assertEquals("only", ring.peek());
        }

        @Test
        void fullRotationCycle() {
            var ring = new KillRing();
            ring.push("a", false, false);
            ring.push("b", false, false);
            ring.push("c", false, false);

            // [a,b,c] → peek=c
            assertEquals("c", ring.peek());
            ring.rotate(); // [c,a,b] → peek=b
            assertEquals("b", ring.peek());
            ring.rotate(); // [b,c,a] → peek=a
            assertEquals("a", ring.peek());
            ring.rotate(); // [a,b,c] → peek=c (back to start)
            assertEquals("c", ring.peek());
        }
    }

    @Nested
    class SizeAndClear {

        @Test
        void sizeTracksEntries() {
            var ring = new KillRing();
            assertEquals(0, ring.size());
            ring.push("a", false, false);
            assertEquals(1, ring.size());
            ring.push("b", false, false);
            assertEquals(2, ring.size());
        }

        @Test
        void clearRemovesAll() {
            var ring = new KillRing();
            ring.push("a", false, false);
            ring.push("b", false, false);
            ring.clear();
            assertTrue(ring.isEmpty());
            assertNull(ring.peek());
        }
    }
}
