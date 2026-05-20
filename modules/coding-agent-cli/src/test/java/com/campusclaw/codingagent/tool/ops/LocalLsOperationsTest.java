/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.campusclaw.codingagent.tool.ops;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LocalLsOperationsTest {

    @Test
    void listEmptyDirectory(@TempDir Path tmp) throws IOException {
        LocalLsOperations ops = new LocalLsOperations();
        assertThat(ops.list(tmp)).isEmpty();
    }

    @Test
    void listFilesAndDirectories(@TempDir Path tmp) throws IOException {
        Files.writeString(tmp.resolve("a.txt"), "hi");
        Files.createDirectories(tmp.resolve("sub"));
        LocalLsOperations ops = new LocalLsOperations();
        List<LsOperations.LsEntry> entries = ops.list(tmp);
        assertThat(entries).extracting(LsOperations.LsEntry::name).containsExactlyInAnyOrder("a.txt", "sub");
        assertThat(entries)
                .filteredOn(e -> e.name().equals("a.txt"))
                .first()
                .extracting(LsOperations.LsEntry::type)
                .isEqualTo("file");
        assertThat(entries)
                .filteredOn(e -> e.name().equals("sub"))
                .first()
                .extracting(LsOperations.LsEntry::type)
                .isEqualTo("directory");
    }

    @Test
    void listMissingThrows(@TempDir Path tmp) {
        LocalLsOperations ops = new LocalLsOperations();
        assertThatThrownBy(() -> ops.list(tmp.resolve("missing"))).isInstanceOf(IOException.class);
    }
}
