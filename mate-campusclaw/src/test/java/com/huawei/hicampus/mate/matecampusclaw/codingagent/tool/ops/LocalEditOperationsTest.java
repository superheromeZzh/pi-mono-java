/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.hicampus.mate.matecampusclaw.codingagent.tool.ops;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LocalEditOperationsTest {

    @Test
    void readWriteRoundtrip(@TempDir Path tmp) throws IOException {
        LocalEditOperations ops = new LocalEditOperations();
        Path file = tmp.resolve("a.txt");
        ops.writeFile(file, "hello");
        assertThat(new String(ops.readFile(file), StandardCharsets.UTF_8)).isEqualTo("hello");
    }

    @Test
    void writeFileCreatesParentDirs(@TempDir Path tmp) throws IOException {
        LocalEditOperations ops = new LocalEditOperations();
        Path nested = tmp.resolve("a/b/c.txt");
        ops.writeFile(nested, "deep");
        assertThat(Files.exists(nested)).isTrue();
    }

    @Test
    void exists(@TempDir Path tmp) throws IOException {
        LocalEditOperations ops = new LocalEditOperations();
        Path file = tmp.resolve("x.txt");
        assertThat(ops.exists(file)).isFalse();
        Files.writeString(file, "x");
        assertThat(ops.exists(file)).isTrue();
    }

    @Test
    void detectMimeTypeProbesContent(@TempDir Path tmp) throws IOException {
        LocalEditOperations ops = new LocalEditOperations();
        Path file = tmp.resolve("a.txt");
        Files.writeString(file, "hi");

        // probeContentType may return null on some FSes; just verify call doesn't throw
        ops.detectMimeType(file);
    }

    @Test
    void mkdirCreatesNested(@TempDir Path tmp) throws IOException {
        LocalEditOperations ops = new LocalEditOperations();
        Path dir = tmp.resolve("a/b/c");
        ops.mkdir(dir);
        assertThat(Files.isDirectory(dir)).isTrue();
    }

    @Test
    void readMissingThrows(@TempDir Path tmp) {
        LocalEditOperations ops = new LocalEditOperations();
        assertThatThrownBy(() -> ops.readFile(tmp.resolve("missing"))).isInstanceOf(IOException.class);
    }
}
