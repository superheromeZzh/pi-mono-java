package com.huawei.hicampus.mate.matecampusclaw.cron;

import java.util.List;
import java.util.Optional;

import com.huawei.hicampus.mate.matecampusclaw.cron.engine.CronEngine;
import com.huawei.hicampus.mate.matecampusclaw.cron.engine.CronEventListener;
import com.huawei.hicampus.mate.matecampusclaw.cron.model.CronJob;
import com.huawei.hicampus.mate.matecampusclaw.cron.model.CronPayload;
import com.huawei.hicampus.mate.matecampusclaw.cron.model.CronRunRecord;
import com.huawei.hicampus.mate.matecampusclaw.cron.model.CronSchedule;
import com.huawei.hicampus.mate.matecampusclaw.cron.store.CronRunLog;
import com.huawei.hicampus.mate.matecampusclaw.cron.store.CronStore;

import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;

/**
 * Facade service for cron job management. Coordinates store, engine, and run log.
 */
@Service
public class CronService {

    private final CronStore store;
    private final CronEngine engine;
    private final CronRunLog runLog;
    private volatile String defaultModelId;

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

    public void start() {
        engine.start();
    }

    public void stop() {
        engine.stop();
    }

    public boolean isRunning() {
        return engine.isRunning();
    }

    public void addListener(CronEventListener listener) {
        engine.addListener(listener);
    }

    public CronJob createJob(String name, @Nullable String description,
                              CronSchedule schedule, CronPayload payload) {
        var job = CronJob.create(name, description, schedule, payload);
        store.addJob(job);
        if (engine.isRunning()) {
            engine.scheduleJob(job);
        }
        return job;
    }

    public boolean deleteJob(String jobId) {
        engine.unscheduleJob(jobId);
        return store.removeJob(jobId);
    }

    public List<CronJob> listJobs() {
        return store.load();
    }

    public Optional<CronJob> getJob(String jobId) {
        return store.getJob(jobId);
    }

    public void enableJob(String jobId) {
        store.getJob(jobId).ifPresent(job -> {
            var enabled = job.withEnabled(true);
            store.updateJob(enabled);
            if (engine.isRunning()) {
                engine.scheduleJob(enabled);
            }
        });
    }

    public void disableJob(String jobId) {
        store.getJob(jobId).ifPresent(job -> {
            engine.unscheduleJob(jobId);
            store.updateJob(job.withEnabled(false));
        });
    }

    public CronRunRecord triggerJob(String jobId) {
        return engine.triggerJob(jobId);
    }

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
