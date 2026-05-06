/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.campusclaw.codingagent.tool.ops;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;

/**
 * Abstraction for directory listing operations.
 * Implementations may target local filesystem, SSH, or RPC backends.
 *
 * @version [br_eCampusCore 25.1.0_Next, 2026/05/06]
 * @since [br_eCampusCore 25.1.0_Next]
 */
public interface LsOperations {

    @SuppressWarnings("checkstyle:java_doc_format_missing")
    List<LsEntry> list(Path directory) throws IOException;

    @SuppressWarnings("checkstyle:top_class_comment")
    record LsEntry(String name, String type, long size, Instant lastModified) {}
}
