package com.huawei.hicampus.mate.matecampusclaw.cron.model;

import org.springframework.lang.Nullable;

/**
 * Record of a single cron job execution.
 */
public record CronRunRecord(
    String runId,
    String jobId,
    long startedAtMs,
    long finishedAtMs,
    RunStatus status,
    @Nullable String error,
    @Nullable String output,
    int turnCount
) {

    public enum RunStatus {
        RUNNING, SUCCESS, FAILED, CANCELLED
    }
}
