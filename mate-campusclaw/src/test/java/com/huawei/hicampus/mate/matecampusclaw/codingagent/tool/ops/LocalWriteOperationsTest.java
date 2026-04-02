package com.huawei.hicampus.mate.matecampusclaw.codingagent.tool.ops;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LocalWriteOperationsTest {

    private final LocalWriteOperations ops = new LocalWriteOperations();

    @TempDir
    Path tempDir;

    @Nested
    class WriteFile {

        @Test
        void writesNewFile() throws IOException {
            Path file = tempDir.resolve("new.txt");
            ops.writeFile(file, "hello");
            assertEquals("hello", Files.readString(file));
        }

        @Test
        void overwritesExistingFile() throws IOException {
            Path file = Files.writeString(tempDir.resolve("existing.txt"), "old");
            ops.writeFile(file, "new");
            assertEquals("new", Files.readString(file));
        }

        @Test
        void createsParentDirectories() throws IOException {
            Path file = tempDir.resolve("a/b/c/deep.txt");
            ops.writeFile(file, "deep content");
            assertTrue(Files.exists(file));
            assertEquals("deep content", Files.readString(file));
        }

        @Test
        void writesEmptyContent() throws IOException {
            Path file = tempDir.resolve("empty.txt");
            ops.writeFile(file, "");
            assertEquals("", Files.readString(file));
        }

        @Test
        void writesUtf8Content() throws IOException {
            Path file = tempDir.resolve("utf8.txt");
            ops.writeFile(file, "你好世界");
            assertEquals("你好世界", Files.readString(file));
        }
    }

    @Nested
    class Mkdir {

        @Test
        void createsSingleDirectory() throws IOException {
            Path dir = tempDir.resolve("newdir");
            ops.mkdir(dir);
            assertTrue(Files.isDirectory(dir));
        }

        @Test
        void createsNestedDirectories() throws IOException {
            Path dir = tempDir.resolve("a/b/c");
            ops.mkdir(dir);
            assertTrue(Files.isDirectory(dir));
        }

        @Test
        void existingDirectoryIsNoOp() throws IOException {
            Path dir = tempDir.resolve("existing");
            Files.createDirectory(dir);
            assertDoesNotThrow(() -> ops.mkdir(dir));
            assertTrue(Files.isDirectory(dir));
        }
    }
}
