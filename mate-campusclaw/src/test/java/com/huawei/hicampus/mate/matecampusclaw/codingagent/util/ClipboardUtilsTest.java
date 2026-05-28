/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.hicampus.mate.matecampusclaw.codingagent.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;

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

        /**
         * Round-trips through the public copy/paste API: writing a unique random string
         * via {@code copy()} and reading it back via {@code paste()}. On platforms with
         * a working native clipboard (macOS with pbpaste, Linux with xclip/xsel) the
         * round-trip must succeed; on platforms without it, paste() falls back to
         * {@code Optional.empty()} and the assertion is skipped via JUnit's Assumption
         * machinery so the test reports as skipped (not silently passing).
         */
        @Test
        void roundTripThroughCopyAndPaste() {
            String unique = "campusclaw-paste-test-" + System.nanoTime();
            boolean copyOk = assertDoesNotThrow(() -> ClipboardUtils.copy(unique));

            // copy() is always true on macOS/Linux/Windows (OSC52 fallback never fails
            // unless System.out is closed). If it ever returns false the contract is
            // already broken, so we assert that first.
            assertThat(copyOk).isTrue();

            // Best-effort paste; if no native tool is available (Windows / headless
            // container), paste() returns Optional.empty() — that is itself the
            // documented contract for unsupported platforms.
            Optional<String> pasted = assertDoesNotThrow(() -> ClipboardUtils.paste());
            org.junit.jupiter.api.Assumptions.assumeTrue(
                    pasted.isPresent(), "no native clipboard tool on this platform; paste roundtrip skipped");
            assertThat(pasted).contains(unique);
        }
    }

    // -------------------------------------------------------------------
    // drainToNull (65dd1a6d) — exercised via reflection
    // -------------------------------------------------------------------

    @Nested
    class DrainToNull {

        @Test
        void drainsBytesAndClosesStream() throws Exception {
            byte[] payload = "garbled subprocess output that nobody reads".getBytes(StandardCharsets.UTF_8);
            TrackingInputStream in = new TrackingInputStream(new ByteArrayInputStream(payload));

            Method m = ClipboardUtils.class.getDeclaredMethod("drainToNull", InputStream.class);
            m.setAccessible(true);
            m.invoke(null, in);

            assertThat(in.bytesRead).isEqualTo(payload.length);
            assertThat(in.closed).isTrue();
        }

        @Test
        void drainsEmptyStreamCleanly() throws Exception {
            TrackingInputStream in = new TrackingInputStream(new ByteArrayInputStream(new byte[0]));

            Method m = ClipboardUtils.class.getDeclaredMethod("drainToNull", InputStream.class);
            m.setAccessible(true);
            m.invoke(null, in);

            assertThat(in.bytesRead).isZero();
            assertThat(in.closed).isTrue();
        }

        private static final class TrackingInputStream extends InputStream {
            private final InputStream delegate;
            int bytesRead;
            boolean closed;

            TrackingInputStream(InputStream delegate) {
                this.delegate = delegate;
            }

            @Override
            public int read() throws IOException {
                int b = delegate.read();
                if (b != -1) {
                    bytesRead++;
                }
                return b;
            }

            @Override
            public int read(byte[] b, int off, int len) throws IOException {
                int n = delegate.read(b, off, len);
                if (n > 0) {
                    bytesRead += n;
                }
                return n;
            }

            @Override
            public void close() throws IOException {
                closed = true;
                delegate.close();
            }
        }
    }

    // -------------------------------------------------------------------
    // commandExists (65dd1a6d) — private helper, reflectively invoked
    // -------------------------------------------------------------------

    @Nested
    class CommandExists {

        @Test
        @DisabledOnOs(value = OS.WINDOWS, disabledReason = "`ls` is not on PATH on Windows; JUnit reports skipped")
        void returnsTrueForKnownCommand() throws Exception {
            Method m = ClipboardUtils.class.getDeclaredMethod("commandExists", String.class);
            m.setAccessible(true);
            assertThat((boolean) m.invoke(null, "ls")).isTrue();
        }

        @Test
        void returnsFalseForBogusCommand() throws Exception {
            Method m = ClipboardUtils.class.getDeclaredMethod("commandExists", String.class);
            m.setAccessible(true);
            assertThat((boolean) m.invoke(null, "definitely-not-a-real-command-xyz-" + System.nanoTime()))
                    .isFalse();
        }
    }
}
