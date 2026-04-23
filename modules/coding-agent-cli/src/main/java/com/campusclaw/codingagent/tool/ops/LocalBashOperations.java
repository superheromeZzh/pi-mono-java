package com.campusclaw.codingagent.tool.ops;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import com.campusclaw.codingagent.tool.bash.ShellResolver;

/**
 * Local shell implementation of {@link BashOperations}.
 * Executes commands via {@code bash -c}; shell discovery is delegated to
 * {@link ShellResolver} so that Git Bash on Windows is found even when
 * {@code bash.exe} is not on PATH.
 */
public class LocalBashOperations implements BashOperations {

    @Override
    public BashResult exec(String command, Path cwd, BashExecOptions options) throws IOException {
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

        Process process = pb.start();

        // Register cancellation callback to destroy the entire process tree
        if (options.signal() != null) {
            options.signal().onCancel(() -> killProcessTree(process));
        }

        // Drain stdout/stderr in a background thread so waitFor timeout can fire
        Thread drainer = new Thread(() -> {
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
                // Stream closed due to process destruction — expected on timeout/cancel
            }
        }, "bash-output-drainer");
        drainer.setDaemon(true);
        drainer.start();

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
        } catch (InterruptedException e) {
            killProcessTree(process);
            Thread.currentThread().interrupt();
            return new BashResult(null);
        }

        // Wait for drainer to finish reading remaining output
        try {
            drainer.join(5000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        return new BashResult(process.exitValue());
    }

    private static void killProcessTree(Process process) {
        process.descendants().forEach(ProcessHandle::destroyForcibly);
        process.destroyForcibly();
    }
}
