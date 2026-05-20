/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.hicampus.mate.matecampusclaw.cron.store;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import com.huawei.hicampus.mate.matecampusclaw.cron.model.CronJob;
import com.huawei.hicampus.mate.matecampusclaw.cron.model.CronPayload;
import com.huawei.hicampus.mate.matecampusclaw.cron.model.CronSchedule;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CronStoreTest {

    @TempDir
    Path tempDir;

    private CronStore store;

    @BeforeEach
    void setUp() {
        store = new CronStore(tempDir.resolve("jobs.json"));
    }

    @Test
    void loadReturnsEmptyWhenNoFile() {
        var jobs = store.load();
        assertTrue(jobs.isEmpty());
    }

    @Test
    void addAndLoadJob() {
        var job = createJob("test-1");
        store.addJob(job);

        var jobs = store.load();
        assertEquals(1, jobs.size());
        assertEquals("test-1", jobs.get(0).name());
    }

    @Test
    void removeJob() {
        var job = createJob("to-remove");
        store.addJob(job);

        assertTrue(store.removeJob(job.id()));
        assertTrue(store.load().isEmpty());
    }

    @Test
    void removeNonExistentJobReturnsFalse() {
        assertFalse(store.removeJob("nonexistent"));
    }

    @Test
    void updateJob() {
        var job = createJob("to-update");
        store.addJob(job);

        var updated = job.withEnabled(false);
        store.updateJob(updated);

        var jobs = store.load();
        assertEquals(1, jobs.size());
        assertFalse(jobs.get(0).enabled());
    }

    @Test
    void getJob() {
        var job = createJob("find-me");
        store.addJob(job);

        var found = store.getJob(job.id());
        assertTrue(found.isPresent());
        assertEquals("find-me", found.get().name());
    }

    @Test
    void getJobNotFound() {
        assertTrue(store.getJob("nonexistent").isEmpty());
    }

    @Test
    void multipleJobs() {
        store.addJob(createJob("job-1"));
        store.addJob(createJob("job-2"));
        store.addJob(createJob("job-3"));

        var jobs = store.load();
        assertEquals(3, jobs.size());
    }

    @Test
    void persistsToDisk() throws IOException {
        store.addJob(createJob("persisted"));

        // Verify file exists
        assertTrue(Files.exists(tempDir.resolve("jobs.json")));

        // Create new store instance pointing to same file
        var store2 = new CronStore(tempDir.resolve("jobs.json"));
        var jobs = store2.load();
        assertEquals(1, jobs.size());
        assertEquals("persisted", jobs.get(0).name());
    }

    @Test
    void saveListReplacesAllJobs() {
        store.addJob(createJob("a"));
        store.addJob(createJob("b"));

        // save() with a new list should replace previously stored jobs entirely.
        var replacement = java.util.List.of(createJob("only-one"));
        store.save(replacement);

        var jobs = store.load();
        assertEquals(1, jobs.size());
        assertEquals("only-one", jobs.get(0).name());
    }

    @Test
    void loadReturnsEmptyForCorruptJsonFile() throws IOException {
        Files.writeString(
                tempDir.resolve("jobs.json"), "{ this is not valid json", java.nio.charset.StandardCharsets.UTF_8);
        assertTrue(store.load().isEmpty(), "corrupt file should not crash; load() returns empty list");
    }

    @Test
    void updateNonExistentJobIsNoOp() {
        var job = createJob("ghost");
        store.updateJob(job); // file doesn't exist yet, should not throw and should not create stale state
        assertTrue(store.load().isEmpty(), "updateJob against a missing id must not implicitly add it");
    }

    @Test
    void processLockAcquireAndRelease() {
        var lock = store.acquireProcessLock();
        assertTrue(lock != null, "first acquireProcessLock should succeed");

        // releaseProcessLock(null) is allowed and must not throw.
        store.releaseProcessLock(null);
        store.releaseProcessLock(lock);

        // After release, lock is acquirable again.
        var second = store.acquireProcessLock();
        assertTrue(second != null, "lock acquirable again after release");
        store.releaseProcessLock(second);
    }

    private CronJob createJob(String name) {
        return CronJob.create(
                name,
                null,
                new CronSchedule.Every(60000L),
                new CronPayload.AgentPrompt("do something", null, null, null));
    }
}
