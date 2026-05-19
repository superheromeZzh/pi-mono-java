/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.hicampus.mate.matecampusclaw.codingagent.tool.ops;

import java.io.IOException;
import java.nio.file.Path;

/**
 * Abstraction for file write operations.
 * Implementations may target local filesystem, SSH, or RPC backends.
 *
 * @version [br_eCampusCore 25.1.0_Next, 2026/05/06]
 * @since [br_eCampusCore 25.1.0_Next]
 */
public interface WriteOperations {

    void writeFile(Path path, String content) throws IOException;

    void mkdir(Path dir) throws IOException;
}
