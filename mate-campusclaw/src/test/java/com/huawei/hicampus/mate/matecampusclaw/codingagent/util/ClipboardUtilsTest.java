/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.hicampus.mate.matecampusclaw.codingagent.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintStream;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
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
        void pasteDoesNotThrow() {
            // pbpaste / xclip / xsel may or may not be available — assert via doesNotThrow
            // so the test fails on regression rather than silently passing on non-null.
            assertThatNoException().isThrownBy(ClipboardUtils::paste);
        }

        @Test
        void pasteOnUnsupportedOsReturnsEmpty() {
            String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);

            // Only assert on platforms where paste cannot succeed (no pbpaste/xclip/xsel branch).
            // On macOS / Linux the call may legitimately return a value, so we skip the assertion.
            boolean nativeUnavailable = !os.contains("mac") && !os.contains("linux") && !commandOnPath("pbpaste");
            if (nativeUnavailable) {
                Optional<String> result = ClipboardUtils.paste();
                assertThat(result).isEmpty();
            }
        }

        private static boolean commandOnPath(String c) {
            try {
                Process p =
                        new ProcessBuilder("which", c).redirectErrorStream(true).start();
                p.getOutputStream().close();
                p.getInputStream().readAllBytes();
                return p.waitFor() == 0;
            } catch (Exception e) {
                return false;
            }
        }
    }

    // -------------------------------------------------------------------
    // OSC 52 IOException fallback (65dd1a6d)
    // -------------------------------------------------------------------

    /**
     * Forces the IOException catch on lines 75-77 of ClipboardUtils. We swap
     * {@code System.out} for a PrintStream whose underlying OutputStream always
     * throws — but note PrintStream itself swallows IOExceptions, so this only
     * triggers the catch when the byte write goes through {@code System.out.write(...)}.
     */
    @Nested
    class Osc52IoFailure {

        @Test
        void doesNotThrowAndReturnsBooleanWhenStreamFails() throws Exception {
            PrintStream original = System.out;

            // PrintStream catches IO errors and sets an internal flag; tryOsc52Copy
            // observes this via System.out.write throwing only when wrapping a
            // ThrowingOutputStream that propagates. Use the latter directly:
            ThrowingOutputStream bad = new ThrowingOutputStream();
            System.setOut(new PrintStream(bad, true, StandardCharsets.UTF_8));
            try {
                // Either branch (true or false) is acceptable — what we are guarding
                // against is an uncaught IOException leaking out of tryOsc52Copy.
                assertThatNoException().isThrownBy(() -> ClipboardUtils.tryOsc52Copy("payload"));
            } finally {
                System.setOut(original);
            }
        }

        private static final class ThrowingOutputStream extends OutputStream {
            @Override
            public void write(int b) throws IOException {
                throw new IOException("induced failure");
            }

            @Override
            public void write(byte[] b, int off, int len) throws IOException {
                throw new IOException("induced failure");
            }
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
        void returnsTrueForKnownCommand() throws Exception {
            // `ls` exists on macOS/Linux PATH; on Windows the test runner has `cmd` but not `ls`,
            // so we skip the positive case there.
            String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
            if (os.contains("win")) {
                return;
            }
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
