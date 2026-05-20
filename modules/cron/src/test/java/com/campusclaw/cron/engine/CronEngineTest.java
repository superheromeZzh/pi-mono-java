/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.campusclaw.cron.engine;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import com.campusclaw.cron.model.CronEvent;
import com.campusclaw.cron.model.CronJob;
import com.campusclaw.cron.model.CronJobState;
import com.campusclaw.cron.model.CronPayload;
import com.campusclaw.cron.model.CronRunRecord;
import com.campusclaw.cron.model.CronRunRecord.RunStatus;
import com.campusclaw.cron.model.CronSchedule;
import com.campusclaw.cron.store.CronStore;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

/**
 * Unit tests for {@link CronEngine}. Mocks store/executor and verifies the
 * engine's scheduling, tick processing, error backoff, listener notification,
 * and consecutive-error auto-disable behavior.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CronEngineTest {

    @Mock
    CronStore store;

    @Mock
    CronJobExecutor executor;

    private CronEngine engine;

    private static CronJob job(String id, boolean enabled, CronSchedule schedule, CronJobState state) {
        return new CronJob(
                id,
                "job-" + id,
                null,
                enabled,
                false,
                schedule,
                new CronPayload.AgentPrompt("p", null, null, null),
                state,
                0L);
    }

    private static CronJob job(String id, boolean enabled, CronSchedule schedule) {
        return job(id, enabled, schedule, CronJobState.initial());
    }

    @AfterEach
    void cleanup() {
        if (engine != null && engine.isRunning()) {
            engine.stop();
        }
    }

    @Nested
    class Lifecycle {

        @Test
        void startAndStopFlipsRunningFlag() {
            when(store.load()).thenReturn(List.of());
            engine = new CronEngine(store, executor);
            assertThat(engine.isRunning()).isFalse();
            engine.start();
            assertThat(engine.isRunning()).isTrue();
            engine.stop();
            assertThat(engine.isRunning()).isFalse();
        }

        @Test
        void doubleStartIsIdempotent() {
            when(store.load()).thenReturn(List.of());
            engine = new CronEngine(store, executor);
            engine.start();
            engine.start();
            assertThat(engine.isRunning()).isTrue();
        }

        @Test
        void doubleStopIsIdempotent() {
            engine = new CronEngine(store, executor);
            engine.stop();
            assertThat(engine.isRunning()).isFalse();
        }

        @Test
        void startSchedulesEnabledJobs() {
            CronJob enabled = job("j1", true, new CronSchedule.Every(60_000L));
            CronJob disabled = job("j2", false, new CronSchedule.Every(60_000L));
            when(store.load()).thenReturn(List.of(enabled, disabled));
            engine = new CronEngine(store, executor);
            engine.start();

            // Disabled job is not scheduled — store.updateJob should only be called for enabled
            // (scheduleJob writes next run time to state)
            verify(store, atLeastOnce()).updateJob(any(CronJob.class));
        }
    }

    @Nested
    class Listeners {

        @Test
        void emitsJobStartedAndCompleted() {
            engine = new CronEngine(store, executor);
            AtomicInteger started = new AtomicInteger();
            AtomicInteger completed = new AtomicInteger();
            engine.addListener(e -> {
                if (e instanceof CronEvent.JobStarted) {
                    started.incrementAndGet();
                } else if (e instanceof CronEvent.JobCompleted) {
                    completed.incrementAndGet();
                }
            });

            CronJob j = job("j1", true, new CronSchedule.Every(60_000L));
            when(store.getJob("j1")).thenReturn(Optional.of(j));
            when(executor.execute(j))
                    .thenReturn(new CronRunRecord("r1", "j1", 0L, 100L, RunStatus.SUCCESS, null, "done", 1));

            engine.triggerJob("j1");
            assertThat(started.get()).isEqualTo(1);
            assertThat(completed.get()).isEqualTo(1);
        }

        @Test
        void emitsJobFailedOnFailure() {
            engine = new CronEngine(store, executor);
            AtomicInteger failed = new AtomicInteger();
            engine.addListener(e -> {
                if (e instanceof CronEvent.JobFailed) {
                    failed.incrementAndGet();
                }
            });

            CronJob j = job("j1", true, new CronSchedule.Every(60_000L));
            when(store.getJob("j1")).thenReturn(Optional.of(j));
            when(executor.execute(j))
                    .thenReturn(new CronRunRecord("r1", "j1", 0L, 100L, RunStatus.FAILED, "boom", null, 0));

            engine.triggerJob("j1");
            assertThat(failed.get()).isEqualTo(1);
        }

        @Test
        void emitsJobFailedOnExceptionFromExecutor() {
            engine = new CronEngine(store, executor);
            AtomicInteger failed = new AtomicInteger();
            engine.addListener(e -> {
                if (e instanceof CronEvent.JobFailed) {
                    failed.incrementAndGet();
                }
            });

            CronJob j = job("j1", true, new CronSchedule.Every(60_000L));
            when(store.getJob("j1")).thenReturn(Optional.of(j));
            when(executor.execute(j)).thenThrow(new RuntimeException("oops"));

            CronRunRecord record = engine.triggerJob("j1");
            assertThat(failed.get()).isEqualTo(1);
            assertThat(record.status()).isEqualTo(RunStatus.FAILED);
            assertThat(record.error()).isEqualTo("oops");
        }

        @Test
        void removeListenerStopsNotifications() {
            engine = new CronEngine(store, executor);
            AtomicInteger count = new AtomicInteger();
            com.campusclaw.cron.engine.CronEventListener listener = e -> count.incrementAndGet();
            engine.addListener(listener);
            engine.removeListener(listener);

            CronJob j = job("j1", true, new CronSchedule.Every(60_000L));
            when(store.getJob("j1")).thenReturn(Optional.of(j));
            when(executor.execute(j))
                    .thenReturn(new CronRunRecord("r1", "j1", 0L, 100L, RunStatus.SUCCESS, null, null, 0));
            engine.triggerJob("j1");
            assertThat(count.get()).isZero();
        }

        @Test
        void listenerExceptionDoesNotInterrupt() {
            engine = new CronEngine(store, executor);
            AtomicInteger ok = new AtomicInteger();
            engine.addListener(e -> {
                throw new IllegalStateException("bad listener");
            });
            engine.addListener(e -> ok.incrementAndGet());

            CronJob j = job("j1", true, new CronSchedule.Every(60_000L));
            when(store.getJob("j1")).thenReturn(Optional.of(j));
            when(executor.execute(j))
                    .thenReturn(new CronRunRecord("r1", "j1", 0L, 100L, RunStatus.SUCCESS, null, null, 0));
            engine.triggerJob("j1");

            // Second listener still ran despite first throwing
            assertThat(ok.get()).isGreaterThanOrEqualTo(1);
        }
    }

    @Nested
    class TriggerJob {

        @Test
        void unknownJobThrows() {
            engine = new CronEngine(store, executor);
            when(store.getJob("ghost")).thenReturn(Optional.empty());
            assertThatThrownBy(() -> engine.triggerJob("ghost"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Job not found");
        }

        @Test
        void successUpdatesStateAndResetsErrors() {
            engine = new CronEngine(store, executor);
            CronJob j = job("j1", true, new CronSchedule.Every(60_000L), new CronJobState(0, 0, 0, "failed", 2, 5));
            when(store.getJob("j1")).thenReturn(Optional.of(j));
            when(executor.execute(j))
                    .thenReturn(new CronRunRecord("r1", "j1", 0L, 100L, RunStatus.SUCCESS, null, "done", 1));

            engine.triggerJob("j1");

            ArgumentCaptor<CronJob> captor = ArgumentCaptor.forClass(CronJob.class);
            verify(store, atLeast(2)).updateJob(captor.capture());
            CronJob lastWritten = captor.getValue();
            assertThat(lastWritten.state().consecutiveErrors()).isZero();
            assertThat(lastWritten.state().totalRuns()).isEqualTo(6);
            assertThat(lastWritten.state().lastRunStatus()).isEqualTo("success");
        }

        @Test
        void failureIncrementsConsecutiveErrors() {
            engine = new CronEngine(store, executor);
            CronJob j = job("j1", true, new CronSchedule.Every(60_000L), new CronJobState(0, 0, 0, null, 0, 0));
            when(store.getJob("j1")).thenReturn(Optional.of(j));
            when(executor.execute(j))
                    .thenReturn(new CronRunRecord("r1", "j1", 0L, 100L, RunStatus.FAILED, "boom", null, 0));

            engine.triggerJob("j1");

            ArgumentCaptor<CronJob> captor = ArgumentCaptor.forClass(CronJob.class);
            verify(store, atLeast(2)).updateJob(captor.capture());
            CronJob lastWritten = captor.getValue();
            assertThat(lastWritten.state().consecutiveErrors()).isEqualTo(1);
            assertThat(lastWritten.enabled()).isTrue();
        }

        @Test
        void threeConsecutiveErrorsAutoDisables() {
            engine = new CronEngine(store, executor);
            CronJob j = job("j1", true, new CronSchedule.Every(60_000L), new CronJobState(0, 0, 0, "failed", 2, 5));
            when(store.getJob("j1")).thenReturn(Optional.of(j));
            when(executor.execute(j))
                    .thenReturn(new CronRunRecord("r1", "j1", 0L, 100L, RunStatus.FAILED, "boom", null, 0));

            engine.triggerJob("j1");

            ArgumentCaptor<CronJob> captor = ArgumentCaptor.forClass(CronJob.class);
            verify(store, atLeast(2)).updateJob(captor.capture());
            CronJob lastWritten = captor.getValue();
            assertThat(lastWritten.enabled()).isFalse();
            assertThat(lastWritten.state().consecutiveErrors()).isEqualTo(3);
        }
    }

    @Nested
    class TickOnce {

        @Test
        void emptyStoreReturnsEmptyList() {
            when(store.load()).thenReturn(List.of());
            engine = new CronEngine(store, executor);
            assertThat(engine.tickOnce()).isEmpty();
        }

        @Test
        void disabledJobsSkipped() {
            CronJob j = job("j1", false, new CronSchedule.Every(60_000L));
            when(store.load()).thenReturn(List.of(j));
            engine = new CronEngine(store, executor);
            assertThat(engine.tickOnce()).isEmpty();
            verify(executor, never()).execute(any(CronJob.class));
        }

        @Test
        void runningJobSkipped() {
            CronJob j = job(
                    "j1",
                    true,
                    new CronSchedule.Every(60_000L),
                    new CronJobState(0, System.currentTimeMillis(), 0, null, 0, 0));
            when(store.load()).thenReturn(List.of(j));
            engine = new CronEngine(store, executor);
            assertThat(engine.tickOnce()).isEmpty();
            verify(executor, never()).execute(any(CronJob.class));
        }

        @Test
        void pastDueAtScheduleSkipped() {
            // At schedule with past timestamp returns -1 delay → skipped
            CronJob j = job("j1", true, new CronSchedule.At(0L));
            when(store.load()).thenReturn(List.of(j));
            engine = new CronEngine(store, executor);
            assertThat(engine.tickOnce()).isEmpty();
        }

        @Test
        void dueWithinToleranceExecutes() {
            // Every schedule defaults to lastRun=0 + interval = 60s past 1970 → due now
            CronJob j = job("j1", true, new CronSchedule.Every(60_000L));
            when(store.load()).thenReturn(List.of(j));
            when(executor.execute(j))
                    .thenReturn(new CronRunRecord("r1", "j1", 0L, 100L, RunStatus.SUCCESS, null, "ok", 1));
            engine = new CronEngine(store, executor);
            List<CronRunRecord> results = engine.tickOnce();
            assertThat(results).hasSize(1);
        }
    }

    @Nested
    class ComputeNextDelay {

        @Test
        void atScheduleInFutureReturnsPositive() {
            engine = new CronEngine(store, executor);
            long future = System.currentTimeMillis() + 60_000L;
            CronJob j = job("j1", true, new CronSchedule.At(future));
            long delay = engine.computeNextDelay(j);
            assertThat(delay).isPositive();
        }

        @Test
        void atScheduleInPastReturnsMinusOne() {
            engine = new CronEngine(store, executor);
            CronJob j = job("j1", true, new CronSchedule.At(0L));
            assertThat(engine.computeNextDelay(j)).isEqualTo(-1L);
        }

        @Test
        void everyScheduleHonorsBaseTime() {
            engine = new CronEngine(store, executor);
            long lastRun = System.currentTimeMillis();
            CronJob j =
                    job("j1", true, new CronSchedule.Every(60_000L), new CronJobState(0, 0, lastRun, "success", 0, 1));
            long delay = engine.computeNextDelay(j);

            // next run is lastRun + interval; with interval 60s and now == lastRun, delay ≈ 60_000
            assertThat(delay).isGreaterThan(50_000L);
        }

        @Test
        void everyWithConsecutiveErrorsAppliesBackoff() {
            engine = new CronEngine(store, executor);
            CronJob j = job("j1", true, new CronSchedule.Every(1000L), new CronJobState(0, 0, 0, "failed", 3, 5));
            long delay = engine.computeNextDelay(j);

            // 3 errors → 1000 * 2^3 = 8s backoff at minimum
            assertThat(delay).isGreaterThanOrEqualTo(7_000L);
        }

        @Test
        void cronExpressionValid() {
            engine = new CronEngine(store, executor);
            CronJob j = job("j1", true, new CronSchedule.CronExpr("0 0 * * * *", null));
            long delay = engine.computeNextDelay(j);

            // delay to next hour boundary — at most 1 hour
            assertThat(delay).isBetween(0L, 3_600_000L);
        }

        @Test
        void cronExpressionInvalidReturnsMinusOne() {
            engine = new CronEngine(store, executor);
            CronJob j = job("j1", true, new CronSchedule.CronExpr("not a cron", null));
            assertThat(engine.computeNextDelay(j)).isEqualTo(-1L);
        }
    }

    @Nested
    class ScheduleJob {

        @Test
        void notRunningIsNoOp() {
            engine = new CronEngine(store, executor);
            CronJob j = job("j1", true, new CronSchedule.Every(60_000L));
            engine.scheduleJob(j);
            verify(store, never()).updateJob(any(CronJob.class));
        }

        @Test
        void disabledJobNotScheduled() {
            when(store.load()).thenReturn(List.of());
            engine = new CronEngine(store, executor);
            engine.start();
            CronJob j = job("j1", false, new CronSchedule.Every(60_000L));
            engine.scheduleJob(j);

            // No state update should occur for disabled job
            verify(store, never()).updateJob(j);
        }

        @Test
        void unscheduleMissingJobIsNoOp() {
            engine = new CronEngine(store, executor);
            engine.unscheduleJob("nonexistent");

            // Should not throw
        }
    }
}
