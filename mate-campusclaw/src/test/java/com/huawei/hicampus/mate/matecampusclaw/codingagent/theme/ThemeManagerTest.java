/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.hicampus.mate.matecampusclaw.codingagent.theme;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ThemeManagerTest {

    @Nested
    class BuiltInThemes {

        @Test
        void defaultActiveOnConstruction() {
            ThemeManager mgr = new ThemeManager();
            assertThat(mgr.getActiveTheme()).isNotNull();
            assertThat(mgr.getActiveTheme().name()).isEqualTo("default");
        }

        @Test
        void threeBuiltInThemesRegistered() {
            ThemeManager mgr = new ThemeManager();
            assertThat(mgr.getThemeNames()).containsExactlyInAnyOrder("default", "dark", "light");
        }

        @Test
        void defaultThemeHasExpectedKey() {
            ThemeManager mgr = new ThemeManager();
            Theme dflt = mgr.getTheme("default").orElseThrow();
            assertThat(dflt.colors()).containsEntry(Theme.PRIMARY, "cyan");
            assertThat(dflt.description()).isEqualTo("Default theme");
        }

        @Test
        void darkAndLightHaveDistinctPalettes() {
            ThemeManager mgr = new ThemeManager();
            Theme dark = mgr.getTheme("dark").orElseThrow();
            Theme light = mgr.getTheme("light").orElseThrow();
            assertThat(dark.colors().get(Theme.PRIMARY))
                    .isNotEqualTo(light.colors().get(Theme.PRIMARY));
        }
    }

    @Nested
    class SetActiveTheme {

        @Test
        void existingNameSwitches() {
            ThemeManager mgr = new ThemeManager();
            assertThat(mgr.setActiveTheme("dark")).isTrue();
            assertThat(mgr.getActiveTheme().name()).isEqualTo("dark");
        }

        @Test
        void missingNameReturnsFalseAndKeepsCurrent() {
            ThemeManager mgr = new ThemeManager();
            assertThat(mgr.setActiveTheme("does-not-exist")).isFalse();
            assertThat(mgr.getActiveTheme().name()).isEqualTo("default");
        }
    }

    @Nested
    class GetTheme {

        @Test
        void presentReturnsOptional() {
            ThemeManager mgr = new ThemeManager();
            assertThat(mgr.getTheme("light")).isPresent();
        }

        @Test
        void absentReturnsEmpty() {
            ThemeManager mgr = new ThemeManager();
            assertThat(mgr.getTheme("nope")).isEmpty();
        }
    }

    @Nested
    class Register {

        @Test
        void customThemeAppearsInNamesAndGet() {
            ThemeManager mgr = new ThemeManager();
            Theme custom = new Theme("custom", Map.of(Theme.PRIMARY, "magenta"), "tester");
            mgr.register(custom);
            assertThat(mgr.getThemeNames()).contains("custom");
            assertThat(mgr.getTheme("custom")).contains(custom);
        }

        @Test
        void duplicateNameOverwrites() {
            ThemeManager mgr = new ThemeManager();
            Theme first = new Theme("custom", Map.of(Theme.PRIMARY, "red"), null);
            Theme second = new Theme("custom", Map.of(Theme.PRIMARY, "blue"), null);
            mgr.register(first);
            mgr.register(second);
            assertThat(mgr.getTheme("custom"))
                    .map(Theme::colors)
                    .map(c -> c.get(Theme.PRIMARY))
                    .contains("blue");
        }
    }

    @Nested
    class LoadFromDirectory {

        @Test
        void nonExistentDirIsNoOp(@TempDir Path tmp) {
            ThemeManager mgr = new ThemeManager();
            Optional<Theme> before = mgr.getTheme("ghost");
            mgr.loadFromDirectory(tmp.resolve("does/not/exist"));

            // Still only built-ins
            assertThat(mgr.getThemeNames()).hasSize(3);
            assertThat(before).isEqualTo(mgr.getTheme("ghost"));
        }

        @Test
        void notADirectoryIsNoOp(@TempDir Path tmp) throws IOException {
            Path file = tmp.resolve("file.json");
            Files.writeString(file, "{}");
            ThemeManager mgr = new ThemeManager();
            mgr.loadFromDirectory(file);
            assertThat(mgr.getThemeNames()).hasSize(3);
        }

        @Test
        void loadsValidJsonTheme(@TempDir Path tmp) throws IOException {
            Path themeFile = tmp.resolve("mytheme.json");
            Files.writeString(themeFile, "{\"name\":\"mytheme\",\"colors\":{\"primary\":\"red\"}}");
            ThemeManager mgr = new ThemeManager();
            mgr.loadFromDirectory(tmp);
            assertThat(mgr.getTheme("mytheme")).isPresent();
        }

        @Test
        void malformedJsonIsSwallowed(@TempDir Path tmp) throws IOException {
            Path themeFile = tmp.resolve("bad.json");
            Files.writeString(themeFile, "{not json");
            ThemeManager mgr = new ThemeManager();
            mgr.loadFromDirectory(tmp);

            // Still 3 built-ins, bad file ignored
            assertThat(mgr.getThemeNames()).hasSize(3);
        }

        @Test
        void nonJsonFilesIgnored(@TempDir Path tmp) throws IOException {
            Files.writeString(tmp.resolve("notes.txt"), "ignore me");
            ThemeManager mgr = new ThemeManager();
            mgr.loadFromDirectory(tmp);
            assertThat(mgr.getThemeNames()).hasSize(3);
        }
    }
}
