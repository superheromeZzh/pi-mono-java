/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.hicampus.mate.matecampusclaw.cron.model;

import org.springframework.lang.Nullable;

/**
 * Record of a single cron job execution.
 *
 * @version [br_eCampusCore 25.1.0_Next, 2026/05/06]
 * @since [br_eCampusCore 25.1.0_Next]
 */
public record CronRunRecord(
        String runId,
        String jobId,
        long startedAtMs,
        long finishedAtMs,
        RunStatus status,
        @Nullable String error,
        @Nullable String output,
        int turnCount) {

    @SuppressWarnings("checkstyle:top_class_comment")
    public enum RunStatus {
        RUNNING,
        SUCCESS,
        FAILED,
        CANCELLED
    }
}
