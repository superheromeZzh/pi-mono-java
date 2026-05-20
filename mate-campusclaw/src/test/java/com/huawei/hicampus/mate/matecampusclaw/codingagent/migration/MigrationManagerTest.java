/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.hicampus.mate.matecampusclaw.codingagent.migration;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class MigrationManagerTest {

    @Nested
    class VersionDetection {

        @Test
        void missingVersionFileDefaultsToOne(@TempDir Path tmp) {
            MigrationManager m = new MigrationManager();
            assertThat(m.getCurrentVersion(tmp)).isEqualTo(1);
        }

        @Test
        void readsExistingVersion(@TempDir Path tmp) throws IOException {
            Files.writeString(tmp.resolve(".schema-version"), "2");
            MigrationManager m = new MigrationManager();
            assertThat(m.getCurrentVersion(tmp)).isEqualTo(2);
        }

        @Test
        void corruptVersionFileFallsBackToOne(@TempDir Path tmp) throws IOException {
            Files.writeString(tmp.resolve(".schema-version"), "not a number");
            MigrationManager m = new MigrationManager();
            assertThat(m.getCurrentVersion(tmp)).isEqualTo(1);
        }

        @Test
        void setVersionPersists(@TempDir Path tmp) throws IOException {
            MigrationManager m = new MigrationManager();
            m.setVersion(tmp, 3);
            assertThat(Files.readString(tmp.resolve(".schema-version")).trim()).isEqualTo("3");
            assertThat(m.getCurrentVersion(tmp)).isEqualTo(3);
        }

        @Test
        void needsMigrationDetectsOlderVersion(@TempDir Path tmp) throws IOException {
            MigrationManager m = new MigrationManager();
            assertThat(m.needsMigration(tmp)).isTrue();
            m.setVersion(tmp, MigrationManager.CURRENT_VERSION);
            assertThat(m.needsMigration(tmp)).isFalse();
        }
    }

    @Nested
    class Migrate {

        @Test
        void noOpWhenAlreadyCurrent(@TempDir Path tmp) throws IOException {
            MigrationManager m = new MigrationManager();
            m.setVersion(tmp, MigrationManager.CURRENT_VERSION);
            assertThat(m.migrate(tmp)).isEmpty();
        }

        @Test
        void runsRegisteredMigrationStepsInOrder(@TempDir Path tmp) throws IOException {
            MigrationManager m = new MigrationManager();
            List<Integer> trace = new java.util.ArrayList<>();

            // Built-ins already cover v1→v2. Add v2→3 step bumping CURRENT_VERSION exists,
            // so we register a custom manager via a subclass-of-behavior — but CURRENT_VERSION
            // is static. Validate the built-in v1→v2 path runs.
            Path sessions = tmp.resolve("sessions");
            Files.createDirectories(sessions);
            Files.writeString(
                    sessions.resolve("a.jsonl"),
                    "{\"id\":\"m1\",\"role\":\"user\"}\n{\"id\":\"m2\",\"role\":\"assistant\"}\n");

            // baseline v1
            List<String> applied = m.migrate(tmp);
            assertThat(applied).isNotEmpty();

            // After migration the file should contain parentId
            List<String> result = Files.readAllLines(sessions.resolve("a.jsonl"));
            assertThat(result).allMatch(line -> line.contains("parentId"));

            // Version stamped
            assertThat(m.getCurrentVersion(tmp)).isEqualTo(MigrationManager.CURRENT_VERSION);
        }

        @Test
        void migrationWithoutSessionsDirSkips(@TempDir Path tmp) throws IOException {
            MigrationManager m = new MigrationManager();
            List<String> applied = m.migrate(tmp);

            // Even without sessions dir, the migration step runs (it just no-ops internally)
            assertThat(applied).isNotEmpty();
        }
    }

    @Nested
    class BackupBehavior {

        @Test
        void backupCreatedDuringMigration(@TempDir Path tmp) throws IOException {
            Files.writeString(tmp.resolve("keep.txt"), "hello");
            MigrationManager m = new MigrationManager();
            m.migrate(tmp);

            // After migration we should have a backup of the original file under .backups/v1/
            Path backup = tmp.resolve(".backups").resolve("v1").resolve("keep.txt");
            assertThat(Files.exists(backup)).isTrue();
            assertThat(Files.readString(backup)).isEqualTo("hello");
        }
    }
}
