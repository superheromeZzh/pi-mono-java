package com.huawei.hicampus.mate.matecampusclaw.codingagent.migration;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;

import com.fasterxml.jackson.databind.ObjectMapper;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Manages data format migrations for sessions and settings.
 * Supports versioned schema upgrades with rollback capability.
 */
public class MigrationManager {
    private static final Logger log = LoggerFactory.getLogger(MigrationManager.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String VERSION_FILE = ".schema-version";
    public static final int CURRENT_VERSION = 2;

    @FunctionalInterface
    public interface Migration {
        void apply(Path dataDir) throws IOException;
    }

    public record MigrationStep(
        int fromVersion,
        int toVersion,
        String description,
        Migration migration
    ) {}

    private final List<MigrationStep> migrations = new ArrayList<>();

    public MigrationManager() {
        registerBuiltins();
    }

    /** Register a migration step. */
    public void register(MigrationStep step) {
        migrations.add(step);
        migrations.sort(Comparator.comparingInt(MigrationStep::fromVersion));
    }

    /** Get the current schema version for a data directory. */
    public int getCurrentVersion(Path dataDir) {
        Path versionFile = dataDir.resolve(VERSION_FILE);
        if (!Files.exists(versionFile)) return 1; // Default to version 1
        try {
            String content = Files.readString(versionFile).trim();
            return Integer.parseInt(content);
        } catch (IOException | NumberFormatException e) {
            return 1;
        }
    }

    /** Set the schema version for a data directory. */
    public void setVersion(Path dataDir, int version) throws IOException {
        Files.createDirectories(dataDir);
        Files.writeString(dataDir.resolve(VERSION_FILE), String.valueOf(version));
    }

    /** Check if migration is needed. */
    public boolean needsMigration(Path dataDir) {
        return getCurrentVersion(dataDir) < CURRENT_VERSION;
    }

    /** Run all needed migrations. */
    public List<String> migrate(Path dataDir) throws IOException {
        int current = getCurrentVersion(dataDir);
        if (current >= CURRENT_VERSION) {
            return List.of();
        }

        List<String> applied = new ArrayList<>();
        for (MigrationStep step : migrations) {
            if (step.fromVersion() >= current && step.toVersion() <= CURRENT_VERSION) {
                log.info("Applying migration v{} -> v{}: {}", step.fromVersion(), step.toVersion(), step.description());
                // Create backup before migration
                createBackup(dataDir, step.fromVersion());
                try {
                    step.migration().apply(dataDir);
                    setVersion(dataDir, step.toVersion());
                    applied.add(step.description());
                    current = step.toVersion();
                } catch (IOException e) {
                    log.error("Migration failed at v{} -> v{}: {}", step.fromVersion(), step.toVersion(), e.getMessage());
                    throw e;
                }
            }
        }
        return applied;
    }

    /** Create a backup of the data directory before migration. */
    private void createBackup(Path dataDir, int version) {
        Path backupDir = dataDir.resolve(".backups").resolve("v" + version);
        try {
            Files.createDirectories(backupDir);
            // Copy key files
            try (var stream = Files.list(dataDir)) {
                stream.filter(p -> !p.getFileName().toString().startsWith("."))
                    .filter(Files::isRegularFile)
                    .forEach(p -> {
                        try {
                            Files.copy(p, backupDir.resolve(p.getFileName()),
                                StandardCopyOption.REPLACE_EXISTING);
                        } catch (IOException e) {
                            log.warn("Failed to backup file: {}", p, e);
                        }
                    });
            }
            log.debug("Created backup at {}", backupDir);
        } catch (IOException e) {
            log.warn("Failed to create backup directory: {}", backupDir, e);
        }
    }

    /** Register built-in migrations. */
    private void registerBuiltins() {
        // v1 -> v2: Add parentId to session entries for tree support
        register(new MigrationStep(1, 2, "Add session tree support (parentId field)", dataDir -> {
            Path sessionsDir = dataDir.resolve("sessions");
            if (!Files.isDirectory(sessionsDir)) return;

            try (var stream = Files.list(sessionsDir)) {
                stream.filter(p -> p.toString().endsWith(".jsonl"))
                    .forEach(sessionFile -> {
                        try {
                            List<String> lines = Files.readAllLines(sessionFile);
                            List<String> updated = new ArrayList<>();
                            String lastId = null;
                            for (String line : lines) {
                                if (line.isBlank()) continue;
                                // Add parentId if not present
                                if (!line.contains("\"parentId\"")) {
                                    // Insert parentId before the closing brace
                                    String parentField = lastId != null
                                        ? ",\"parentId\":\"" + lastId + "\""
                                        : ",\"parentId\":null";
                                    line = line.substring(0, line.length() - 1) + parentField + "}";
                                }
                                // Extract id for next iteration
                                int idIdx = line.indexOf("\"id\":\"");
                                if (idIdx >= 0) {
                                    int start = idIdx + 6;
                                    int end = line.indexOf('"', start);
                                    if (end > start) lastId = line.substring(start, end);
                                }
                                updated.add(line);
                            }
                            Files.write(sessionFile, updated);
                        } catch (IOException e) {
                            log.warn("Failed to migrate session file: {}", sessionFile, e);
                        }
                    });
            }
        }));
    }
}
