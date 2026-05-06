/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.hicampus.mate.matecampusclaw.codingagent.tool.ops;

import java.io.IOException;
import java.nio.file.Path;

/**
 * Abstraction for shell command execution.
 * Implementations may target local shell, SSH, or remote execution backends.
 *
 * @version [br_eCampusCore 25.1.0_Next, 2026/05/06]
 * @since [br_eCampusCore 25.1.0_Next]
 */
public interface BashOperations {

    BashResult exec(String command, Path cwd, BashExecOptions options) throws IOException;
}
