/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.campusclaw.codingagent.tool.ops;

/**
 * Result of a bash command execution.
 *
 * @param exitCode the process exit code, or null if the process was killed/cancelled
 */
public record BashResult(Integer exitCode) {}
