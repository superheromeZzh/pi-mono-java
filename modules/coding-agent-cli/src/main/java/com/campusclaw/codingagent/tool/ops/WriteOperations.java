/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.campusclaw.codingagent.tool.ops;

import java.io.IOException;
import java.nio.file.Path;

/**
 * Abstraction for file write operations.
 * Implementations may target local filesystem, SSH, or RPC backends.
 */
public interface WriteOperations {

    void writeFile(Path path, String content) throws IOException;

    void mkdir(Path dir) throws IOException;
}
