/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.hicampus.mate.matecampusclaw.codingagent.util;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Tests for {@link GitUtils}. Uses a real {@code git init} in a temporary
 * directory so each call exercises the actual {@code git} subprocess plumbing
 * rather than mocked output. If {@code git} is not on PATH the methods all
 * silently fall back to {@code Optional.empty()} — those branches are
 * covered by the {@code nonGitDirectory} test which always runs.
 */
class GitUtilsTest {

    @TempDir
    Path tempDir;

    @Test
    void nonGitDirectoryReturnsEmpty() {
        assertThat(GitUtils.isGitRepo(tempDir)).isFalse();
        assertThat(GitUtils.getCurrentBranch(tempDir)).isEmpty();
        assertThat(GitUtils.getHeadCommit(tempDir)).isEmpty();
        assertThat(GitUtils.getRepoRoot(tempDir)).isEmpty();
        assertThat(GitUtils.getRemoteUrl(tempDir)).isEmpty();
        assertThat(GitUtils.hasUncommittedChanges(tempDir)).isFalse();
        assertThat(GitUtils.getChangedFiles(tempDir)).isEmpty();
    }

    @Test
    void freshGitRepoReportsBranchAndCleanWorkingTree() throws Exception {
        if (!gitAvailable()) {
            return; // Skip silently — environment without git on PATH
        }
        runGit("init", "-b", "main");
        runGit("config", "user.email", "test@example.com");
        runGit("config", "user.name", "Test User");
        Files.writeString(tempDir.resolve("a.txt"), "hello", StandardCharsets.UTF_8);
        runGit("add", "a.txt");
        runGit("commit", "-m", "initial");

        assertThat(GitUtils.isGitRepo(tempDir)).isTrue();
        assertThat(GitUtils.getCurrentBranch(tempDir)).contains("main");
        assertThat(GitUtils.getHeadCommit(tempDir)).isPresent();
        assertThat(GitUtils.getRepoRoot(tempDir)).isPresent();
        assertThat(GitUtils.getRepoRoot(tempDir).get().toRealPath()).isEqualTo(tempDir.toRealPath());
        assertThat(GitUtils.getUserName(tempDir)).contains("Test User");
        assertThat(GitUtils.hasUncommittedChanges(tempDir)).isFalse();
        assertThat(GitUtils.getChangedFiles(tempDir)).isEmpty();
        assertThat(GitUtils.getRemoteUrl(tempDir)).isEmpty();
    }

    @Test
    void detectsUncommittedChanges() throws Exception {
        if (!gitAvailable()) {
            return;
        }
        runGit("init", "-b", "main");
        runGit("config", "user.email", "test@example.com");
        runGit("config", "user.name", "Test User");
        Files.writeString(tempDir.resolve("a.txt"), "hello", StandardCharsets.UTF_8);
        runGit("add", "a.txt");
        runGit("commit", "-m", "initial");
        Files.writeString(tempDir.resolve("a.txt"), "modified", StandardCharsets.UTF_8);

        assertThat(GitUtils.hasUncommittedChanges(tempDir)).isTrue();
        assertThat(GitUtils.getChangedFiles(tempDir)).contains("a.txt");
    }

    @Test
    void getRemoteUrlReturnsConfiguredOriginUrl() throws Exception {
        if (!gitAvailable()) {
            return;
        }
        runGit("init", "-b", "main");
        runGit("remote", "add", "origin", "https://example.com/repo.git");
        assertThat(GitUtils.getRemoteUrl(tempDir)).contains("https://example.com/repo.git");
    }

    private boolean gitAvailable() {
        try {
            Process p = new ProcessBuilder("git", "--version").start();
            return p.waitFor() == 0;
        } catch (Exception e) {
            return false;
        }
    }

    private void runGit(String... args) throws Exception {
        var command = new java.util.ArrayList<String>();
        command.add("git");
        for (String a : args) {
            command.add(a);
        }
        Process p = new ProcessBuilder(command)
                .directory(tempDir.toFile())
                .redirectErrorStream(true)
                .start();
        p.getInputStream().readAllBytes();
        p.waitFor();
    }
}
