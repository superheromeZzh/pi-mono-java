package com.huawei.hicampus.mate.matecampusclaw.codingagent.util;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Clipboard utilities supporting OSC 52 terminal escape and native pbcopy/xclip.
 */
public final class ClipboardUtils {
    private static final Logger log = LoggerFactory.getLogger(ClipboardUtils.class);

    private ClipboardUtils() {}

    /** Copy text to clipboard using best available method. */
    public static boolean copy(String text) {
        // Try native first
        if (tryNativeCopy(text)) return true;
        // Fall back to OSC 52 (works in most modern terminals)
        return tryOsc52Copy(text);
    }

    /** Read text from clipboard using native tools. */
    public static Optional<String> paste() {
        return tryNativePaste();
    }

    /** Write OSC 52 escape sequence to stdout for terminal clipboard. */
    public static boolean tryOsc52Copy(String text) {
        try {
            String b64 = java.util.Base64.getEncoder().encodeToString(
                text.getBytes(StandardCharsets.UTF_8));
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
        String os = System.getProperty("os.name", "").toLowerCase();
        String[] cmd;
        if (os.contains("mac")) {
            cmd = new String[]{"pbcopy"};
        } else if (os.contains("linux")) {
            // Try xclip first, then xsel
            if (commandExists("xclip")) {
                cmd = new String[]{"xclip", "-selection", "clipboard"};
            } else if (commandExists("xsel")) {
                cmd = new String[]{"xsel", "--clipboard", "--input"};
            } else {
                return false;
            }
        } else if (os.contains("win")) {
            cmd = new String[]{"clip"};
        } else {
            return false;
        }

        try {
            Process proc = new ProcessBuilder(cmd)
                .redirectErrorStream(true)
                .start();
            try (OutputStream out = proc.getOutputStream()) {
                out.write(text.getBytes(StandardCharsets.UTF_8));
            }
            return proc.waitFor() == 0;
        } catch (Exception e) {
            log.debug("Native clipboard copy failed", e);
            return false;
        }
    }

    private static Optional<String> tryNativePaste() {
        String os = System.getProperty("os.name", "").toLowerCase();
        String[] cmd;
        if (os.contains("mac")) {
            cmd = new String[]{"pbpaste"};
        } else if (os.contains("linux")) {
            if (commandExists("xclip")) {
                cmd = new String[]{"xclip", "-selection", "clipboard", "-o"};
            } else if (commandExists("xsel")) {
                cmd = new String[]{"xsel", "--clipboard", "--output"};
            } else {
                return Optional.empty();
            }
        } else {
            return Optional.empty();
        }

        try {
            Process proc = new ProcessBuilder(cmd)
                .redirectErrorStream(true)
                .start();
            String result = new String(proc.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            if (proc.waitFor() == 0) {
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
                .redirectErrorStream(true)
                .start();
            return proc.waitFor() == 0;
        } catch (Exception e) {
            return false;
        }
    }
}
