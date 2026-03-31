package com.campusclaw.codingagent.tool.ops;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;

/**
 * Abstraction for directory listing operations.
 * Implementations may target local filesystem, SSH, or RPC backends.
 */
public interface LsOperations {

    List<LsEntry> list(Path directory) throws IOException;

    record LsEntry(String name, String type, long size, Instant lastModified) {}
}
