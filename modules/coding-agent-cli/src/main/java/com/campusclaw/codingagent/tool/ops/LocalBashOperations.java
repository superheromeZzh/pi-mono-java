package com.campusclaw.codingagent.tool.ops;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

/**
 * Local shell implementation of {@link BashOperations}.
 * Executes commands via {@code bash -c}; resolves {@code bash} from PATH on Windows.
 */
public class LocalBashOperations implements BashOperations {

    @Override
    public BashResult exec(String command, Path cwd, BashExecOptions options) throws IOException {
        boolean windows = System.getProperty("os.name", "").toLowerCase().contains("win");
        String bashCmd = windows ? "bash" : "/bin/bash";
        ProcessBuilder pb = new ProcessBuilder(bashCmd, "-c", command);
        pb.directory(cwd.toFile());
        pb.redirectErrorStream(true);

        if (options.env() != null && !options.env().isEmpty()) {
            pb.environment().putAll(options.env());
        }

        Process process = pb.start();

        // Register cancellation callback to destroy the process
        if (options.signal() != null) {
            options.signal().onCancel(process::destroyForcibly);
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
                    process.destroyForcibly();
                    process.waitFor(5, TimeUnit.SECONDS);
                    return new BashResult(null);
                }
            } else {
                process.waitFor();
            }
        } catch (InterruptedException e) {
            process.destroyForcibly();
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
}
