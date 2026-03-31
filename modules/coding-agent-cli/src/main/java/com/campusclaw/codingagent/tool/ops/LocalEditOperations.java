package com.campusclaw.codingagent.tool.ops;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Local filesystem implementation of {@link EditOperations},
 * combining read and write capabilities.
 */
public class LocalEditOperations implements EditOperations {

    @Override
    public byte[] readFile(Path path) throws IOException {
        return Files.readAllBytes(path);
    }

    @Override
    public boolean exists(Path path) {
        return Files.exists(path);
    }

    @Override
    public String detectMimeType(Path path) throws IOException {
        return Files.probeContentType(path);
    }

    @Override
    public void writeFile(Path path, String content) throws IOException {
        Path parent = path.getParent();
        if (parent != null && !Files.exists(parent)) {
            Files.createDirectories(parent);
        }
        Files.writeString(path, content, StandardCharsets.UTF_8);
    }

    @Override
    public void mkdir(Path dir) throws IOException {
        Files.createDirectories(dir);
    }
}
