package com.campusclaw.cron.engine;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.locks.ReentrantLock;

import com.campusclaw.cron.model.*;
import com.campusclaw.cron.store.CronStore;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.support.CronExpression;
import org.springframework.stereotype.Service;

/**
 * Scheduling engine for cron jobs. Manages tick-based scheduling, concurrent execution,
 * and job lifecycle. Must be explicitly started/stopped (not SmartLifecycle).
 */
@Service
public class CronEngine {

    private static final Logger log = LoggerFactory.getLogger(CronEngine.class);
    private static final long MAX_TICK_INTERVAL_MS = 60_000;
    private static final long STALE_THRESHOLD_MS = 2 * 60 * 60 * 1000; // 2 hours
    private static final int MAX_CONSECUTIVE_ERRORS = 3;

    private final CronStore store;
    private final CronJobExecutor executor;
    private final List<CronEventListener> listeners = new CopyOnWriteArrayList<>();
    private final Map<String, ScheduledFuture<?>> scheduledJobs = new ConcurrentHashMap<>();
    private final ReentrantLock tickLock = new ReentrantLock();

    private volatile ScheduledExecutorService scheduler;
    private volatile boolean running;

    public CronEngine(CronStore store, CronJobExecutor executor) {
        this.store = store;
        this.executor = executor;
    }

    public void start() {
        if (running) return;
        running = true;
        scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "cron-engine");
            t.setDaemon(true);
            return t;
        });

        // Clean stale running marks
        cleanStaleRunning();

        // Schedule all enabled jobs
        var jobs = store.load();
        for (var job : jobs) {
            if (job.enabled()) {
                scheduleJob(job);
            }
        }
        log.info("Cron engine started with {} jobs ({} enabled)",
            jobs.size(), jobs.stream().filter(CronJob::enabled).count());
    }

    public void stop() {
        if (!running) return;
        running = false;
        scheduledJobs.values().forEach(f -> f.cancel(false));
        scheduledJobs.clear();
        if (scheduler != null) {
            scheduler.shutdownNow();
            scheduler = null;
        }
        log.info("Cron engine stopped");
    }

    public boolean isRunning() {
        return running;
    }

    public void addListener(CronEventListener listener) {
        listeners.add(listener);
    }

    public void removeListener(CronEventListener listener) {
        listeners.remove(listener);
    }

    /** Schedule or reschedule a job. Called when a job is created/updated. */
    public void scheduleJob(CronJob job) {
        if (!running || scheduler == null) return;

        // Cancel existing schedule
        var existing = scheduledJobs.remove(job.id());
        if (existing != null) {
            existing.cancel(false);
        }

        if (!job.enabled()) return;

        long delayMs = computeNextDelay(job);
        if (delayMs < 0) {
            log.debug("Job {} has no next run time, skipping", job.name());
            return;
        }

        var future = scheduler.schedule(() -> executeAndReschedule(job.id()), delayMs, TimeUnit.MILLISECONDS);
        scheduledJobs.put(job.id(), future);

        // Update next run time in state
        long nextRunAtMs = System.currentTimeMillis() + delayMs;
        store.updateJob(job.withState(new CronJobState(
            nextRunAtMs, job.state().runningAtMs(),
            job.state().lastRunAtMs(), job.state().lastRunStatus(),
            job.state().consecutiveErrors(), job.state().totalRuns()
        )));

        log.debug("Scheduled job {} to run in {}ms", job.name(), delayMs);
    }

    /** Unschedule a job. Called when a job is deleted/disabled. */
    public void unscheduleJob(String jobId) {
        var future = scheduledJobs.remove(jobId);
        if (future != null) {
            future.cancel(false);
        }
    }

    /** Trigger immediate execution of a job. */
    public CronRunRecord triggerJob(String jobId) {
        var jobOpt = store.getJob(jobId);
        if (jobOpt.isEmpty()) {
            throw new IllegalArgumentException("Job not found: " + jobId);
        }
        return executeJob(jobOpt.get());
    }

    private void executeAndReschedule(String jobId) {
        if (!tickLock.tryLock()) {
            log.debug("Tick lock busy, skipping job {}", jobId);
            return;
        }
        try {
            var jobOpt = store.getJob(jobId);
            if (jobOpt.isEmpty() || !jobOpt.get().enabled()) return;

            var job = jobOpt.get();

            // Skip if already running
            if (job.state().runningAtMs() != 0) {
                log.debug("Job {} is already running, skipping", job.name());
                return;
            }

            executeJob(job);

            // Reschedule if still enabled and not deleteAfterRun
            var updatedJob = store.getJob(jobId);
            if (updatedJob.isPresent() && updatedJob.get().enabled()) {
                scheduleJob(updatedJob.get());
            }
        } finally {
            tickLock.unlock();
        }
    }

    private CronRunRecord executeJob(CronJob job) {
        // Mark as running
        store.updateJob(job.withState(new CronJobState(
            job.state().nextRunAtMs(), System.currentTimeMillis(),
            job.state().lastRunAtMs(), job.state().lastRunStatus(),
            job.state().consecutiveErrors(), job.state().totalRuns()
        )));

        String runId = null;
        emit(new CronEvent.JobStarted(job.id(), job.name(), ""));

        try {
            CronRunRecord result = executor.execute(job);
            runId = result.runId();

            // Update state based on result
            boolean success = result.status() == CronRunRecord.RunStatus.SUCCESS;
            int errors = success ? 0 : job.state().consecutiveErrors() + 1;
            boolean shouldDisable = errors >= MAX_CONSECUTIVE_ERRORS;

            var newState = new CronJobState(
                0, 0,
                System.currentTimeMillis(),
                success ? "success" : "failed",
                errors,
                job.state().totalRuns() + 1
            );

            var updatedJob = job.withState(newState);
            if (shouldDisable) {
                updatedJob = updatedJob.withEnabled(false);
                log.warn("Job {} auto-disabled after {} consecutive errors", job.name(), errors);
            }

            // Handle deleteAfterRun for one-shot At schedules
            if (job.deleteAfterRun() && success) {
                store.removeJob(job.id());
                unscheduleJob(job.id());
            } else {
                store.updateJob(updatedJob);
            }

            if (success) {
                emit(new CronEvent.JobCompleted(job.id(), job.name(), runId, result.output()));
            } else {
                emit(new CronEvent.JobFailed(job.id(), job.name(), runId,
                    result.error() != null ? result.error() : "Unknown error"));
            }

            return result;

        } catch (Exception e) {
            log.error("Unexpected error executing job {}", job.name(), e);

            int errors = job.state().consecutiveErrors() + 1;
            var newState = new CronJobState(
                0, 0,
                System.currentTimeMillis(), "failed",
                errors, job.state().totalRuns() + 1
            );
            var updatedJob = job.withState(newState);
            if (errors >= MAX_CONSECUTIVE_ERRORS) {
                updatedJob = updatedJob.withEnabled(false);
            }
            store.updateJob(updatedJob);

            emit(new CronEvent.JobFailed(job.id(), job.name(),
                runId != null ? runId : "", e.getMessage()));

            return new CronRunRecord(
                runId != null ? runId : "", job.id(),
                System.currentTimeMillis(), System.currentTimeMillis(),
                CronRunRecord.RunStatus.FAILED, e.getMessage(), null, 0
            );
        }
    }

    /**
     * Perform a single synchronous tick: check all enabled jobs, execute those that are due.
     * Designed for {@code --cron-tick} mode where an external scheduler (launchd/crontab) invokes the CLI.
     */
    public List<CronRunRecord> tickOnce() {
        cleanStaleRunning();
        var results = new ArrayList<CronRunRecord>();
        var jobs = store.load();
        for (var job : jobs) {
            if (!job.enabled()) continue;
            if (job.state().runningAtMs() != 0) continue; // skip if running
            long delay = computeNextDelay(job);
            if (delay < 0) continue; // past one-shot or invalid
            if (delay <= 60_000) { // due within 1 minute (tick tolerance)
                results.add(executeJob(job));
            }
        }
        return results;
    }

    long computeNextDelay(CronJob job) {
        long now = System.currentTimeMillis();
        return switch (job.schedule()) {
            case CronSchedule.At at -> {
                long delay = at.timestampMs() - now;
                yield delay > 0 ? delay : -1;
            }
            case CronSchedule.Every every -> {
                long base = Math.max(job.state().lastRunAtMs(), job.createdAtMs());
                long next = base + every.intervalMs();
                // Apply exponential backoff if there are consecutive errors
                if (job.state().consecutiveErrors() > 0) {
                    long backoff = Math.min(
                        1000L * (1L << job.state().consecutiveErrors()),
                        3_600_000L
                    );
                    next = Math.max(next, now + backoff);
                }
                yield Math.max(0, next - now);
            }
            case CronSchedule.CronExpr cron -> {
                try {
                    var expr = CronExpression.parse(cron.expression());
                    ZoneId zone = cron.timezone() != null
                        ? ZoneId.of(cron.timezone())
                        : ZoneId.systemDefault();
                    var next = expr.next(ZonedDateTime.now(zone));
                    if (next == null) {
                        yield -1L;
                    }
                    yield Math.max(0, next.toInstant().toEpochMilli() - now);
                } catch (Exception e) {
                    log.error("Invalid cron expression for job {}: {}", job.name(), cron.expression(), e);
                    yield -1L;
                }
            }
        };
    }

    private void cleanStaleRunning() {
        long now = System.currentTimeMillis();
        var jobs = new ArrayList<>(store.load());
        for (var job : jobs) {
            if (job.state().runningAtMs() != 0
                    && (now - job.state().runningAtMs()) > STALE_THRESHOLD_MS) {
                log.warn("Clearing stale running mark for job {} (running since {})",
                    job.name(), Instant.ofEpochMilli(job.state().runningAtMs()));
                store.updateJob(job.withState(new CronJobState(
                    job.state().nextRunAtMs(), 0,
                    job.state().lastRunAtMs(), "stale",
                    job.state().consecutiveErrors(), job.state().totalRuns()
                )));
            }
        }
    }

    private void emit(CronEvent event) {
        for (var listener : listeners) {
            try {
                listener.onCronEvent(event);
            } catch (Exception e) {
                log.debug("Cron event listener error", e);
            }
        }
    }
}
