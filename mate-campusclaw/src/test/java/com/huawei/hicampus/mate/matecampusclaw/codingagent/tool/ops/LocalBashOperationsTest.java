package com.huawei.hicampus.mate.matecampusclaw.codingagent.tool.ops;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import com.huawei.hicampus.mate.matecampusclaw.agent.tool.CancellationToken;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LocalBashOperationsTest {

    private final LocalBashOperations ops = new LocalBashOperations();

    @TempDir
    Path tempDir;

    private BashExecOptions defaultOptions() {
        return new BashExecOptions(null, null, Duration.ofSeconds(10), null);
    }

    @Nested
    class Exec {

        @Test
        void simpleEchoCommand() throws IOException {
            var collected = new AtomicReference<String>("");
            var options = new BashExecOptions(
                    data -> collected.updateAndGet(prev -> prev + new String(data, StandardCharsets.UTF_8)),
                    null, Duration.ofSeconds(10), null
            );

            BashResult result = ops.exec("echo hello", tempDir, options);

            assertEquals(0, result.exitCode());
            assertTrue(collected.get().trim().contains("hello"));
        }

        @Test
        void capturesExitCode() throws IOException {
            BashResult result = ops.exec("exit 42", tempDir, defaultOptions());
            assertEquals(42, result.exitCode());
        }

        @Test
        void usesWorkingDirectory() throws IOException {
            var collected = new AtomicReference<String>("");
            var options = new BashExecOptions(
                    data -> collected.updateAndGet(prev -> prev + new String(data, StandardCharsets.UTF_8)),
                    null, Duration.ofSeconds(10), null
            );

            ops.exec("pwd", tempDir, options);

            // The real path may differ from tempDir due to symlinks (e.g. /private/var vs /var on macOS)
            String output = collected.get().trim();
            assertTrue(output.endsWith(tempDir.getFileName().toString()));
        }

        @Test
        void passesEnvironmentVariables() throws IOException {
            var collected = new AtomicReference<String>("");
            var options = new BashExecOptions(
                    data -> collected.updateAndGet(prev -> prev + new String(data, StandardCharsets.UTF_8)),
                    null, Duration.ofSeconds(10),
                    Map.of("MY_TEST_VAR", "test_value_123")
            );

            ops.exec("echo $MY_TEST_VAR", tempDir, options);
            assertTrue(collected.get().trim().contains("test_value_123"));
        }

        @Test
        void timeoutKillsLongRunningProcess() throws IOException {
            BashResult result = ops.exec("sleep 60", tempDir,
                    new BashExecOptions(null, null, Duration.ofMillis(500), null));
            // Timeout should kill the process, returning null exit code
            assertNull(result.exitCode());
        }

        @Test
        void cancellationKillsProcess() throws IOException {
            var token = new CancellationToken();

            // Cancel after a short delay in a separate thread
            new Thread(() -> {
                try {
                    Thread.sleep(300);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                token.cancel();
            }).start();

            BashResult result = ops.exec("sleep 60", tempDir,
                    new BashExecOptions(null, token, Duration.ofSeconds(30), null));

            // Process should have been killed; exit code is non-zero or null
            // destroyForcibly returns 137 (SIGKILL) on most Unix systems
            assertTrue(result.exitCode() == null || result.exitCode() != 0);
        }

        @Test
        void capturesStderrViaMergedStream() throws IOException {
            var collected = new AtomicReference<String>("");
            var options = new BashExecOptions(
                    data -> collected.updateAndGet(prev -> prev + new String(data, StandardCharsets.UTF_8)),
                    null, Duration.ofSeconds(10), null
            );

            ops.exec("echo error_msg >&2", tempDir, options);
            assertTrue(collected.get().contains("error_msg"));
        }

        @Test
        void nullOnDataCallbackIsHandled() throws IOException {
            // Should not throw when onData is null
            BashResult result = ops.exec("echo ok", tempDir, defaultOptions());
            assertEquals(0, result.exitCode());
        }
    }

    @Nested
    class BashExecOptionsTest {

        @Test
        void nullEnvBecomesEmptyMap() {
            var options = new BashExecOptions(null, null, null, null);
            assertNotNull(options.env());
            assertTrue(options.env().isEmpty());
        }

        @Test
        void envIsImmutableCopy() {
            var mutable = new java.util.HashMap<String, String>();
            mutable.put("KEY", "VAL");
            var options = new BashExecOptions(null, null, null, mutable);

            // Modifying original should not affect the record
            mutable.put("KEY2", "VAL2");
            assertFalse(options.env().containsKey("KEY2"));

            // Record's map should be immutable
            assertThrows(UnsupportedOperationException.class, () ->
                    options.env().put("NEW", "VALUE"));
        }
    }

    @Nested
    class BashResultTest {

        @Test
        void exitCodePresent() {
            var result = new BashResult(0);
            assertEquals(0, result.exitCode());
        }

        @Test
        void exitCodeNull() {
            var result = new BashResult(null);
            assertNull(result.exitCode());
        }
    }
}
