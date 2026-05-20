/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.campusclaw.codingagent.util;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ChangelogTest {

    private static Path writeJson(Path dir, String json) throws IOException {
        Path f = dir.resolve("changelog.json");
        Files.writeString(f, json);
        return f;
    }

    @Nested
    class Loading {

        @Test
        void loadFromValidFileSortsNewestFirst(@TempDir Path tmp) throws IOException {
            Path file = writeJson(
                    tmp,
                    "["
                            + "{\"version\":\"1.0\",\"date\":\"2024-01-01\",\"changes\":[]},"
                            + "{\"version\":\"2.0\",\"date\":\"2025-05-01\",\"title\":\"newer\",\"changes\":[]}]");
            Changelog cl = new Changelog(tmp);
            cl.loadFromFile(file);
            assertThat(cl.getAll()).hasSize(2);
            assertThat(cl.getAll().get(0).version()).isEqualTo("2.0");
        }

        @Test
        void loadFromMissingFileIsSwallowed(@TempDir Path tmp) {
            Changelog cl = new Changelog(tmp);
            cl.loadFromFile(tmp.resolve("does-not-exist.json"));
            assertThat(cl.getAll()).isEmpty();
        }

        @Test
        void loadFromMalformedJsonIsSwallowed(@TempDir Path tmp) throws IOException {
            Path file = writeJson(tmp, "{not valid json");
            Changelog cl = new Changelog(tmp);
            cl.loadFromFile(file);
            assertThat(cl.getAll()).isEmpty();
        }

        @Test
        void loadFromMissingResourceLogsAndReturns(@TempDir Path tmp) {
            Changelog cl = new Changelog(tmp);
            cl.loadFromResource("/no-such-changelog-resource.json");
            assertThat(cl.getAll()).isEmpty();
        }
    }

    @Nested
    class UnreadTracking {

        @Test
        void allUnreadWhenStateMissing(@TempDir Path tmp) throws IOException {
            Path file = writeJson(
                    tmp,
                    "["
                            + "{\"version\":\"1.0\",\"date\":\"2024-01-01\",\"changes\":[]},"
                            + "{\"version\":\"2.0\",\"date\":\"2025-01-01\",\"changes\":[]}]");
            Changelog cl = new Changelog(tmp);
            cl.loadFromFile(file);
            assertThat(cl.hasUnread()).isTrue();
            assertThat(cl.getUnread()).hasSize(2);
        }

        @Test
        void markAllReadPersistsAndClearsUnread(@TempDir Path tmp) throws IOException {
            Path file = writeJson(tmp, "[{\"version\":\"1.0\",\"date\":\"2024-01-01\",\"changes\":[]}]");
            Changelog cl = new Changelog(tmp);
            cl.loadFromFile(file);
            cl.markAllRead();
            assertThat(cl.hasUnread()).isFalse();

            // New instance reads the persisted state
            Changelog cl2 = new Changelog(tmp);
            cl2.loadFromFile(file);
            assertThat(cl2.hasUnread()).isFalse();
        }

        @Test
        void markAllReadNoOpWhenEmpty(@TempDir Path tmp) {
            Changelog cl = new Changelog(tmp);
            cl.markAllRead();
            assertThat(cl.hasUnread()).isFalse();
        }

        @Test
        void unreadStopsAtLastReadVersion(@TempDir Path tmp) throws IOException {
            Path file = writeJson(
                    tmp,
                    "["
                            + "{\"version\":\"2.0\",\"date\":\"2025-01-01\",\"changes\":[]},"
                            + "{\"version\":\"1.0\",\"date\":\"2024-01-01\",\"changes\":[]}]");
            Changelog cl = new Changelog(tmp);
            cl.loadFromFile(file);

            // Manually pre-populate state file (simulates a previous run where 1.0 was current)
            Files.writeString(tmp.resolve("changelog-read.txt"), "1.0");
            Changelog cl2 = new Changelog(tmp);
            cl2.loadFromFile(file);

            // 2.0 unread, then break at 1.0
            assertThat(cl2.getUnread())
                    .extracting(Changelog.ChangelogEntry::version)
                    .containsExactly("2.0");
        }
    }

    @Nested
    class Formatting {

        @Test
        void formatRendersHeaderAndIcons() {
            Changelog.ChangelogEntry entry = new Changelog.ChangelogEntry(
                    "1.0",
                    "2025-01-01",
                    "Release",
                    List.of(
                            new Changelog.Change("feature", "added X"),
                            new Changelog.Change("fix", "fixed Y"),
                            new Changelog.Change("improvement", "improved Z"),
                            new Changelog.Change("breaking", "broke W"),
                            new Changelog.Change("unknown", "other")));
            String text = Changelog.format(entry);
            assertThat(text)
                    .contains("1.0")
                    .contains("Release")
                    .contains("2025-01-01")
                    .contains("added X")
                    .contains("fixed Y")
                    .contains("improved Z")
                    .contains("broke W")
                    .contains("other");
        }

        @Test
        void formatWithoutTitle() {
            Changelog.ChangelogEntry entry = new Changelog.ChangelogEntry(
                    "1.0", "2025-01-01", null, List.of(new Changelog.Change("feature", "x")));
            String text = Changelog.format(entry);
            assertThat(text).contains("1.0").doesNotContain(" — ");
        }

        @Test
        void formatAllConcatenates(@TempDir Path tmp) throws IOException {
            Path file = writeJson(
                    tmp,
                    "["
                            + "{\"version\":\"1.0\",\"date\":\"2024-01-01\",\"changes\":[{\"type\":\"fix\",\"description\":\"a\"}]},"
                            + "{\"version\":\"2.0\",\"date\":\"2025-01-01\",\"changes\":[{\"type\":\"feature\",\"description\":\"b\"}]}]");
            Changelog cl = new Changelog(tmp);
            cl.loadFromFile(file);
            String text = cl.formatAll();
            assertThat(text).contains("1.0").contains("2.0").contains("a").contains("b");
        }
    }
}
