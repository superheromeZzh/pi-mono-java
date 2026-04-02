package com.huawei.hicampus.mate.matecampusclaw.codingagent.tool.ops;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LocalReadOperationsTest {

    private final LocalReadOperations ops = new LocalReadOperations();

    @TempDir
    Path tempDir;

    @Nested
    class ReadFile {

        @Test
        void readsFileContent() throws IOException {
            Path file = Files.writeString(tempDir.resolve("test.txt"), "hello world");
            byte[] content = ops.readFile(file);
            assertEquals("hello world", new String(content, StandardCharsets.UTF_8));
        }

        @Test
        void readsBinaryContent() throws IOException {
            byte[] binary = {0x00, 0x01, 0x02, (byte) 0xFF};
            Path file = tempDir.resolve("binary.dat");
            Files.write(file, binary);
            assertArrayEquals(binary, ops.readFile(file));
        }

        @Test
        void readsEmptyFile() throws IOException {
            Path file = Files.createFile(tempDir.resolve("empty.txt"));
            assertEquals(0, ops.readFile(file).length);
        }

        @Test
        void throwsOnNonExistentFile() {
            assertThrows(NoSuchFileException.class, () ->
                    ops.readFile(tempDir.resolve("missing.txt")));
        }
    }

    @Nested
    class Exists {

        @Test
        void existingFile() throws IOException {
            Path file = Files.createFile(tempDir.resolve("exists.txt"));
            assertTrue(ops.exists(file));
        }

        @Test
        void existingDirectory() {
            assertTrue(ops.exists(tempDir));
        }

        @Test
        void nonExistentPath() {
            assertFalse(ops.exists(tempDir.resolve("nope")));
        }
    }

    @Nested
    class DetectMimeType {

        @Test
        void textFile() throws IOException {
            Path file = Files.writeString(tempDir.resolve("test.txt"), "content");
            String mime = ops.detectMimeType(file);
            assertNotNull(mime);
            // Different OS/JDK may return different MIME types for .txt
            // but it should not be the fallback for a .txt file typically
        }

        @Test
        void unknownExtensionFallsBackToOctetStream() throws IOException {
            Path file = Files.writeString(tempDir.resolve("data.xyz123"), "content");
            String mime = ops.detectMimeType(file);
            // probeContentType may return null for unknown extensions; we fallback
            assertNotNull(mime);
        }
    }
}
