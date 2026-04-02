package com.huawei.hicampus.mate.matecampusclaw.codingagent.tool.ops;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class EditOperationsTest {

    @TempDir
    Path tempDir;

    /**
     * Verifies that a class implementing EditOperations gets both read and write capabilities.
     */
    @Test
    void editOperationsCombinesReadAndWrite() throws IOException {
        EditOperations ops = new LocalEditOperations();

        // Write a file
        Path file = tempDir.resolve("edit-test.txt");
        ops.writeFile(file, "edited content");

        // Read it back
        assertTrue(ops.exists(file));
        byte[] content = ops.readFile(file);
        assertEquals("edited content", new String(content, StandardCharsets.UTF_8));
    }

    /**
     * Simple local implementation combining both local read and write operations.
     */
    private static class LocalEditOperations implements EditOperations {
        private final LocalReadOperations read = new LocalReadOperations();
        private final LocalWriteOperations write = new LocalWriteOperations();

        @Override
        public byte[] readFile(Path path) throws IOException {
            return read.readFile(path);
        }

        @Override
        public boolean exists(Path path) {
            return read.exists(path);
        }

        @Override
        public String detectMimeType(Path path) throws IOException {
            return read.detectMimeType(path);
        }

        @Override
        public void writeFile(Path path, String content) throws IOException {
            write.writeFile(path, content);
        }

        @Override
        public void mkdir(Path dir) throws IOException {
            write.mkdir(dir);
        }
    }
}
