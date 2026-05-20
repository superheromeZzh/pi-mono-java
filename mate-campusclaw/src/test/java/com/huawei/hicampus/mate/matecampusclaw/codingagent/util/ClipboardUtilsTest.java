/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.hicampus.mate.matecampusclaw.codingagent.util;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class ClipboardUtilsTest {

    @Nested
    class Osc52 {

        @Test
        void writesEscapeSequenceToStdout() throws Exception {
            PrintStream original = System.out;
            ByteArrayOutputStream buf = new ByteArrayOutputStream();
            System.setOut(new PrintStream(buf, true, StandardCharsets.UTF_8));
            try {
                boolean ok = ClipboardUtils.tryOsc52Copy("hello");
                assertThat(ok).isTrue();
                String out = buf.toString(StandardCharsets.UTF_8);
                assertThat(out).startsWith("\033]52;c;").endsWith("\033\\");

                // base64("hello") == "aGVsbG8="
                assertThat(out).contains("aGVsbG8=");
            } finally {
                System.setOut(original);
            }
        }

        @Test
        void emptyStringStillWrites() throws Exception {
            PrintStream original = System.out;
            ByteArrayOutputStream buf = new ByteArrayOutputStream();
            System.setOut(new PrintStream(buf, true, StandardCharsets.UTF_8));
            try {
                boolean ok = ClipboardUtils.tryOsc52Copy("");
                assertThat(ok).isTrue();
                String out = buf.toString(StandardCharsets.UTF_8);
                assertThat(out).isEqualTo("\033]52;c;\033\\");
            } finally {
                System.setOut(original);
            }
        }
    }

    @Nested
    class CopyFallback {

        @Test
        void copyReturnsTrueOnAnyPath() {
            // copy() succeeds whenever native or OSC 52 fallback works; OSC 52 path always succeeds
            // unless System.out throws, which we cannot easily induce here.
            // Use unique text so test does not pollute a global clipboard with predictable content.
            String text = "copy-text-" + System.nanoTime();
            boolean ok = ClipboardUtils.copy(text);
            assertThat(ok).isTrue();
        }
    }

    @Nested
    class Paste {

        @Test
        void pasteReturnsOptional() {
            // pbpaste / xclip / xsel may or may not be available — we only check that a call
            // produces an Optional without throwing.
            Optional<String> result = ClipboardUtils.paste();
            assertThat(result).isNotNull();
        }
    }
}
