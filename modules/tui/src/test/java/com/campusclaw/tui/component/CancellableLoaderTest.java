/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.campusclaw.tui.component;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class CancellableLoaderTest {

    @Nested
    class Construction {

        @Test
        void defaultHint() {
            CancellableLoader cl = new CancellableLoader("loading");
            List<String> lines = cl.render(80);
            assertTrue(lines.size() >= 2);
            assertTrue(lines.get(1).contains("Press Escape"));
        }

        @Test
        void customHint() {
            CancellableLoader cl = new CancellableLoader("loading", "Hit q to quit");
            List<String> lines = cl.render(80);
            assertTrue(lines.get(1).contains("Hit q to quit"));
        }
    }

    @Nested
    class Cancellation {

        @Test
        void notCancelledByDefault() {
            CancellableLoader cl = new CancellableLoader("x");
            assertFalse(cl.isCancelled());
        }

        @Test
        void cancelSetsFlag() {
            CancellableLoader cl = new CancellableLoader("x");
            cl.cancel();
            assertTrue(cl.isCancelled());
        }

        @Test
        void cancelInvokesCallback() {
            CancellableLoader cl = new CancellableLoader("x");
            AtomicInteger counter = new AtomicInteger();
            cl.setOnCancel(counter::incrementAndGet);
            cl.cancel();
            assertEquals(1, counter.get());
        }

        @Test
        void escapeKeyTriggersCancel() {
            CancellableLoader cl = new CancellableLoader("x");
            cl.handleInput("\033");
            assertTrue(cl.isCancelled());
        }

        @Test
        void otherInputDoesNotCancel() {
            CancellableLoader cl = new CancellableLoader("x");
            cl.handleInput("a");
            assertFalse(cl.isCancelled());
        }

        @Test
        void cancelledRendersMessage() {
            CancellableLoader cl = new CancellableLoader("x");
            cl.cancel();
            assertEquals(List.of("Cancelled."), cl.render(80));
        }

        @Test
        void resetClears() {
            CancellableLoader cl = new CancellableLoader("x");
            cl.cancel();
            cl.reset();
            assertFalse(cl.isCancelled());
        }

        @Test
        void invalidateClears() {
            CancellableLoader cl = new CancellableLoader("x");
            cl.cancel();
            cl.invalidate();
            assertFalse(cl.isCancelled());
        }
    }

    @Nested
    class Updates {

        @Test
        void setMessageAndCancelHint() {
            CancellableLoader cl = new CancellableLoader("a");
            cl.setMessage("b");
            cl.setCancelHint("Press X");
            List<String> lines = cl.render(80);
            assertTrue(lines.get(0).contains("b"));
            assertTrue(lines.get(1).contains("Press X"));
        }

        @Test
        void emptyCancelHintSkipsHintLine() {
            CancellableLoader cl = new CancellableLoader("x", "");
            List<String> lines = cl.render(80);
            assertEquals(1, lines.size());
        }
    }
}
