/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.campusclaw.tui.terminal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class StdinBufferTest {

    @Nested
    class BasicLifecycle {

        @Test
        void newBufferIsEmpty() {
            StdinBuffer b = new StdinBuffer();
            assertTrue(b.isEmpty());
            assertEquals(0, b.size());
            assertTrue(b.drain().isEmpty());
        }

        @Test
        void appendNullAndEmptyIgnored() {
            StdinBuffer b = new StdinBuffer();
            b.append(null);
            b.append("");
            assertEquals(0, b.size());
        }

        @Test
        void clearResetsBuffer() {
            StdinBuffer b = new StdinBuffer();
            b.append("abc");
            b.clear();
            assertTrue(b.isEmpty());
        }
    }

    @Nested
    class RegularCharacters {

        @Test
        void appendAndDrainSingleChar() {
            StdinBuffer b = new StdinBuffer();
            b.append("a");
            List<String> events = b.drain();
            assertEquals(List.of("a"), events);
            assertTrue(b.isEmpty());
        }

        @Test
        void multipleCharsIndividually() {
            StdinBuffer b = new StdinBuffer();
            b.append("abc");
            List<String> events = b.drain();
            assertEquals(List.of("a", "b", "c"), events);
        }
    }

    @Nested
    class CsiSequences {

        @Test
        void completeCsi() {
            StdinBuffer b = new StdinBuffer();
            b.append("\033[A");
            assertEquals(List.of("\033[A"), b.drain());
        }

        @Test
        void csiWithParameters() {
            StdinBuffer b = new StdinBuffer();
            b.append("\033[1;5A");
            assertEquals(List.of("\033[1;5A"), b.drain());
        }

        @Test
        void csiWithIntermediate() {
            StdinBuffer b = new StdinBuffer();

            // ESC [ 1 ! @ — has intermediate byte
            b.append("\033[1!@");
            assertEquals(List.of("\033[1!@"), b.drain());
        }

        @Test
        void incompleteCsiHeldUntilCompletion() {
            StdinBuffer b = new StdinBuffer();
            b.append("\033[");
            assertTrue(b.drain().isEmpty());
            b.append("1");
            assertTrue(b.drain().isEmpty());
            b.append("A");
            assertEquals(List.of("\033[1A"), b.drain());
        }

        @Test
        void invalidCsiFinalByteYieldsLiteralEscBracket() {
            StdinBuffer b = new StdinBuffer();

            // 0x1F is below 0x20 so not a valid final byte
            b.append("\033[");
            List<String> events = b.drain();

            // First event is \033[, then the next char re-parsed
            assertEquals("\033[", events.get(0));
        }
    }

    @Nested
    class Ss3Sequences {

        @Test
        void completeSs3() {
            StdinBuffer b = new StdinBuffer();
            b.append("\033OP");
            assertEquals(List.of("\033OP"), b.drain());
        }

        @Test
        void incompleteSs3Held() {
            StdinBuffer b = new StdinBuffer();
            b.append("\033O");
            assertTrue(b.drain().isEmpty());
            assertFalse(b.isEmpty());
            b.append("P");
            assertEquals(List.of("\033OP"), b.drain());
        }
    }

    @Nested
    class AltKeys {

        @Test
        void altPlusPrintableChar() {
            StdinBuffer b = new StdinBuffer();
            b.append("\033a");
            assertEquals(List.of("\033a"), b.drain());
        }

        @Test
        void unknownEscFollowedByControlYieldsBareEsc() {
            StdinBuffer b = new StdinBuffer();

            // 0x7F (DEL) is not in 0x20-0x7E range — should yield bare ESC, then DEL handled normally
            b.append("\033");
            List<String> events = b.drain();
            assertEquals("\033", events.get(0));
        }
    }

    @Nested
    class BareEscape {

        @Test
        void bareEscHeldUntilTimeout() throws InterruptedException {
            StdinBuffer b = new StdinBuffer();
            b.append("\033");
            assertTrue(b.drain().isEmpty());

            // Wait longer than ESCAPE_TIMEOUT_MS
            Thread.sleep(100);
            assertEquals(List.of("\033"), b.drain());
        }
    }
}
