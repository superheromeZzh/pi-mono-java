/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.campusclaw.codingagent.tool.ops;

import java.io.IOException;
import java.nio.file.Path;

/**
 * Abstraction for file read operations.
 * Implementations may target local filesystem, SSH, or RPC backends.
 *
 * @version [br_eCampusCore 25.1.0_Next, 2026/05/06]
 * @since [br_eCampusCore 25.1.0_Next]
 */
public interface ReadOperations {

    @SuppressWarnings("checkstyle:java_doc_format_missing")
    byte[] readFile(Path path) throws IOException;

    @SuppressWarnings("checkstyle:java_doc_format_missing")
    boolean exists(Path path);

    @SuppressWarnings("checkstyle:java_doc_format_missing")
    String detectMimeType(Path path) throws IOException;
}
