package com.huawei.hicampus.mate.matecampusclaw.codingagent.tool.bash;

import java.time.Duration;
import java.util.Map;

import com.huawei.hicampus.mate.matecampusclaw.agent.tool.CancellationToken;

/**
 * Options for {@link BashExecutor} command execution.
 *
 * @param timeout maximum execution duration (may be null for no timeout)
 * @param signal  cancellation token for cooperative cancellation (may be null)
 * @param env     additional environment variables (may be null or empty)
 */
public record BashExecutorOptions(
        Duration timeout,
        CancellationToken signal,
        Map<String, String> env
) {
    public BashExecutorOptions {
        env = env == null ? Map.of() : Map.copyOf(env);
    }
}
