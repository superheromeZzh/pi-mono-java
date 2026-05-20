/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.campusclaw.tui.image;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class TerminalImageProtocolTest {

    @Nested
    class KittyImage {

        @Test
        void emitsKittyEscapeWithBase64() {
            byte[] data = "hello".getBytes(java.nio.charset.StandardCharsets.UTF_8);
            String seq = TerminalImageProtocol.kittyImage(data, 0, 0);
            assertTrue(seq.startsWith("\033_G"));
            assertTrue(seq.contains("a=T,f=100"));
            assertTrue(seq.contains("aGVsbG8=")); // base64("hello")
            assertTrue(seq.endsWith("\033\\"));
        }

        @Test
        void includesWidthAndHeight() {
            String seq = TerminalImageProtocol.kittyImage("x".getBytes(java.nio.charset.StandardCharsets.UTF_8), 10, 5);
            assertTrue(seq.contains("c=10"));
            assertTrue(seq.contains("r=5"));
        }

        @Test
        void chunksLargePayload() {
            byte[] data = new byte[10000]; // > 4096 base64-encoded
            String seq = TerminalImageProtocol.kittyImage(data, 0, 0);

            // multiple chunks → multiple ESC_G ... ESC \
            int chunks = (seq.length() - seq.replace("\033_G", "").length()) / 3;
            assertTrue(chunks >= 2);
            assertTrue(seq.contains("m=1"));
            assertTrue(seq.contains("m=0"));
        }
    }

    @Nested
    class Iterm2Image {

        @Test
        void emitsIterm2Escape() {
            String seq = TerminalImageProtocol.iterm2Image(
                    "hi".getBytes(java.nio.charset.StandardCharsets.UTF_8), "100px", "50px", true);
            assertTrue(seq.startsWith("\033]1337;File=inline=1"));
            assertTrue(seq.contains("size=2"));
            assertTrue(seq.contains("width=100px"));
            assertTrue(seq.contains("height=50px"));
            assertTrue(seq.contains("preserveAspectRatio=1"));
            assertTrue(seq.endsWith("\007"));
            assertTrue(seq.contains("aGk="));
        }

        @Test
        void omitsOptionalSizes() {
            String seq = TerminalImageProtocol.iterm2Image(
                    "x".getBytes(java.nio.charset.StandardCharsets.UTF_8), null, null, false);
            assertFalse(seq.contains("width="));
            assertFalse(seq.contains("height="));
            assertTrue(seq.contains("preserveAspectRatio=0"));
        }
    }

    @Nested
    class RenderImage {

        @Test
        void returnsNonNullOrNullDependingOnProtocol() {
            // Detection depends on environment — outcome depends on which protocol detect() picked.
            TerminalImageProtocol.Protocol p = TerminalImageProtocol.detect();
            String result =
                    TerminalImageProtocol.renderImage("x".getBytes(java.nio.charset.StandardCharsets.UTF_8), 10, 5);
            if (p == TerminalImageProtocol.Protocol.NONE || p == TerminalImageProtocol.Protocol.SIXEL) {
                assertEquals(null, result);
            } else {
                assertTrue(result != null && !result.isEmpty());
            }
        }
    }

    @Nested
    class Detect {

        @Test
        void returnsAProtocol() {
            TerminalImageProtocol.Protocol p = TerminalImageProtocol.detect();
            assertNotNull(p);
        }
    }

    @Nested
    class IsSupported {

        @Test
        void matchesDetection() {
            boolean supported = TerminalImageProtocol.isSupported();
            boolean expected = TerminalImageProtocol.detect() != TerminalImageProtocol.Protocol.NONE;
            assertEquals(expected, supported);
        }
    }
}
