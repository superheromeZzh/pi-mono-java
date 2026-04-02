package com.huawei.hicampus.mate.matecampusclaw.codingagent.tool.ops;

/**
 * Result of a bash command execution.
 *
 * @param exitCode the process exit code, or null if the process was killed/cancelled
 */
public record BashResult(Integer exitCode) {
}
