/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.campusclaw.cron;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import com.campusclaw.cron.engine.CronEngine;
import com.campusclaw.cron.engine.CronEventListener;
import com.campusclaw.cron.model.CronJob;
import com.campusclaw.cron.model.CronJobState;
import com.campusclaw.cron.model.CronPayload;
import com.campusclaw.cron.model.CronRunRecord;
import com.campusclaw.cron.model.CronRunRecord.RunStatus;
import com.campusclaw.cron.model.CronSchedule;
import com.campusclaw.cron.store.CronRunLog;
import com.campusclaw.cron.store.CronStore;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CronServiceTest {

    @Mock
    CronStore store;

    @Mock
    CronEngine engine;

    @Mock
    CronRunLog runLog;

    @InjectMocks
    CronService svc;

    private static CronJob job(String id, boolean enabled) {
        return new CronJob(
                id,
                "name",
                null,
                enabled,
                false,
                new CronSchedule.Every(60000),
                new CronPayload.AgentPrompt("p", null, null, null),
                CronJobState.initial(),
                0L);
    }

    @Nested
    class Lifecycle {

        @Test
        void defaultModelId() {
            svc.setDefaultModelId("claude-x");
            assertThat(svc.getDefaultModelId()).isEqualTo("claude-x");
        }

        @Test
        void startDelegates() {
            svc.start();
            verify(engine).start();
        }

        @Test
        void stopDelegates() {
            svc.stop();
            verify(engine).stop();
        }

        @Test
        void isRunningDelegates() {
            when(engine.isRunning()).thenReturn(true);
            assertThat(svc.isRunning()).isTrue();
        }
    }

    @Nested
    class JobManagement {

        @Test
        void createJobSchedulesWhenRunning() {
            when(engine.isRunning()).thenReturn(true);
            CronJob created = svc.createJob(
                    "n", "d", new CronSchedule.Every(60000), new CronPayload.AgentPrompt("p", null, null, null));
            verify(store).addJob(any(CronJob.class));
            verify(engine).scheduleJob(any(CronJob.class));
            assertThat(created.name()).isEqualTo("n");
        }

        @Test
        void createJobSkipsScheduleWhenStopped() {
            when(engine.isRunning()).thenReturn(false);
            svc.createJob("n", null, new CronSchedule.Every(60000), new CronPayload.AgentPrompt("p", null, null, null));
            verify(engine, never()).scheduleJob(any(CronJob.class));
        }

        @Test
        void deleteJobUnscheduledAndRemoved() {
            when(store.removeJob("j1")).thenReturn(true);
            assertThat(svc.deleteJob("j1")).isTrue();
            verify(engine).unscheduleJob("j1");
        }

        @Test
        void listAndGet() {
            CronJob j = job("j1", true);
            when(store.load()).thenReturn(List.of(j));
            when(store.getJob("j1")).thenReturn(Optional.of(j));
            assertThat(svc.listJobs()).hasSize(1);
            assertThat(svc.getJob("j1")).contains(j);
        }
    }

    @Nested
    class EnableDisable {

        @Test
        void enableJobSchedulesWhenRunning() {
            CronJob j = job("j1", false);
            when(store.getJob("j1")).thenReturn(Optional.of(j));
            when(engine.isRunning()).thenReturn(true);
            svc.enableJob("j1");
            ArgumentCaptor<CronJob> captor = ArgumentCaptor.forClass(CronJob.class);
            verify(store).updateJob(captor.capture());
            assertThat(captor.getValue().enabled()).isTrue();
            verify(engine).scheduleJob(any(CronJob.class));
        }

        @Test
        void enableJobMissingIsNoOp() {
            when(store.getJob("missing")).thenReturn(Optional.empty());
            svc.enableJob("missing");
            verify(store, never()).updateJob(any(CronJob.class));
        }

        @Test
        void disableJobUnschedulesAndUpdates() {
            CronJob j = job("j1", true);
            when(store.getJob("j1")).thenReturn(Optional.of(j));
            svc.disableJob("j1");
            verify(engine).unscheduleJob("j1");
            ArgumentCaptor<CronJob> captor = ArgumentCaptor.forClass(CronJob.class);
            verify(store).updateJob(captor.capture());
            assertThat(captor.getValue().enabled()).isFalse();
        }
    }

    @Nested
    class TriggerAndRuns {

        @Test
        void triggerDelegates() {
            CronRunRecord rec = new CronRunRecord("r1", "j1", 0, 0, RunStatus.SUCCESS, null, null, 0);
            when(engine.triggerJob("j1")).thenReturn(rec);
            assertThat(svc.triggerJob("j1")).isSameAs(rec);
        }

        @Test
        void getRecentRunsDelegates() {
            CronRunRecord rec = new CronRunRecord("r1", "j1", 0, 0, RunStatus.SUCCESS, null, null, 0);
            when(runLog.getRecentRuns("j1", 5)).thenReturn(List.of(rec));
            assertThat(svc.getRecentRuns("j1", 5)).containsExactly(rec);
        }

        @Test
        void tickOnceDelegates() {
            when(engine.tickOnce()).thenReturn(List.of());
            assertThat(svc.tickOnce()).isEmpty();
        }
    }

    @Nested
    class Listeners {

        @Test
        void addListenerDelegates() {
            CronEventListener listener = (CronEventListener) e -> {};
            svc.addListener(listener);
            verify(engine).addListener(listener);
        }
    }
}
