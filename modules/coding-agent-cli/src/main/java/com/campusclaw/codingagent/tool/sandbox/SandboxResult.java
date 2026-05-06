/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.campusclaw.codingagent.tool.sandbox;

import lombok.Builder;
import lombok.Data;

/**
 * 沙箱执行结果
 *
 * @version [br_eCampusCore 25.1.0_Next, 2026/05/06]
 * @since [br_eCampusCore 25.1.0_Next]
 */
@Data
@Builder
public class SandboxResult {
    private String stdout;
    private String stderr;
    private Integer exitCode;
    private boolean timeout;
    private String errorMessage;
    private long executionTimeMs;

    public boolean isSuccess() {
        return exitCode != null && exitCode == 0 && errorMessage == null && !timeout;
    }

    @SuppressWarnings("checkstyle:java_doc_format_missing")
    public static SandboxResult error(String message, String stderr) {
        return SandboxResult.builder()
                .errorMessage(message)
                .stderr(stderr)
                .exitCode(-1)
                .build();
    }

    @SuppressWarnings("checkstyle:java_doc_format_missing")
    public static SandboxResult timeout(int timeoutSeconds) {
        return SandboxResult.builder()
                .timeout(true)
                .errorMessage("Execution timed out after " + timeoutSeconds + " seconds")
                .exitCode(-1)
                .build();
    }

    @SuppressWarnings("checkstyle:java_doc_format_missing")
    public static SandboxResult success(String stdout) {
        return SandboxResult.builder().stdout(stdout).exitCode(0).build();
    }
}
