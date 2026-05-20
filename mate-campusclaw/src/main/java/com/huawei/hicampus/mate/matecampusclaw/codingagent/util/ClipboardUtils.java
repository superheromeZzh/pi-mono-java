/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.hicampus.mate.matecampusclaw.codingagent.util;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Clipboard utilities supporting OSC 52 terminal escape and native pbcopy/xclip.
 *
 * @version [br_eCampusCore 25.1.0_Next, 2026/05/06]
 * @since [br_eCampusCore 25.1.0_Next]
 */
public final class ClipboardUtils {
    private static final Logger log = LoggerFactory.getLogger(ClipboardUtils.class);
    private static final long COPY_TIMEOUT_SECONDS = 10L;
    private static final long PASTE_TIMEOUT_SECONDS = 10L;
    private static final long WHICH_TIMEOUT_SECONDS = 5L;

    private ClipboardUtils() {}

    /**
     * Copy text to clipboard using best available method.
     *
     * @param text the text
     * @return the result
     */
    public static boolean copy(String text) {
        // Try native first
        if (tryNativeCopy(text)) {
            return true;
        }

        // Fall back to OSC 52 (works in most modern terminals)
        return tryOsc52Copy(text);
    }

    /**
     * Read text from clipboard using native tools.
     *
     * @return the result
     */
    public static Optional<String> paste() {
        return tryNativePaste();
    }

    /**
     * Write OSC 52 escape sequence to stdout for terminal clipboard. The OSC 52 byte
     * sequence is a terminal control protocol, not log output — it must reach the
     * controlling terminal verbatim, so it goes through System.out, never a logger.
     *
     * @param text the text
     * @return the result
     */
    @SuppressWarnings("checkstyle:no_system_out_err")
    public static boolean tryOsc52Copy(String text) {
        try {
            String b64 = java.util.Base64.getEncoder().encodeToString(text.getBytes(StandardCharsets.UTF_8));

            // OSC 52 format: ESC ] 52 ; c ; <base64> ST
            String osc52 = "\033]52;c;" + b64 + "\033\\";
            System.out.write(osc52.getBytes(StandardCharsets.UTF_8));
            System.out.flush();
            return true;
        } catch (IOException e) {
            log.debug("OSC 52 clipboard write failed", e);
            return false;
        }
    }

    private static boolean tryNativeCopy(String text) {
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        String[] cmd;
        if (os.contains("mac")) {
            cmd = new String[] {"pbcopy"};
        } else if (os.contains("linux")) {
            // Try xclip first, then xsel
            if (commandExists("xclip")) {
                cmd = new String[] {"xclip", "-selection", "clipboard"};
            } else if (commandExists("xsel")) {
                cmd = new String[] {"xsel", "--clipboard", "--input"};
            } else {
                return false;
            }
        } else if (os.contains("win")) {
            cmd = new String[] {"clip"};
        } else {
            return false;
        }

        try {
            // Discard stdout/stderr at OS level: pbcopy/xclip/xsel/clip occasionally print
            // warnings, and we never read them. Redirect.DISCARD is best-effort (not
            // guaranteed on macOS), so also drain the JVM-side streams to nullOutputStream
            // — without an explicit drain the pipe could fill and deadlock the writer.
            Process proc = new ProcessBuilder(cmd)
                    .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                    .redirectError(ProcessBuilder.Redirect.DISCARD)
                    .start();
            try (OutputStream out = proc.getOutputStream()) {
                out.write(text.getBytes(StandardCharsets.UTF_8));
            }
            drainToNull(proc.getInputStream());
            drainToNull(proc.getErrorStream());
            if (!proc.waitFor(COPY_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                proc.destroyForcibly();
                log.debug("Native clipboard copy timed out after {}s: {}", COPY_TIMEOUT_SECONDS, cmd[0]);
                return false;
            }
            return proc.exitValue() == 0;
        } catch (Exception e) {
            log.debug("Native clipboard copy failed", e);
            return false;
        }
    }

    private static Optional<String> tryNativePaste() {
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        String[] cmd;
        if (os.contains("mac")) {
            cmd = new String[] {"pbpaste"};
        } else if (os.contains("linux")) {
            if (commandExists("xclip")) {
                cmd = new String[] {"xclip", "-selection", "clipboard", "-o"};
            } else if (commandExists("xsel")) {
                cmd = new String[] {"xsel", "--clipboard", "--output"};
            } else {
                return Optional.empty();
            }
        } else {
            return Optional.empty();
        }

        try {
            Process proc = new ProcessBuilder(cmd).redirectErrorStream(true).start();

            // pbpaste / xclip -o / xsel --output do not read stdin; close it explicitly to
            // signal EOF defensively in case a future command does.
            proc.getOutputStream().close();
            String result = new String(proc.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            if (!proc.waitFor(PASTE_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                proc.destroyForcibly();
                log.debug("Native clipboard paste timed out after {}s", PASTE_TIMEOUT_SECONDS);
                return Optional.empty();
            }
            if (proc.exitValue() == 0) {
                return Optional.of(result);
            }
        } catch (Exception e) {
            log.debug("Native clipboard paste failed", e);
        }
        return Optional.empty();
    }

    private static boolean commandExists(String command) {
        try {
            Process proc = new ProcessBuilder("which", command)
                    .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                    .redirectError(ProcessBuilder.Redirect.DISCARD)
                    .start();

            // which does not read stdin; close it so the child sees EOF immediately.
            proc.getOutputStream().close();
            drainToNull(proc.getInputStream());
            drainToNull(proc.getErrorStream());
            if (!proc.waitFor(WHICH_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                proc.destroyForcibly();
                return false;
            }
            return proc.exitValue() == 0;
        } catch (Exception e) {
            log.debug("commandExists check failed: {}", command, e);
            return false;
        }
    }

    private static void drainToNull(InputStream in) throws IOException {
        try (in;
                OutputStream sink = OutputStream.nullOutputStream()) {
            in.transferTo(sink);
        }
    }
}
