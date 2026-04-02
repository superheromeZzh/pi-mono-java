package com.huawei.hicampus.mate.matecampusclaw.cron.model;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

import org.springframework.lang.Nullable;

/**
 * Sealed schedule type for cron jobs.
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
@JsonSubTypes({
    @JsonSubTypes.Type(value = CronSchedule.At.class, name = "at"),
    @JsonSubTypes.Type(value = CronSchedule.Every.class, name = "every"),
    @JsonSubTypes.Type(value = CronSchedule.CronExpr.class, name = "cron")
})
public sealed interface CronSchedule {

    /** One-shot schedule at a specific timestamp. */
    record At(long timestampMs) implements CronSchedule {}

    /** Recurring schedule at a fixed interval. */
    record Every(long intervalMs) implements CronSchedule {}

    /** Recurring schedule using a Spring-compatible cron expression. */
    record CronExpr(String expression, @Nullable String timezone) implements CronSchedule {}
}
