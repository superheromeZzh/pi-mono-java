package com.huawei.hicampus.campusclaw.codingagent.tool.ops;

import java.time.Duration;
import java.util.Map;
import java.util.function.Consumer;

import com.huawei.hicampus.campusclaw.agent.tool.CancellationToken;

/**
 * Options for bash command execution.
 *
 * @param onData  callback receiving incremental output data (may be null)
 * @param signal  cancellation token for cooperative cancellation (may be null)
 * @param timeout maximum execution duration (may be null for no timeout)
 * @param env     additional environment variables (may be null or empty)
 */
public record BashExecOptions(
        Consumer<byte[]> onData,
        CancellationToken signal,
        Duration timeout,
        Map<String, String> env
) {
    public BashExecOptions {
        env = env == null ? Map.of() : Map.copyOf(env);
    }
}
