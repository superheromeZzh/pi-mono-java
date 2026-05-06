/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.campusclaw.codingagent.tool.bash;

/**
 * Result of a {@link BashExecutor} command execution.
 *
 * @param exitCode the process exit code, or null if the process was killed/timed out
 * @param stdout   captured standard output
 * @param stderr   captured standard error
 *
 * @version [br_eCampusCore 25.1.0_Next, 2026/05/06]
 * @since [br_eCampusCore 25.1.0_Next]
 */
public record BashExecutionResult(Integer exitCode, String stdout, String stderr) {}
