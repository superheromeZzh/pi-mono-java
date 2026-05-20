/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.hicampus.mate.matecampusclaw.codingagent.skill;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SkillStateStoreTest {

    @Test
    void loadEmptyWhenFileMissing(@TempDir Path tmp) {
        SkillStateStore store = new SkillStateStore(tmp);
        assertThat(store.loadDisabled()).isEmpty();
    }

    @Test
    void loadMalformedReturnsEmpty(@TempDir Path tmp) throws IOException {
        Files.writeString(tmp.resolve(".disabled.json"), "not json");
        SkillStateStore store = new SkillStateStore(tmp);
        assertThat(store.loadDisabled()).isEmpty();
    }

    @Test
    void disableAddsToFile(@TempDir Path tmp) {
        SkillStateStore store = new SkillStateStore(tmp);
        store.disable("a");
        store.disable("b");
        assertThat(store.loadDisabled()).containsExactlyInAnyOrder("a", "b");
        assertThat(store.isDisabled("a")).isTrue();
        assertThat(store.isDisabled("c")).isFalse();
    }

    @Test
    void enableRemovesFromFile(@TempDir Path tmp) {
        SkillStateStore store = new SkillStateStore(tmp);
        store.disable("a");
        store.enable("a");
        assertThat(store.isDisabled("a")).isFalse();
    }

    @Test
    void disableIdempotent(@TempDir Path tmp) {
        SkillStateStore store = new SkillStateStore(tmp);
        store.disable("a");
        store.disable("a");
        assertThat(store.loadDisabled()).containsExactly("a");
    }

    @Test
    void enableMissingIdempotent(@TempDir Path tmp) {
        SkillStateStore store = new SkillStateStore(tmp);
        store.enable("never-disabled");
        assertThat(store.loadDisabled()).isEmpty();
    }

    @Test
    void filePersisted(@TempDir Path tmp) {
        SkillStateStore store = new SkillStateStore(tmp);
        store.disable("a");

        // New instance should see the state
        SkillStateStore other = new SkillStateStore(tmp);
        assertThat(other.isDisabled("a")).isTrue();
    }
}
