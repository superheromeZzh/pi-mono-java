package com.huawei.hicampus.mate.matecampusclaw.codingagent.tool.ops;

import java.io.IOException;
import java.nio.file.Path;

/**
 * Abstraction for shell command execution.
 * Implementations may target local shell, SSH, or remote execution backends.
 */
public interface BashOperations {

    BashResult exec(String command, Path cwd, BashExecOptions options) throws IOException;
}
