package com.huawei.hicampus.mate.matecampusclaw.codingagent.tool.bash;

/**
 * Result of a {@link BashExecutor} command execution.
 *
 * @param exitCode the process exit code, or null if the process was killed/timed out
 * @param stdout   captured standard output
 * @param stderr   captured standard error
 */
public record BashExecutionResult(
        Integer exitCode,
        String stdout,
        String stderr
) {
}
