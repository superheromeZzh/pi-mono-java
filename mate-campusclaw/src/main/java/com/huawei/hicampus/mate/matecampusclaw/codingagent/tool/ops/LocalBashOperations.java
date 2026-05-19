/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.hicampus.mate.matecampusclaw.codingagent.tool.ops;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import com.huawei.hicampus.mate.matecampusclaw.agent.util.LoggingUncaughtExceptionHandler;
import com.huawei.hicampus.mate.matecampusclaw.codingagent.tool.bash.ShellResolver;

/**
 * Local shell implementation of {@link BashOperations}.
 * Executes commands via {@code bash -c}; shell discovery is delegated to
 * {@link ShellResolver} so that Git Bash on Windows is found even when
 * {@code bash.exe} is not on PATH.
 *
 * @version [br_eCampusCore 25.1.0_Next, 2026/05/06]
 * @since [br_eCampusCore 25.1.0_Next]
 */
public class LocalBashOperations implements BashOperations {

    @Override
    public BashResult exec(String command, Path cwd, BashExecOptions options) throws IOException {
        ProcessBuilder pb = buildProcessBuilder(command, cwd, options);
        Process process = pb.start();
        if (options.signal() != null) {
            options.signal().onCancel(() -> killProcessTree(process));
        }
        Thread drainer = startOutputDrainer(process, options);
        BashResult earlyResult = awaitProcess(process, options);
        if (earlyResult != null) {
            return earlyResult;
        }
        try {
            drainer.join(5000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        return new BashResult(process.exitValue());
    }

    private static ProcessBuilder buildProcessBuilder(String command, Path cwd, BashExecOptions options) {
        ShellResolver.ShellConfig shell = ShellResolver.resolve();
        List<String> argv = new ArrayList<>(shell.args().size() + 2);
        argv.add(shell.shell());
        argv.addAll(shell.args());
        argv.add(command);
        ProcessBuilder pb = new ProcessBuilder(argv);
        pb.directory(cwd.toFile());
        pb.redirectErrorStream(true);
        if (options.env() != null && !options.env().isEmpty()) {
            pb.environment().putAll(options.env());
        }
        return pb;
    }

    // Drain stdout/stderr in a background thread so waitFor() with a timeout
    // can fire without the child blocking on a full pipe buffer.
    private static Thread startOutputDrainer(Process process, BashExecOptions options) {
        Thread drainer = new Thread(
                () -> {
                    try (InputStream is = process.getInputStream()) {
                        byte[] buffer = new byte[4096];
                        int bytesRead;
                        while ((bytesRead = is.read(buffer)) != -1) {
                            if (options.onData() != null) {
                                byte[] chunk = new byte[bytesRead];
                                System.arraycopy(buffer, 0, chunk, 0, bytesRead);
                                options.onData().accept(chunk);
                            }
                        }
                    } catch (IOException ignored) {
                        // Stream closed due to process destruction — expected on timeout/cancel.
                    }
                },
                "bash-output-drainer");
        drainer.setDaemon(true);
        drainer.setUncaughtExceptionHandler(LoggingUncaughtExceptionHandler.INSTANCE);
        drainer.start();
        return drainer;
    }

    // Returns a sentinel BashResult(null) if the process timed out or was
    // interrupted; null when waitFor() completed normally and the caller
    // should read the exit code.
    private static BashResult awaitProcess(Process process, BashExecOptions options) {
        try {
            if (options.timeout() != null) {
                boolean finished = process.waitFor(options.timeout().toMillis(), TimeUnit.MILLISECONDS);
                if (!finished) {
                    killProcessTree(process);
                    process.waitFor(5, TimeUnit.SECONDS);
                    return new BashResult(null);
                }
            } else {
                process.waitFor();
            }
            return null;
        } catch (InterruptedException e) {
            killProcessTree(process);
            Thread.currentThread().interrupt();
            return new BashResult(null);
        }
    }

    private static void killProcessTree(Process process) {
        process.descendants().forEach(ProcessHandle::destroyForcibly);
        process.destroyForcibly();
    }
}
