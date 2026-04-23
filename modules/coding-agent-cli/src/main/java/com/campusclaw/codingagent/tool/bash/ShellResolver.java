package com.campusclaw.codingagent.tool.bash;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Locates a usable bash shell on the current machine.
 *
 * <p>Resolution order mirrors the pi-mono reference implementation:
 * <ul>
 *   <li>Windows: {@code %ProgramFiles%\Git\bin\bash.exe} → {@code %ProgramFiles(x86)%\Git\bin\bash.exe}
 *       → {@code where bash.exe}.</li>
 *   <li>Unix: {@code /bin/bash} → {@code which bash} → {@code sh}.</li>
 * </ul>
 * The result is cached for the lifetime of the JVM — the shell layout of the host
 * does not change at runtime.
 */
public final class ShellResolver {

    /** Resolved shell plus the flag used to pass a single command string. */
    public record ShellConfig(String shell, List<String> args) {
        public ShellConfig {
            args = List.copyOf(args);
        }
    }

    private static volatile ShellConfig cached;

    private ShellResolver() {}

    /** Resolve a shell, caching the result. Throws if no shell can be located (Windows only). */
    public static ShellConfig resolve() {
        ShellConfig local = cached;
        if (local != null) {
            return local;
        }
        synchronized (ShellResolver.class) {
            if (cached == null) {
                cached = doResolve();
            }
            return cached;
        }
    }

    /** Reset the cache. Intended for tests. */
    static void resetCacheForTesting() {
        cached = null;
    }

    private static ShellConfig doResolve() {
        if (isWindows()) {
            List<String> probed = new ArrayList<>();
            String programFiles = System.getenv("ProgramFiles");
            if (programFiles != null) {
                probed.add(programFiles + "\\Git\\bin\\bash.exe");
            }
            String programFilesX86 = System.getenv("ProgramFiles(x86)");
            if (programFilesX86 != null) {
                probed.add(programFilesX86 + "\\Git\\bin\\bash.exe");
            }
            for (String candidate : probed) {
                if (Files.isRegularFile(Path.of(candidate))) {
                    return new ShellConfig(candidate, List.of("-c"));
                }
            }
            String fromPath = findOnPath("where", "bash.exe", true);
            if (fromPath != null) {
                return new ShellConfig(fromPath, List.of("-c"));
            }
            StringBuilder msg = new StringBuilder()
                    .append("No bash shell found. Options:\n")
                    .append("  1. Install Git for Windows: https://git-scm.com/download/win\n")
                    .append("  2. Add bash.exe to PATH (Cygwin, MSYS2, WSL, etc.)\n")
                    .append("  3. Set the CAMPUSCLAW_SHELL environment variable to an absolute bash.exe path\n\n")
                    .append("Searched Git Bash in:\n");
            for (String p : probed) {
                msg.append("  ").append(p).append('\n');
            }
            throw new IllegalStateException(msg.toString());
        }

        // Allow an explicit override anywhere (primarily useful for exotic Unix setups).
        String override = System.getenv("CAMPUSCLAW_SHELL");
        if (override != null && !override.isBlank() && Files.isRegularFile(Path.of(override))) {
            return new ShellConfig(override, List.of("-c"));
        }

        if (Files.isRegularFile(Path.of("/bin/bash"))) {
            return new ShellConfig("/bin/bash", List.of("-c"));
        }
        String fromPath = findOnPath("which", "bash", false);
        if (fromPath != null) {
            return new ShellConfig(fromPath, List.of("-c"));
        }
        // Last resort — POSIX systems are required to ship sh.
        return new ShellConfig("sh", List.of("-c"));
    }

    /**
     * Run {@code which}/{@code where} to locate an executable on PATH.
     * On Windows {@code where} sometimes returns paths that no longer exist, so the result is
     * verified with {@link Files#isRegularFile(Path, java.nio.file.LinkOption...)}.
     */
    private static String findOnPath(String locator, String target, boolean verifyExists) {
        try {
            Process p = new ProcessBuilder(locator, target)
                    .redirectErrorStream(true)
                    .start();
            StringBuilder sb = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (sb.length() == 0) {
                        sb.append(line);
                    }
                }
            }
            if (!p.waitFor(5, TimeUnit.SECONDS)) {
                p.destroyForcibly();
                return null;
            }
            if (p.exitValue() != 0) {
                return null;
            }
            String first = sb.toString().trim();
            if (first.isEmpty()) {
                return null;
            }
            if (verifyExists && !Files.isRegularFile(Path.of(first))) {
                return null;
            }
            return first;
        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            return null;
        }
    }

    private static boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase().contains("win");
    }
}
