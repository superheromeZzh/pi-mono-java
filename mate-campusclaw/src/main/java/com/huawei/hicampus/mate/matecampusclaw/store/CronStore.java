package com.huawei.hicampus.mate.matecampusclaw.cron.store;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import com.huawei.hicampus.mate.matecampusclaw.cron.model.CronJob;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * JSON file persistence for cron job definitions.
 * Stores jobs in {@code ~/.campusclaw/agent/cron/jobs.json}.
 */
@Service
public class CronStore {

    private static final Logger log = LoggerFactory.getLogger(CronStore.class);

    private final ObjectMapper mapper;
    private final Path jobsFile;
    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();

    public CronStore() {
        this(defaultJobsPath());
    }

    public CronStore(Path jobsFile) {
        this.jobsFile = jobsFile;
        this.mapper = new ObjectMapper();
        this.mapper.setSerializationInclusion(JsonInclude.Include.NON_NULL);
        this.mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    }

    public List<CronJob> load() {
        lock.readLock().lock();
        try {
            if (!Files.exists(jobsFile)) {
                return List.of();
            }
            var file = mapper.readValue(jobsFile.toFile(), JobsFile.class);
            return file.jobs() != null ? file.jobs() : List.of();
        } catch (IOException e) {
            log.warn("Failed to load cron jobs from {}", jobsFile, e);
            return List.of();
        } finally {
            lock.readLock().unlock();
        }
    }

    public void save(List<CronJob> jobs) {
        lock.writeLock().lock();
        try {
            Files.createDirectories(jobsFile.getParent());
            mapper.writerWithDefaultPrettyPrinter()
                .writeValue(jobsFile.toFile(), new JobsFile(1, jobs));
        } catch (IOException e) {
            log.error("Failed to save cron jobs to {}", jobsFile, e);
        } finally {
            lock.writeLock().unlock();
        }
    }

    public CronJob addJob(CronJob job) {
        lock.writeLock().lock();
        try {
            var jobs = new ArrayList<>(loadUnsafe());
            jobs.add(job);
            saveUnsafe(jobs);
            return job;
        } finally {
            lock.writeLock().unlock();
        }
    }

    public boolean removeJob(String jobId) {
        lock.writeLock().lock();
        try {
            var jobs = new ArrayList<>(loadUnsafe());
            boolean removed = jobs.removeIf(j -> j.id().equals(jobId));
            if (removed) {
                saveUnsafe(jobs);
            }
            return removed;
        } finally {
            lock.writeLock().unlock();
        }
    }

    public Optional<CronJob> getJob(String jobId) {
        return load().stream().filter(j -> j.id().equals(jobId)).findFirst();
    }

    public void updateJob(CronJob updated) {
        lock.writeLock().lock();
        try {
            var jobs = new ArrayList<>(loadUnsafe());
            for (int i = 0; i < jobs.size(); i++) {
                if (jobs.get(i).id().equals(updated.id())) {
                    jobs.set(i, updated);
                    break;
                }
            }
            saveUnsafe(jobs);
        } finally {
            lock.writeLock().unlock();
        }
    }

    private List<CronJob> loadUnsafe() {
        try {
            if (!Files.exists(jobsFile)) {
                return List.of();
            }
            var file = mapper.readValue(jobsFile.toFile(), JobsFile.class);
            return file.jobs() != null ? file.jobs() : List.of();
        } catch (IOException e) {
            log.warn("Failed to load cron jobs", e);
            return List.of();
        }
    }

    private void saveUnsafe(List<CronJob> jobs) {
        try {
            Files.createDirectories(jobsFile.getParent());
            mapper.writerWithDefaultPrettyPrinter()
                .writeValue(jobsFile.toFile(), new JobsFile(1, jobs));
        } catch (IOException e) {
            log.error("Failed to save cron jobs", e);
        }
    }

    /**
     * Acquire an inter-process exclusive file lock for safe concurrent access
     * from multiple JVM instances (e.g. --cron-tick via system scheduler).
     * Returns null if locking fails.
     */
    public FileLock acquireProcessLock() {
        try {
            Files.createDirectories(jobsFile.getParent());
            Path lockPath = jobsFile.resolveSibling(jobsFile.getFileName() + ".lock");
            var channel = FileChannel.open(lockPath,
                    StandardOpenOption.CREATE, StandardOpenOption.WRITE);
            FileLock fileLock = channel.tryLock();
            if (fileLock == null) {
                channel.close();
                return null;
            }
            return fileLock;
        } catch (IOException e) {
            log.warn("Failed to acquire process lock", e);
            return null;
        }
    }

    /**
     * Release a previously acquired process lock.
     */
    public void releaseProcessLock(FileLock fileLock) {
        if (fileLock == null) return;
        try {
            var channel = fileLock.channel();
            fileLock.release();
            channel.close();
        } catch (IOException e) {
            log.debug("Error releasing process lock", e);
        }
    }

    private static Path defaultJobsPath() {
        return Path.of(System.getProperty("user.home"))
            .resolve(".campusclaw").resolve("agent").resolve("cron").resolve("jobs.json");
    }

    record JobsFile(int version, List<CronJob> jobs) {}
}
