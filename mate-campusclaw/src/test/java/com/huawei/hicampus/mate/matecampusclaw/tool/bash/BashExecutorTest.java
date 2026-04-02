package com.huawei.hicampus.mate.matecampusclaw.codingagent.tool.bash;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;

import com.huawei.hicampus.mate.matecampusclaw.agent.tool.CancellationToken;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class BashExecutorTest {

    private final BashExecutor executor = new BashExecutor();

    @TempDir
    Path tempDir;

    private BashExecutorOptions defaultOptions() {
        return new BashExecutorOptions(Duration.ofSeconds(10), null, null);
    }

    // -------------------------------------------------------------------
    // Normal execution
    // -------------------------------------------------------------------

    @Nested
    class NormalExecution {

        @Test
        void capturesStdout() throws IOException {
            var result = executor.execute("echo hello", tempDir, defaultOptions());
            assertEquals(0, result.exitCode());
            assertEquals("hello\n", result.stdout());
            assertEquals("", result.stderr());
        }

        @Test
        void capturesStderr() throws IOException {
            var result = executor.execute("echo error >&2", tempDir, defaultOptions());
            assertEquals(0, result.exitCode());
            assertEquals("", result.stdout());
            assertEquals("error\n", result.stderr());
        }

        @Test
        void capturesStdoutAndStderrSeparately() throws IOException {
            var result = executor.execute("echo out && echo err >&2", tempDir, defaultOptions());
            assertEquals(0, result.exitCode());
            assertEquals("out\n", result.stdout());
            assertEquals("err\n", result.stderr());
        }

        @Test
        void usesWorkingDirectory() throws IOException {
            var result = executor.execute("pwd", tempDir, defaultOptions());
            assertEquals(0, result.exitCode());
            // On macOS, /var may symlink to /private/var, so just check the directory name
            assertTrue(result.stdout().trim().endsWith(tempDir.getFileName().toString()));
        }

        @Test
        void passesEnvironmentVariables() throws IOException {
            var options = new BashExecutorOptions(
                    Duration.ofSeconds(10), null, Map.of("MY_VAR", "value42"));
            var result = executor.execute("echo $MY_VAR", tempDir, options);
            assertEquals(0, result.exitCode());
            assertEquals("value42\n", result.stdout());
        }

        @Test
        void emptyCommand() throws IOException {
            var result = executor.execute("", tempDir, defaultOptions());
            assertEquals(0, result.exitCode());
        }

        @Test
        void multiLineOutput() throws IOException {
            var result = executor.execute("echo line1 && echo line2 && echo line3", tempDir, defaultOptions());
            assertEquals(0, result.exitCode());
            assertEquals("line1\nline2\nline3\n", result.stdout());
        }
    }

    // -------------------------------------------------------------------
    // Non-zero exit codes
    // -------------------------------------------------------------------

    @Nested
    class NonZeroExitCode {

        @Test
        void capturesNonZeroExitCode() throws IOException {
            var result = executor.execute("exit 42", tempDir, defaultOptions());
            assertEquals(42, result.exitCode());
        }

        @Test
        void capturesOutputBeforeFailure() throws IOException {
            var result = executor.execute("echo before && exit 1", tempDir, defaultOptions());
            assertEquals(1, result.exitCode());
            assertEquals("before\n", result.stdout());
        }

        @Test
        void capturesStderrOnFailure() throws IOException {
            var result = executor.execute("echo fail >&2 && exit 2", tempDir, defaultOptions());
            assertEquals(2, result.exitCode());
            assertEquals("fail\n", result.stderr());
        }
    }

    // -------------------------------------------------------------------
    // Timeout
    // -------------------------------------------------------------------

    @Nested
    class Timeout {

        @Test
        void timeoutReturnsNullExitCode() throws IOException {
            var options = new BashExecutorOptions(Duration.ofMillis(500), null, null);
            var result = executor.execute("sleep 60", tempDir, options);
            assertNull(result.exitCode());
        }

        @Test
        void fastCommandDoesNotTimeout() throws IOException {
            var options = new BashExecutorOptions(Duration.ofSeconds(10), null, null);
            var result = executor.execute("echo fast", tempDir, options);
            assertEquals(0, result.exitCode());
            assertEquals("fast\n", result.stdout());
        }
    }

    // -------------------------------------------------------------------
    // Cancellation
    // -------------------------------------------------------------------

    @Nested
    class Cancellation {

        @Test
        void cancellationKillsProcess() throws IOException {
            var token = new CancellationToken();

            // Cancel after a short delay
            Thread.ofVirtual().start(() -> {
                try {
                    Thread.sleep(300);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                token.cancel();
            });

            var options = new BashExecutorOptions(Duration.ofSeconds(30), token, null);
            var result = executor.execute("sleep 60", tempDir, options);

            // Process was killed: exit code is non-zero or null
            assertTrue(result.exitCode() == null || result.exitCode() != 0);
        }

        @Test
        void alreadyCancelledTokenKillsImmediately() throws IOException {
            var token = new CancellationToken();
            token.cancel();

            var options = new BashExecutorOptions(Duration.ofSeconds(5), token, null);

            long start = System.nanoTime();
            var result = executor.execute("sleep 60", tempDir, options);
            long elapsed = Duration.ofNanos(System.nanoTime() - start).toMillis();

            // Should complete quickly since token is already cancelled
            assertTrue(elapsed < 3000, "Should finish fast, took " + elapsed + "ms");
            assertTrue(result.exitCode() == null || result.exitCode() != 0);
        }
    }

    // -------------------------------------------------------------------
    // BashExecutorOptions record
    // -------------------------------------------------------------------

    @Nested
    class OptionsTest {

        @Test
        void nullEnvBecomesEmptyMap() {
            var options = new BashExecutorOptions(null, null, null);
            assertNotNull(options.env());
            assertTrue(options.env().isEmpty());
        }

        @Test
        void envIsImmutableCopy() {
            var mutable = new java.util.HashMap<String, String>();
            mutable.put("K", "V");
            var options = new BashExecutorOptions(null, null, mutable);

            mutable.put("K2", "V2");
            assertFalse(options.env().containsKey("K2"));
            assertThrows(UnsupportedOperationException.class, () -> options.env().put("X", "Y"));
        }
    }
}
