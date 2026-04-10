package com.huawei.hicampus.mate.matecampusclaw.codingagent.tool.sandbox;

import lombok.Builder;
import lombok.Data;

/**
 * 沙箱执行结果
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

    public static SandboxResult error(String message, String stderr) {
        return SandboxResult.builder()
            .errorMessage(message)
            .stderr(stderr)
            .exitCode(-1)
            .build();
    }

    public static SandboxResult timeout(int timeoutSeconds) {
        return SandboxResult.builder()
            .timeout(true)
            .errorMessage("Execution timed out after " + timeoutSeconds + " seconds")
            .exitCode(-1)
            .build();
    }

    public static SandboxResult success(String stdout) {
        return SandboxResult.builder()
            .stdout(stdout)
            .exitCode(0)
            .build();
    }
}
