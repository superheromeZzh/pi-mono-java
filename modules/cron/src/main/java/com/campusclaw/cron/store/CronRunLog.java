package com.campusclaw.cron.store;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.campusclaw.cron.model.CronRunRecord;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Append-only JSONL log for cron job execution records.
 * Each job has its own log file at {@code ~/.campusclaw/agent/cron/runs/{jobId}.jsonl}.
 */
@Service
public class CronRunLog {

    private static final Logger log = LoggerFactory.getLogger(CronRunLog.class);

    private final ObjectMapper mapper;
    private final Path runsDir;

    public CronRunLog() {
        this(defaultRunsDir());
    }

    public CronRunLog(Path runsDir) {
        this.runsDir = runsDir;
        this.mapper = new ObjectMapper();
        this.mapper.setSerializationInclusion(JsonInclude.Include.NON_NULL);
        this.mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    }

    public void appendRun(CronRunRecord record) {
        Path logFile = runsDir.resolve(record.jobId() + ".jsonl");
        try {
            Files.createDirectories(runsDir);
            String json = mapper.writeValueAsString(record);
            try (BufferedWriter writer = Files.newBufferedWriter(logFile, StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND)) {
                writer.write(json);
                writer.newLine();
            }
        } catch (IOException e) {
            log.error("Failed to append cron run record for job {}", record.jobId(), e);
        }
    }

    public List<CronRunRecord> getRecentRuns(String jobId, int limit) {
        Path logFile = runsDir.resolve(jobId + ".jsonl");
        if (!Files.exists(logFile)) {
            return List.of();
        }
        try {
            List<String> lines = Files.readAllLines(logFile, StandardCharsets.UTF_8);
            var records = new ArrayList<CronRunRecord>();
            // Read from end for most recent
            int start = Math.max(0, lines.size() - limit);
            for (int i = start; i < lines.size(); i++) {
                String line = lines.get(i).trim();
                if (line.isEmpty()) continue;
                try {
                    records.add(mapper.readValue(line, CronRunRecord.class));
                } catch (IOException e) {
                    log.debug("Skipping malformed run log line: {}", line);
                }
            }
            Collections.reverse(records);
            return records;
        } catch (IOException e) {
            log.warn("Failed to read cron run log for job {}", jobId, e);
            return List.of();
        }
    }

    private static Path defaultRunsDir() {
        return Path.of(System.getProperty("user.home"))
            .resolve(".campusclaw").resolve("agent").resolve("cron").resolve("runs");
    }
}
