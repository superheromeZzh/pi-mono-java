package com.huawei.hicampus.mate.matecampusclaw.codingagent.util;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PathUtilsTest {

    @TempDir
    Path tempDir;

    // -------------------------------------------------------------------
    // resolveToCwd
    // -------------------------------------------------------------------

    @Nested
    class ResolveToCwd {

        @Test
        void relativePathResolved() {
            Path result = PathUtils.resolveToCwd("foo/bar.txt", tempDir);
            assertEquals(tempDir.resolve("foo/bar.txt"), result);
        }

        @Test
        void simpleFilename() {
            Path result = PathUtils.resolveToCwd("file.txt", tempDir);
            assertEquals(tempDir.resolve("file.txt"), result);
        }

        @Test
        void nestedRelativePath() {
            Path result = PathUtils.resolveToCwd("a/b/c/d.txt", tempDir);
            assertEquals(tempDir.resolve("a/b/c/d.txt"), result);
        }

        @Test
        void dotSlashNormalized() {
            Path result = PathUtils.resolveToCwd("./foo/bar.txt", tempDir);
            assertEquals(tempDir.resolve("foo/bar.txt"), result);
        }

        @Test
        void internalDotDotNormalized() {
            // "a/../b" normalizes to "b" which is still inside cwd
            Path result = PathUtils.resolveToCwd("a/../b/file.txt", tempDir);
            assertEquals(tempDir.resolve("b/file.txt"), result);
        }

        @Test
        void absolutePathInsideCwd() {
            Path absPath = tempDir.resolve("sub/file.txt");
            Path result = PathUtils.resolveToCwd(absPath.toString(), tempDir);
            assertEquals(absPath, result);
        }

        @Test
        void cwdItselfIsAllowed() {
            // Resolving "." should give back cwd
            Path result = PathUtils.resolveToCwd(".", tempDir);
            assertEquals(tempDir, result);
        }

        // --- directory traversal attacks ---

        @Test
        void dotDotEscapeBlocked() {
            assertThrows(SecurityException.class, () ->
                    PathUtils.resolveToCwd("../outside.txt", tempDir));
        }

        @Test
        void deepDotDotEscapeBlocked() {
            assertThrows(SecurityException.class, () ->
                    PathUtils.resolveToCwd("a/b/../../../../etc/passwd", tempDir));
        }

        @Test
        void absolutePathOutsideCwdBlocked() {
            assertThrows(SecurityException.class, () ->
                    PathUtils.resolveToCwd("/etc/passwd", tempDir));
        }

        @Test
        void dotDotFromSubdirEscapeBlocked() {
            assertThrows(SecurityException.class, () ->
                    PathUtils.resolveToCwd("sub/../../outside", tempDir));
        }

        // --- invalid arguments ---

        @Test
        void nullInputThrows() {
            assertThrows(IllegalArgumentException.class, () ->
                    PathUtils.resolveToCwd(null, tempDir));
        }

        @Test
        void blankInputThrows() {
            assertThrows(IllegalArgumentException.class, () ->
                    PathUtils.resolveToCwd("   ", tempDir));
        }

        @Test
        void nullCwdThrows() {
            assertThrows(IllegalArgumentException.class, () ->
                    PathUtils.resolveToCwd("file.txt", null));
        }

        @Test
        void relativeCwdThrows() {
            assertThrows(IllegalArgumentException.class, () ->
                    PathUtils.resolveToCwd("file.txt", Path.of("relative/dir")));
        }
    }

    // -------------------------------------------------------------------
    // resolveReadPath
    // -------------------------------------------------------------------

    @Nested
    class ResolveReadPath {

        @Test
        void relativePathResolved() {
            Path result = PathUtils.resolveReadPath("foo/bar.txt", tempDir);
            assertEquals(tempDir.resolve("foo/bar.txt"), result);
        }

        @Test
        void dotDotEscapeBlocked() {
            assertThrows(SecurityException.class, () ->
                    PathUtils.resolveReadPath("../outside.txt", tempDir));
        }

        @Test
        void absolutePathOutsideCwdBlocked() {
            assertThrows(SecurityException.class, () ->
                    PathUtils.resolveReadPath("/etc/passwd", tempDir));
        }

        @Test
        void nullInputThrows() {
            assertThrows(IllegalArgumentException.class, () ->
                    PathUtils.resolveReadPath(null, tempDir));
        }

        @Test
        void existingFileInsideCwd() throws IOException {
            Path file = Files.createFile(tempDir.resolve("real.txt"));
            Path result = PathUtils.resolveReadPath("real.txt", tempDir);
            assertEquals(file, result);
        }

        @Test
        void nonExistentPathStillResolves() {
            // Non-existent paths skip the real-path check but still validate logically
            Path result = PathUtils.resolveReadPath("nonexistent/file.txt", tempDir);
            assertEquals(tempDir.resolve("nonexistent/file.txt"), result);
        }

        // --- symlink tests ---

        @Test
        void symlinkInsideCwdAllowed() throws IOException {
            Path target = Files.createFile(tempDir.resolve("target.txt"));
            Path link = tempDir.resolve("link.txt");
            Files.createSymbolicLink(link, target);

            Path result = PathUtils.resolveReadPath("link.txt", tempDir);
            assertEquals(link, result);
        }

        @Test
        void symlinkPointingOutsideCwdBlocked() throws IOException {
            // Create a file outside tempDir
            Path outsideDir = Files.createTempDirectory("outside");
            Path outsideFile = Files.createFile(outsideDir.resolve("secret.txt"));

            try {
                // Create a symlink inside tempDir pointing to the outside file
                Path link = tempDir.resolve("escape-link.txt");
                Files.createSymbolicLink(link, outsideFile);

                assertThrows(SecurityException.class, () ->
                        PathUtils.resolveReadPath("escape-link.txt", tempDir));
            } finally {
                Files.deleteIfExists(outsideDir.resolve("secret.txt"));
                Files.deleteIfExists(outsideDir);
            }
        }

        @Test
        void symlinkDirPointingOutsideCwdBlocked() throws IOException {
            Path outsideDir = Files.createTempDirectory("outside-dir");
            Files.createFile(outsideDir.resolve("data.txt"));

            try {
                Path linkDir = tempDir.resolve("linked-dir");
                Files.createSymbolicLink(linkDir, outsideDir);

                assertThrows(SecurityException.class, () ->
                        PathUtils.resolveReadPath("linked-dir/data.txt", tempDir));
            } finally {
                Files.deleteIfExists(outsideDir.resolve("data.txt"));
                Files.deleteIfExists(outsideDir);
            }
        }
    }
}
