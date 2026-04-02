package com.huawei.hicampus.mate.matecampusclaw.cron.model;

import java.util.UUID;

import org.springframework.lang.Nullable;

/**
 * A scheduled cron job definition with its runtime state.
 */
public record CronJob(
    String id,
    String name,
    @Nullable String description,
    boolean enabled,
    boolean deleteAfterRun,
    CronSchedule schedule,
    CronPayload payload,
    CronJobState state,
    long createdAtMs
) {

    public static CronJob create(String name, @Nullable String description,
                                  CronSchedule schedule, CronPayload payload) {
        return new CronJob(
            UUID.randomUUID().toString(),
            name,
            description,
            true,
            false,
            schedule,
            payload,
            CronJobState.initial(),
            System.currentTimeMillis()
        );
    }

    public CronJob withState(CronJobState newState) {
        return new CronJob(id, name, description, enabled, deleteAfterRun,
            schedule, payload, newState, createdAtMs);
    }

    public CronJob withEnabled(boolean newEnabled) {
        return new CronJob(id, name, description, newEnabled, deleteAfterRun,
            schedule, payload, state, createdAtMs);
    }
}
