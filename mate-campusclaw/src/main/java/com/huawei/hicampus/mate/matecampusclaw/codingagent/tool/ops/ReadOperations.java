package com.huawei.hicampus.mate.matecampusclaw.codingagent.tool.ops;

import java.io.IOException;
import java.nio.file.Path;

/**
 * Abstraction for file read operations.
 * Implementations may target local filesystem, SSH, or RPC backends.
 */
public interface ReadOperations {

    byte[] readFile(Path path) throws IOException;

    boolean exists(Path path);

    String detectMimeType(Path path) throws IOException;
}
