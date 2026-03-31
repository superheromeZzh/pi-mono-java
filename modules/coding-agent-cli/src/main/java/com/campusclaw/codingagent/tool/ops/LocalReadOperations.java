package com.campusclaw.codingagent.tool.ops;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Local filesystem implementation of {@link ReadOperations}.
 */
public class LocalReadOperations implements ReadOperations {

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
        String mimeType = Files.probeContentType(path);
        return mimeType != null ? mimeType : "application/octet-stream";
    }
}
