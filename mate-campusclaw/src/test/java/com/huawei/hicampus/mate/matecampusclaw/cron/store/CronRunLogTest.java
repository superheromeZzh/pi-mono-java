/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.hicampus.mate.matecampusclaw.cron.store;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import com.huawei.hicampus.mate.matecampusclaw.cron.model.CronRunRecord;
import com.huawei.hicampus.mate.matecampusclaw.cron.model.CronRunRecord.RunStatus;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CronRunLogTest {

    private static CronRunRecord rec(String runId, String jobId, RunStatus status, String output) {
        return new CronRunRecord(runId, jobId, 0L, 0L, status, null, output, 0);
    }

    @Nested
    class Append {

        @Test
        void appendCreatesFile(@TempDir Path tmp) {
            CronRunLog logStore = new CronRunLog(tmp);
            logStore.appendRun(rec("r1", "j1", RunStatus.SUCCESS, "ok"));
            assertThat(Files.exists(tmp.resolve("j1.jsonl"))).isTrue();
        }

        @Test
        void multipleAppendsAccumulate(@TempDir Path tmp) throws IOException {
            CronRunLog logStore = new CronRunLog(tmp);
            logStore.appendRun(rec("r1", "j1", RunStatus.SUCCESS, "a"));
            logStore.appendRun(rec("r2", "j1", RunStatus.FAILED, "b"));
            List<String> lines = Files.readAllLines(tmp.resolve("j1.jsonl"));
            assertThat(lines).hasSize(2);
        }
    }

    @Nested
    class GetRecentRuns {

        @Test
        void emptyWhenFileMissing(@TempDir Path tmp) {
            assertThat(new CronRunLog(tmp).getRecentRuns("ghost", 10)).isEmpty();
        }

        @Test
        void returnsMostRecentFirst(@TempDir Path tmp) {
            CronRunLog logStore = new CronRunLog(tmp);
            logStore.appendRun(rec("r1", "j1", RunStatus.SUCCESS, "first"));
            logStore.appendRun(rec("r2", "j1", RunStatus.SUCCESS, "second"));
            logStore.appendRun(rec("r3", "j1", RunStatus.SUCCESS, "third"));
            List<CronRunRecord> recent = logStore.getRecentRuns("j1", 10);
            assertThat(recent).extracting(CronRunRecord::runId).containsExactly("r3", "r2", "r1");
        }

        @Test
        void respectsLimit(@TempDir Path tmp) {
            CronRunLog logStore = new CronRunLog(tmp);
            for (int i = 0; i < 5; i++) {
                logStore.appendRun(rec("r" + i, "j1", RunStatus.SUCCESS, "x"));
            }
            List<CronRunRecord> recent = logStore.getRecentRuns("j1", 2);
            assertThat(recent).hasSize(2);
        }

        @Test
        void skipsBlankAndMalformedLines(@TempDir Path tmp) throws IOException {
            Files.writeString(
                    tmp.resolve("j1.jsonl"),
                    "\n{not json}\n{\"runId\":\"r1\",\"jobId\":\"j1\",\"startedAtMs\":0,"
                            + "\"finishedAtMs\":0,\"status\":\"SUCCESS\",\"turnCount\":1}\n");
            List<CronRunRecord> recent = new CronRunLog(tmp).getRecentRuns("j1", 10);
            assertThat(recent).hasSize(1);
            assertThat(recent.get(0).runId()).isEqualTo("r1");
        }
    }

    @Nested
    class DefaultDir {

        @Test
        void defaultConstructorDoesNotThrow() {
            assertThat(new CronRunLog()).isNotNull();
        }
    }
}
