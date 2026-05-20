/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.hicampus.mate.matecampusclaw.cron.tool;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.huawei.hicampus.mate.matecampusclaw.agent.tool.AgentToolResult;
import com.huawei.hicampus.mate.matecampusclaw.ai.types.TextContent;
import com.huawei.hicampus.mate.matecampusclaw.cron.CronService;
import com.huawei.hicampus.mate.matecampusclaw.cron.model.CronJob;
import com.huawei.hicampus.mate.matecampusclaw.cron.model.CronJobState;
import com.huawei.hicampus.mate.matecampusclaw.cron.model.CronPayload;
import com.huawei.hicampus.mate.matecampusclaw.cron.model.CronRunRecord;
import com.huawei.hicampus.mate.matecampusclaw.cron.model.CronRunRecord.RunStatus;
import com.huawei.hicampus.mate.matecampusclaw.cron.model.CronSchedule;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CronToolTest {

    @Mock
    CronService cronService;

    @InjectMocks
    CronTool tool;

    private static String text(AgentToolResult result) {
        TextContent tc = (TextContent) result.content().get(0);
        return tc.text();
    }

    private static CronJob job(String id, String name, CronSchedule sched, CronJobState state) {
        return new CronJob(
                id, name, null, true, false, sched, new CronPayload.AgentPrompt("p", null, null, null), state, 0L);
    }

    @Nested
    class Metadata {

        @Test
        void hasName() {
            assertThat(tool.name()).isEqualTo("cron");
        }

        @Test
        void hasLabel() {
            assertThat(tool.label()).isEqualTo("Cron");
        }

        @Test
        void hasDescription() {
            assertThat(tool.description()).isNotBlank();
        }

        @Test
        void parametersDescribeSchema() {
            var schema = tool.parameters();
            assertThat(schema.get("type").asText()).isEqualTo("object");
            assertThat(schema.get("required").get(0).asText()).isEqualTo("action");
            assertThat(schema.get("properties").has("action")).isTrue();
            assertThat(schema.get("properties").has("schedule_type")).isTrue();
        }
    }

    @Nested
    class ExecuteDispatch {

        @Test
        void missingActionReturnsError() {
            AgentToolResult result = tool.execute("id", Map.of(), null, null);
            assertThat(text(result)).contains("action is required");
        }

        @Test
        void unknownActionReturnsError() {
            AgentToolResult result = tool.execute("id", Map.of("action", "bogus"), null, null);
            assertThat(text(result)).contains("unknown action");
        }
    }

    @Nested
    class HandleCreate {

        @Test
        void missingNameRejected() {
            Map<String, Object> params = Map.of("action", "create");
            assertThat(text(tool.execute("id", params, null, null))).contains("name is required");
        }

        @Test
        void missingScheduleRejected() {
            Map<String, Object> params = Map.of("action", "create", "name", "n");
            assertThat(text(tool.execute("id", params, null, null))).contains("schedule_type");
        }

        @Test
        void missingPromptRejected() {
            Map<String, Object> params = Map.of(
                    "action", "create",
                    "name", "n",
                    "schedule_type", "every",
                    "schedule_value", "1000");
            assertThat(text(tool.execute("id", params, null, null))).contains("prompt");
        }

        @Test
        void invalidScheduleReportsError() {
            Map<String, Object> params = Map.of(
                    "action", "create",
                    "name", "n",
                    "schedule_type", "at",
                    "schedule_value", "not-a-timestamp",
                    "prompt", "do it");
            assertThat(text(tool.execute("id", params, null, null))).contains("Invalid 'at'");
        }

        @Test
        void everySucceedsAndDelegatesToService() {
            when(cronService.createJob(anyString(), any(), any(CronSchedule.class), any(CronPayload.class)))
                    .thenReturn(job("job-1", "n", new CronSchedule.Every(60000), CronJobState.initial()));
            Map<String, Object> params = new HashMap<>(Map.of(
                    "action", "create",
                    "name", "n",
                    "schedule_type", "every",
                    "schedule_value", "60000",
                    "prompt", "do it"));
            assertThat(text(tool.execute("id", params, null, null)))
                    .contains("Created cron job")
                    .contains("job-1");
        }

        @Test
        void atEpochSucceeds() {
            when(cronService.createJob(anyString(), any(), any(CronSchedule.class), any(CronPayload.class)))
                    .thenReturn(job("job-2", "n", new CronSchedule.At(1700_000_000_000L), CronJobState.initial()));
            Map<String, Object> params = new HashMap<>(Map.of(
                    "action", "create",
                    "name", "n",
                    "schedule_type", "at",
                    "schedule_value", "1700000000000",
                    "prompt", "do it"));
            assertThat(text(tool.execute("id", params, null, null))).contains("once at");
        }

        @Test
        void atIsoSucceeds() {
            when(cronService.createJob(anyString(), any(), any(CronSchedule.class), any(CronPayload.class)))
                    .thenReturn(job(
                            "job-3",
                            "n",
                            new CronSchedule.At(
                                    Instant.parse("2030-01-01T00:00:00Z").toEpochMilli()),
                            CronJobState.initial()));
            Map<String, Object> params = new HashMap<>(Map.of(
                    "action", "create",
                    "name", "n",
                    "schedule_type", "at",
                    "schedule_value", "2030-01-01T00:00:00Z",
                    "prompt", "do it"));
            assertThat(text(tool.execute("id", params, null, null))).contains("Created cron job");
        }

        @Test
        void cronSucceeds() {
            when(cronService.createJob(anyString(), any(), any(CronSchedule.class), any(CronPayload.class)))
                    .thenReturn(
                            job("job-4", "n", new CronSchedule.CronExpr("0 0 * * * *", "UTC"), CronJobState.initial()));
            Map<String, Object> params = new HashMap<>(Map.of(
                    "action", "create",
                    "name", "n",
                    "schedule_type", "cron",
                    "schedule_value", "0 0 * * * *",
                    "timezone", "UTC",
                    "prompt", "do it"));
            assertThat(text(tool.execute("id", params, null, null)))
                    .contains("cron:")
                    .contains("UTC");
        }

        @Test
        void cronInvalidExpressionRejected() {
            Map<String, Object> params = Map.of(
                    "action", "create",
                    "name", "n",
                    "schedule_type", "cron",
                    "schedule_value", "not a valid cron",
                    "prompt", "do it");
            assertThat(text(tool.execute("id", params, null, null))).contains("Invalid cron");
        }

        @Test
        void everyNegativeRejected() {
            Map<String, Object> params = Map.of(
                    "action", "create",
                    "name", "n",
                    "schedule_type", "every",
                    "schedule_value", "-5",
                    "prompt", "do it");
            assertThat(text(tool.execute("id", params, null, null))).contains("Interval must be positive");
        }

        @Test
        void everyNonNumericRejected() {
            Map<String, Object> params = Map.of(
                    "action", "create",
                    "name", "n",
                    "schedule_type", "every",
                    "schedule_value", "abc",
                    "prompt", "do it");
            assertThat(text(tool.execute("id", params, null, null))).contains("Invalid 'every'");
        }

        @Test
        void unknownScheduleTypeRejected() {
            Map<String, Object> params = Map.of(
                    "action", "create",
                    "name", "n",
                    "schedule_type", "weekly",
                    "schedule_value", "x",
                    "prompt", "do it");
            assertThat(text(tool.execute("id", params, null, null))).contains("Unknown schedule type");
        }
    }

    @Nested
    class HandleList {

        @Test
        void emptyListMessage() {
            when(cronService.listJobs()).thenReturn(List.of());
            assertThat(text(tool.execute("id", Map.of("action", "list"), null, null)))
                    .contains("No cron jobs");
        }

        @Test
        void jobsRenderWithSchedules() {
            CronJob a = job(
                    "j1",
                    "alpha",
                    new CronSchedule.Every(3600_000L * 2),
                    new CronJobState(0, 0, System.currentTimeMillis(), "success", 0, 5));
            CronJob b = job("j2", "beta", new CronSchedule.Every(120_000L), new CronJobState(0, 0, 0, null, 3, 0));
            CronJob c = job("j3", "gamma", new CronSchedule.Every(500L), new CronJobState(0, 0, 0, null, 0, 0));
            CronJob d = new CronJob(
                    "j4",
                    "delta",
                    null,
                    false,
                    false,
                    new CronSchedule.Every(20_000L),
                    new CronPayload.AgentPrompt("p", null, null, null),
                    new CronJobState(0, 0, 0, null, 0, 0),
                    0L);
            when(cronService.listJobs()).thenReturn(List.of(a, b, c, d));
            String out = text(tool.execute("id", Map.of("action", "list"), null, null));
            assertThat(out)
                    .contains("alpha")
                    .contains("beta")
                    .contains("gamma")
                    .contains("delta")
                    .contains("[ON]")
                    .contains("[OFF]")
                    .contains("every 2h")
                    .contains("every 2m")
                    .contains("every 20s")
                    .contains("every 500ms")
                    .contains("Last run")
                    .contains("Consecutive errors: 3");
        }
    }

    @Nested
    class HandleDelete {

        @Test
        void missingJobIdRejected() {
            assertThat(text(tool.execute("id", Map.of("action", "delete"), null, null)))
                    .contains("job_id is required");
        }

        @Test
        void serviceReturnsTrue() {
            when(cronService.deleteJob("x")).thenReturn(true);
            assertThat(text(tool.execute("id", Map.of("action", "delete", "job_id", "x"), null, null)))
                    .contains("Deleted job x");
        }

        @Test
        void serviceReturnsFalse() {
            when(cronService.deleteJob("x")).thenReturn(false);
            assertThat(text(tool.execute("id", Map.of("action", "delete", "job_id", "x"), null, null)))
                    .contains("Job not found");
        }
    }

    @Nested
    class HandleTrigger {

        @Test
        void missingJobIdRejected() {
            assertThat(text(tool.execute("id", Map.of("action", "trigger"), null, null)))
                    .contains("job_id is required");
        }

        @Test
        void successPath() {
            when(cronService.triggerJob("x"))
                    .thenReturn(new CronRunRecord("run1", "x", 0, 0, RunStatus.SUCCESS, null, "out", 1));
            assertThat(text(tool.execute("id", Map.of("action", "trigger", "job_id", "x"), null, null)))
                    .contains("Triggered job x")
                    .contains("run1")
                    .contains("SUCCESS");
        }

        @Test
        void illegalArgumentSurfaced() {
            when(cronService.triggerJob("x")).thenThrow(new IllegalArgumentException("not found"));
            assertThat(text(tool.execute("id", Map.of("action", "trigger", "job_id", "x"), null, null)))
                    .contains("Error: not found");
        }
    }

    @Nested
    class HandleStatus {

        @Test
        void missingJobIdRejected() {
            assertThat(text(tool.execute("id", Map.of("action", "status"), null, null)))
                    .contains("job_id is required");
        }

        @Test
        void notFound() {
            when(cronService.getJob("x")).thenReturn(Optional.empty());
            assertThat(text(tool.execute("id", Map.of("action", "status", "job_id", "x"), null, null)))
                    .contains("Job not found");
        }

        @Test
        void rendersAllStateBits() {
            CronJob j = job(
                    "x",
                    "alpha",
                    new CronSchedule.Every(60_000L),
                    new CronJobState(2_000_000, 1_000_000, 500_000, "success", 1, 7));
            when(cronService.getJob("x")).thenReturn(Optional.of(j));
            String out = text(tool.execute("id", Map.of("action", "status", "job_id", "x"), null, null));
            assertThat(out)
                    .contains("alpha")
                    .contains("Total runs: 7")
                    .contains("Currently running since")
                    .contains("Last run")
                    .contains("Next run")
                    .contains("Consecutive errors: 1");
        }
    }

    @Nested
    class HandleRuns {

        @Test
        void missingJobIdRejected() {
            assertThat(text(tool.execute("id", Map.of("action", "runs"), null, null)))
                    .contains("job_id is required");
        }

        @Test
        void noRunsMessage() {
            when(cronService.getRecentRuns(anyString(), anyInt())).thenReturn(List.of());
            assertThat(text(tool.execute("id", Map.of("action", "runs", "job_id", "x"), null, null)))
                    .contains("No run records");
        }

        @Test
        void renderRunsAndCustomLimit() {
            CronRunRecord r1 = new CronRunRecord("r1", "x", 100, 200, RunStatus.SUCCESS, null, "hello", 2);
            CronRunRecord r2 = new CronRunRecord("r2", "x", 300, 400, RunStatus.FAILED, "boom", null, 1);
            when(cronService.getRecentRuns("x", 5)).thenReturn(List.of(r1, r2));
            String out = text(tool.execute("id", Map.of("action", "runs", "job_id", "x", "limit", 5), null, null));
            assertThat(out).contains("r1").contains("r2").contains("hello").contains("(boom)");
        }
    }
}
