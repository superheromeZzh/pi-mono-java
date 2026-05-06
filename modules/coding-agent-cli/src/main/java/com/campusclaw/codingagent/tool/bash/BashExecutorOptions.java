/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.campusclaw.codingagent.tool.bash;

import java.time.Duration;
import java.util.Map;

import com.campusclaw.agent.tool.CancellationToken;

/**
 * Options for {@link BashExecutor} command execution.
 *
 * @param timeout maximum execution duration (may be null for no timeout)
 * @param signal  cancellation token for cooperative cancellation (may be null)
 * @param env     additional environment variables (may be null or empty)
 *
 * @version [br_eCampusCore 25.1.0_Next, 2026/05/06]
 * @since [br_eCampusCore 25.1.0_Next]
 */
public record BashExecutorOptions(Duration timeout, CancellationToken signal, Map<String, String> env) {
    @SuppressWarnings("checkstyle:java_doc_format_missing")
    public BashExecutorOptions {
        env = env == null ? Map.of() : Map.copyOf(env);
    }
}
