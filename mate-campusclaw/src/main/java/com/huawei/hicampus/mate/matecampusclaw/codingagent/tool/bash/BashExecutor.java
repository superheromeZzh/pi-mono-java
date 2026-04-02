package com.huawei.hicampus.mate.matecampusclaw.codingagent.tool.bash;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

import org.springframework.stereotype.Service;

/**
 * Execution engine for bash commands, independent of the Bash tool.
 * Manages process lifecycle, timeout, cancellation, and separate stdout/stderr capture.
 */
@Service
public class BashExecutor {

    /**
     * Executes a bash command and captures its output.
     *
     * @param command the shell command to execute
     * @param cwd     the working directory
     * @param options execution options (timeout, cancellation, env)
     * @return the execution result with exit code, stdout, and stderr
     * @throws IOException if the process cannot be started
     */
    public BashExecutionResult execute(String command, Path cwd, BashExecutorOptions options) throws IOException {
        ProcessBuilder pb = new ProcessBuilder("/bin/bash", "-c", command);
        pb.directory(cwd.toFile());
        // Redirect stdin from /dev/null so the child bash doesn't steal
        // the parent's terminal input, which can break JLine's reader.
        pb.redirectInput(ProcessBuilder.Redirect.from(new java.io.File("/dev/null")));

        if (!options.env().isEmpty()) {
            pb.environment().putAll(options.env());
        }

        Process process = pb.start();

        if (options.signal() != null) {
            options.signal().onCancel(process::destroyForcibly);
        }

        // Drain stdout and stderr on virtual threads to prevent blocking
        var stdoutBuf = new ByteArrayOutputStream();
        var stderrBuf = new ByteArrayOutputStream();

        Thread stdoutDrainer = Thread.ofVirtual()
                .name("bash-stdout-drainer")
                .start(() -> drain(process.getInputStream(), stdoutBuf));
        Thread stderrDrainer = Thread.ofVirtual()
                .name("bash-stderr-drainer")
                .start(() -> drain(process.getErrorStream(), stderrBuf));

        boolean timedOut = false;
        try {
            if (options.timeout() != null) {
                boolean finished = process.waitFor(options.timeout().toMillis(), TimeUnit.MILLISECONDS);
                if (!finished) {
                    timedOut = true;
                    process.destroyForcibly();
                    process.waitFor(5, TimeUnit.SECONDS);
                }
            } else {
                process.waitFor();
            }
        } catch (InterruptedException e) {
            process.destroyForcibly();
            Thread.currentThread().interrupt();
            joinDrainers(stdoutDrainer, stderrDrainer);
            return new BashExecutionResult(null,
                    stdoutBuf.toString(StandardCharsets.UTF_8),
                    stderrBuf.toString(StandardCharsets.UTF_8));
        }

        joinDrainers(stdoutDrainer, stderrDrainer);

        Integer exitCode = timedOut ? null : process.exitValue();
        return new BashExecutionResult(
                exitCode,
                stdoutBuf.toString(StandardCharsets.UTF_8),
                stderrBuf.toString(StandardCharsets.UTF_8)
        );
    }

    private static void drain(InputStream is, ByteArrayOutputStream out) {
        try (is) {
            byte[] buffer = new byte[4096];
            int bytesRead;
            while ((bytesRead = is.read(buffer)) != -1) {
                out.write(buffer, 0, bytesRead);
            }
        } catch (IOException ignored) {
            // Stream closed due to process destruction — expected on timeout/cancel
        }
    }

    private static void joinDrainers(Thread stdout, Thread stderr) {
        try {
            stdout.join(5000);
            stderr.join(5000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
