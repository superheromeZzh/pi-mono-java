package com.huawei.hicampus.mate.matecampusclaw.codingagent.util;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Git integration utilities for detecting branch name, uncommitted changes,
 * and other repository state information.
 */
public final class GitUtils {

    private static final Logger log = LoggerFactory.getLogger(GitUtils.class);
    private static final long TIMEOUT_SECONDS = 5;

    private GitUtils() {}

    /**
     * Returns the current git branch name, or empty if not in a git repo.
     */
    public static Optional<String> getCurrentBranch(Path workDir) {
        return runGit(workDir, "rev-parse", "--abbrev-ref", "HEAD")
            .map(String::trim)
            .filter(s -> !s.isEmpty());
    }

    /**
     * Returns true if the working tree has uncommitted changes.
     */
    public static boolean hasUncommittedChanges(Path workDir) {
        return runGit(workDir, "status", "--porcelain")
            .map(output -> !output.trim().isEmpty())
            .orElse(false);
    }

    /**
     * Returns true if the given path is inside a git repository.
     */
    public static boolean isGitRepo(Path workDir) {
        return runGit(workDir, "rev-parse", "--is-inside-work-tree")
            .map(output -> "true".equals(output.trim()))
            .orElse(false);
    }

    /**
     * Returns the root directory of the git repository.
     */
    public static Optional<Path> getRepoRoot(Path workDir) {
        return runGit(workDir, "rev-parse", "--show-toplevel")
            .map(String::trim)
            .filter(s -> !s.isEmpty())
            .map(Path::of);
    }

    /**
     * Returns the short commit hash of HEAD.
     */
    public static Optional<String> getHeadCommit(Path workDir) {
        return runGit(workDir, "rev-parse", "--short", "HEAD")
            .map(String::trim)
            .filter(s -> !s.isEmpty());
    }

    /**
     * Returns a list of changed file paths (staged and unstaged).
     */
    public static List<String> getChangedFiles(Path workDir) {
        return runGit(workDir, "diff", "--name-only", "HEAD")
            .map(output -> output.lines().filter(l -> !l.isBlank()).toList())
            .orElse(List.of());
    }

    /**
     * Returns the configured user name from git config.
     */
    public static Optional<String> getUserName(Path workDir) {
        return runGit(workDir, "config", "user.name")
            .map(String::trim)
            .filter(s -> !s.isEmpty());
    }

    /**
     * Returns the remote URL for 'origin'.
     */
    public static Optional<String> getRemoteUrl(Path workDir) {
        return runGit(workDir, "remote", "get-url", "origin")
            .map(String::trim)
            .filter(s -> !s.isEmpty());
    }

    /**
     * Runs a git command and returns its stdout, or empty on failure.
     */
    private static Optional<String> runGit(Path workDir, String... args) {
        try {
            var command = new String[args.length + 1];
            command[0] = "git";
            System.arraycopy(args, 0, command, 1, args.length);

            var pb = new ProcessBuilder(command)
                .directory(workDir.toFile())
                .redirectErrorStream(false);

            var process = pb.start();
            var stdout = new String(process.getInputStream().readAllBytes());
            boolean completed = process.waitFor(TIMEOUT_SECONDS, TimeUnit.SECONDS);

            if (!completed) {
                process.destroyForcibly();
                return Optional.empty();
            }

            return process.exitValue() == 0 ? Optional.of(stdout) : Optional.empty();
        } catch (IOException | InterruptedException e) {
            log.debug("Git command failed: {}", e.getMessage());
            return Optional.empty();
        }
    }
}
