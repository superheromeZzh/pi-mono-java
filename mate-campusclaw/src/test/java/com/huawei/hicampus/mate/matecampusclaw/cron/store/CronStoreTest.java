package com.huawei.hicampus.mate.matecampusclaw.cron.store;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import com.huawei.hicampus.mate.matecampusclaw.cron.model.CronJob;
import com.huawei.hicampus.mate.matecampusclaw.cron.model.CronPayload;
import com.huawei.hicampus.mate.matecampusclaw.cron.model.CronSchedule;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.*;

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

    private CronJob createJob(String name) {
        return CronJob.create(name, null,
            new CronSchedule.Every(60000L),
            new CronPayload.AgentPrompt("do something", null, null, null));
    }
}
