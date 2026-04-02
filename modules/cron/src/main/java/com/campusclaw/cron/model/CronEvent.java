package com.campusclaw.cron.model;

import org.springframework.lang.Nullable;

/**
 * Events emitted by the cron engine during job lifecycle.
 */
public sealed interface CronEvent {

    record JobStarted(String jobId, String jobName, String runId) implements CronEvent {}

    record JobCompleted(String jobId, String jobName, String runId, @Nullable String output) implements CronEvent {}

    record JobFailed(String jobId, String jobName, String runId, String error) implements CronEvent {}
}
