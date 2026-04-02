package com.huawei.hicampus.mate.matecampusclaw.codingagent.util;

import java.io.IOException;
import java.nio.file.Path;

/**
 * Utilities for resolving user-supplied file paths relative to a working directory.
 * All resolved paths are validated to stay within the cwd subtree to prevent directory traversal attacks.
 */
public final class PathUtils {

    private PathUtils() {
    }

    /**
     * Resolves an input path relative to the current working directory.
     * If the input is already absolute, it is normalized directly.
     * The resolved path must be within the cwd subtree.
     *
     * @param input the user-supplied path string
     * @param cwd   the current working directory (must be absolute)
     * @return the resolved, normalized absolute path
     * @throws SecurityException    if the resolved path escapes the cwd subtree
     * @throws IllegalArgumentException if input is null/blank or cwd is not absolute
     */
    public static Path resolveToCwd(String input, Path cwd) {
        validateArgs(input, cwd);

        Path resolved = cwd.resolve(input).normalize();
        ensureWithinCwd(resolved, cwd);
        return resolved;
    }

    /**
     * Resolves a read path, following symlinks to verify the real path stays within cwd.
     * This provides stronger protection than {@link #resolveToCwd} by resolving symlinks
     * that could point outside the cwd subtree.
     *
     * @param input the user-supplied path string
     * @param cwd   the current working directory (must be absolute)
     * @return the resolved, normalized absolute path (not the real path — the logical path is returned)
     * @throws SecurityException    if the resolved or real path escapes the cwd subtree
     * @throws IllegalArgumentException if input is null/blank or cwd is not absolute
     */
    public static Path resolveReadPath(String input, Path cwd) {
        validateArgs(input, cwd);

        Path resolved = cwd.resolve(input).normalize();
        ensureWithinCwd(resolved, cwd);

        // If the path exists, also check the real (symlink-resolved) path
        if (resolved.toFile().exists()) {
            try {
                Path realPath = resolved.toRealPath();
                Path realCwd = cwd.toRealPath();
                ensureWithinCwd(realPath, realCwd);
            } catch (IOException e) {
                throw new SecurityException("Failed to resolve real path: " + resolved, e);
            }
        }

        return resolved;
    }

    private static void validateArgs(String input, Path cwd) {
        if (input == null || input.isBlank()) {
            throw new IllegalArgumentException("Path input must not be null or blank");
        }
        if (cwd == null || !cwd.isAbsolute()) {
            throw new IllegalArgumentException("cwd must be an absolute path");
        }
    }

    private static void ensureWithinCwd(Path resolved, Path cwd) {
        if (!resolved.startsWith(cwd)) {
            throw new SecurityException(
                    "Path traversal denied: resolved path '" + resolved + "' is outside cwd '" + cwd + "'");
        }
    }
}
