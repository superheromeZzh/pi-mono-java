/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.campusclaw.codingagent.tool.ops;

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
