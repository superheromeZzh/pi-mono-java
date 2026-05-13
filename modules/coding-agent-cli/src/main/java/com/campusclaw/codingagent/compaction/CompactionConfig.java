/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.campusclaw.codingagent.compaction;

/**
 * Tuning parameters for context compaction: the master toggle, the reserve-token budget kept
 * free for the next model response, and the recent-token window retained verbatim around the
 * compaction summary.
 *
 * @version [br_eCampusCore 25.1.0_Next, 2026/05/13]
 * @since [br_eCampusCore 25.1.0_Next]
 */
public record CompactionConfig(boolean enabled, int reserveTokens, int keepRecentTokens) {
    public static CompactionConfig defaults() {
        return new CompactionConfig(true, 16384, 20000);
    }
}
