/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.hicampus.mate.matecampusclaw.cron.model;

import org.springframework.lang.Nullable;

/**
 * Events emitted by the cron engine during job lifecycle.
 *
 * @version [br_eCampusCore 25.1.0_Next, 2026/05/06]
 * @since [br_eCampusCore 25.1.0_Next]
 */
public sealed interface CronEvent {

    @SuppressWarnings("checkstyle:top_class_comment")
    record JobStarted(String jobId, String jobName, String runId) implements CronEvent {}

    @SuppressWarnings("checkstyle:top_class_comment")
    record JobCompleted(String jobId, String jobName, String runId, @Nullable String output) implements CronEvent {}

    @SuppressWarnings("checkstyle:top_class_comment")
    record JobFailed(String jobId, String jobName, String runId, String error) implements CronEvent {}
}
