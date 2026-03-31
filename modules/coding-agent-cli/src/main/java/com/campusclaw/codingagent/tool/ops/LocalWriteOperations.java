package com.campusclaw.codingagent.tool.ops;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Local filesystem implementation of {@link WriteOperations}.
 */
public class LocalWriteOperations implements WriteOperations {

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
