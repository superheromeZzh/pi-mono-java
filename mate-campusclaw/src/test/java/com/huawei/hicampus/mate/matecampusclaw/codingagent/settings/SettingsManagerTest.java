/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.hicampus.mate.matecampusclaw.codingagent.settings;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SettingsManagerTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Nested
    class DeepMerge {

        @Test
        void overrideObjectWinsForLeaves() throws Exception {
            JsonNode base = MAPPER.readTree("{\"a\":1,\"b\":2}");
            JsonNode override = MAPPER.readTree("{\"a\":10,\"c\":3}");
            JsonNode merged = SettingsManager.deepMerge(base, override);
            assertThat(merged.get("a").asInt()).isEqualTo(10);
            assertThat(merged.get("b").asInt()).isEqualTo(2);
            assertThat(merged.get("c").asInt()).isEqualTo(3);
        }

        @Test
        void deepMergeRecurses() throws Exception {
            JsonNode base = MAPPER.readTree("{\"x\":{\"a\":1,\"b\":2}}");
            JsonNode override = MAPPER.readTree("{\"x\":{\"b\":20}}");
            JsonNode merged = SettingsManager.deepMerge(base, override);
            assertThat(merged.get("x").get("a").asInt()).isEqualTo(1);
            assertThat(merged.get("x").get("b").asInt()).isEqualTo(20);
        }

        @Test
        void nonObjectOverrideReplaces() throws Exception {
            JsonNode base = MAPPER.readTree("{\"x\":{\"a\":1}}");
            JsonNode override = MAPPER.readTree("\"replaced\"");
            JsonNode merged = SettingsManager.deepMerge(base, override);
            assertThat(merged.isTextual()).isTrue();
            assertThat(merged.asText()).isEqualTo("replaced");
        }

        @Test
        void mismatchedTypesOverridesWins() throws Exception {
            JsonNode base = MAPPER.readTree("{\"x\":{\"a\":1}}");
            JsonNode override = MAPPER.readTree("{\"x\":42}");
            JsonNode merged = SettingsManager.deepMerge(base, override);
            assertThat(merged.get("x").asInt()).isEqualTo(42);
        }
    }

    @Nested
    class LoadAndSaveProject {

        @Test
        void emptyProjectGivesEmptySettings(@TempDir Path tmp) {
            SettingsManager mgr = new SettingsManager();
            mgr.setWorkingDir(tmp);
            Settings settings = mgr.load();
            assertThat(settings).isNotNull();
        }

        @Test
        void projectFileValuesRead(@TempDir Path tmp) throws IOException {
            Path settingsFile = tmp.resolve(".campusclaw").resolve("settings.json");
            Files.createDirectories(settingsFile.getParent());
            Files.writeString(settingsFile, "{\"theme\":\"dark\"}");
            SettingsManager mgr = new SettingsManager();
            mgr.setWorkingDir(tmp);
            Settings settings = mgr.load();
            assertThat(settings.theme()).isEqualTo("dark");
        }

        @Test
        void setProjectPersistsKey(@TempDir Path tmp) throws IOException {
            SettingsManager mgr = new SettingsManager();
            mgr.setWorkingDir(tmp);
            mgr.setProject("theme", "light");
            Path settingsFile = tmp.resolve(".campusclaw").resolve("settings.json");
            assertThat(Files.readString(settingsFile)).contains("theme").contains("light");
        }

        @Test
        void setProjectMergesWithExisting(@TempDir Path tmp) throws IOException {
            Path settingsFile = tmp.resolve(".campusclaw").resolve("settings.json");
            Files.createDirectories(settingsFile.getParent());
            Files.writeString(settingsFile, "{\"defaultModel\":\"foo\"}");
            SettingsManager mgr = new SettingsManager();
            mgr.setWorkingDir(tmp);
            mgr.setProject("theme", "light");
            String content = Files.readString(settingsFile);
            assertThat(content).contains("defaultModel").contains("theme");
        }

        @Test
        void corruptProjectFileTreatedAsEmpty(@TempDir Path tmp) throws IOException {
            Path settingsFile = tmp.resolve(".campusclaw").resolve("settings.json");
            Files.createDirectories(settingsFile.getParent());
            Files.writeString(settingsFile, "not json");
            SettingsManager mgr = new SettingsManager();
            mgr.setWorkingDir(tmp);
            Settings settings = mgr.load();
            assertThat(settings).isNotNull();
        }
    }

    @Nested
    class LoadGlobal {

        @Test
        void returnsNonNull() {
            // Cannot rewrite the static global path; just verify the method doesn't throw
            // and yields a Settings instance.
            assertThat(new SettingsManager().loadGlobal()).isNotNull();
        }
    }
}
