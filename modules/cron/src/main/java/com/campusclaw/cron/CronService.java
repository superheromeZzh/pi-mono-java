/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.campusclaw.cron;

import java.util.List;
import java.util.Optional;

import com.campusclaw.cron.engine.CronEngine;
import com.campusclaw.cron.engine.CronEventListener;
import com.campusclaw.cron.model.CronJob;
import com.campusclaw.cron.model.CronPayload;
import com.campusclaw.cron.model.CronRunRecord;
import com.campusclaw.cron.model.CronSchedule;
import com.campusclaw.cron.store.CronRunLog;
import com.campusclaw.cron.store.CronStore;

import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;

/**
 * Facade service for cron job management. Coordinates store, engine, and run log.
 *
 * @version [br_eCampusCore 25.1.0_Next, 2026/05/06]
 * @since [br_eCampusCore 25.1.0_Next]
 */
@Service
public class CronService {

    private final CronStore store;
    private final CronEngine engine;
    private final CronRunLog runLog;
    private volatile String defaultModelId;

    @SuppressWarnings("checkstyle:java_doc_format_missing")
    public CronService(CronStore store, CronEngine engine, CronRunLog runLog) {
        this.store = store;
        this.engine = engine;
        this.runLog = runLog;
    }

    /**
     * Set the default model ID for cron jobs that don't specify one.
     * Should be called on startup with the user's chosen model.
     */
    public void setDefaultModelId(String modelId) {
        this.defaultModelId = modelId;
    }

    public String getDefaultModelId() {
        return defaultModelId;
    }

    @SuppressWarnings("checkstyle:java_doc_format_missing")
    public void start() {
        engine.start();
    }

    @SuppressWarnings("checkstyle:java_doc_format_missing")
    public void stop() {
        engine.stop();
    }

    public boolean isRunning() {
        return engine.isRunning();
    }

    @SuppressWarnings("checkstyle:java_doc_format_missing")
    public void addListener(CronEventListener listener) {
        engine.addListener(listener);
    }

    @SuppressWarnings("checkstyle:java_doc_format_missing")
    public CronJob createJob(String name, @Nullable String description, CronSchedule schedule, CronPayload payload) {
        var job = CronJob.create(name, description, schedule, payload);
        store.addJob(job);
        if (engine.isRunning()) {
            engine.scheduleJob(job);
        }
        return job;
    }

    @SuppressWarnings("checkstyle:java_doc_format_missing")
    public boolean deleteJob(String jobId) {
        engine.unscheduleJob(jobId);
        return store.removeJob(jobId);
    }

    @SuppressWarnings("checkstyle:java_doc_format_missing")
    public List<CronJob> listJobs() {
        return store.load();
    }

    @SuppressWarnings("checkstyle:java_doc_format_missing")
    public Optional<CronJob> getJob(String jobId) {
        return store.getJob(jobId);
    }

    @SuppressWarnings("checkstyle:java_doc_format_missing")
    public void enableJob(String jobId) {
        store.getJob(jobId).ifPresent(job -> {
            var enabled = job.withEnabled(true);
            store.updateJob(enabled);
            if (engine.isRunning()) {
                engine.scheduleJob(enabled);
            }
        });
    }

    @SuppressWarnings("checkstyle:java_doc_format_missing")
    public void disableJob(String jobId) {
        store.getJob(jobId).ifPresent(job -> {
            engine.unscheduleJob(jobId);
            store.updateJob(job.withEnabled(false));
        });
    }

    @SuppressWarnings("checkstyle:java_doc_format_missing")
    public CronRunRecord triggerJob(String jobId) {
        return engine.triggerJob(jobId);
    }

    @SuppressWarnings("checkstyle:java_doc_format_missing")
    public List<CronRunRecord> getRecentRuns(String jobId, int limit) {
        return runLog.getRecentRuns(jobId, limit);
    }

    /**
     * Perform a single synchronous tick: execute all due jobs and return results.
     * Used by {@code --cron-tick} CLI mode for system scheduler integration.
     */
    public List<CronRunRecord> tickOnce() {
        return engine.tickOnce();
    }
}
