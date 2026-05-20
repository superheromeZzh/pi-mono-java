/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.campusclaw.agent.subagent.acp.backend;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assumptions.assumeThat;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.file.Paths;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import com.campusclaw.agent.subagent.SubAgentBackend;
import com.campusclaw.agent.subagent.SubAgentEvent;
import com.campusclaw.agent.subagent.SubAgentException;
import com.campusclaw.agent.subagent.SubAgentSession;
import com.campusclaw.agent.subagent.SubAgentSessionKey;
import com.campusclaw.agent.subagent.approval.ApprovalClassifier;
import com.campusclaw.agent.subagent.approval.ApprovalDecision;
import com.campusclaw.agent.subagent.approval.ApprovalPolicy;
import com.campusclaw.agent.subagent.approval.ParentPermissionDecision;
import com.campusclaw.agent.subagent.approval.ParentPermissionResolver;
import com.campusclaw.agent.tool.CancellationToken;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link ProcessAcpBackend}.
 *
 * <p>The class spawns child processes and speaks ACP over stdio — most of the runtime paths
 * (drainer, destroy-tree, enrich, init-timeout) can be exercised on Unix by routing through
 * {@code /bin/cat}: cat echoes the framed JSON-RPC request back instead of returning a valid
 * response, so {@link ProcessAcpBackend#open} reliably hits its {@code RuntimeException} branch
 * and the cleanup path runs. The Windows-specific {@code cmd.exe /c} wrapper branch is not
 * unit-testable on non-Windows runners and is asserted only by the os.name guards inside the SUT.
 */
class ProcessAcpBackendTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase(Locale.ROOT).startsWith("windows");
    }

    private static ProcessAcpBackend.Config catConfig() {
        // /bin/cat → reads stdin, echoes to stdout; ACP initialize will block until timeout.
        return new ProcessAcpBackend.Config(
                "/bin/cat", List.of(), Map.of("ACP_PROCESS_TEST", "1"), "test-client", "9.9.9", Duration.ofSeconds(2L));
    }

    private static ProcessAcpBackend.Config bogusConfig() {
        return new ProcessAcpBackend.Config(
                "/nonexistent/definitely-not-a-real-binary-" + System.nanoTime(),
                List.of(),
                Map.of(),
                "test-client",
                "1.0.0",
                Duration.ofSeconds(2L));
    }

    private static ProcessAcpBackend backendWith(ProcessAcpBackend.Config cfg) {
        return new ProcessAcpBackend("test-backend", cfg, MAPPER, null, null, null);
    }

    // ----------------------------------------------------------------------
    // Constructor validation
    // ----------------------------------------------------------------------

    @Nested
    class ConstructorValidation {

        @Test
        void testRejectsNullId() {
            IllegalArgumentException ex = assertThrows(
                    IllegalArgumentException.class,
                    () -> new ProcessAcpBackend(null, catConfig(), MAPPER, null, null, null));
            assertThat(ex.getMessage()).contains("id must not be blank");
        }

        @Test
        void testRejectsBlankId() {
            IllegalArgumentException ex = assertThrows(
                    IllegalArgumentException.class,
                    () -> new ProcessAcpBackend("  ", catConfig(), MAPPER, null, null, null));
            assertThat(ex.getMessage()).contains("id must not be blank");
        }

        @Test
        void testIdGetterReturnsConstructorValue() {
            var backend = backendWith(catConfig());
            assertThat(backend.id()).isEqualTo("test-backend");
        }
    }

    // ----------------------------------------------------------------------
    // Config record validation
    // ----------------------------------------------------------------------

    @Nested
    class ConfigValidation {

        @Test
        void testRejectsNullCommand() {
            IllegalArgumentException ex = assertThrows(
                    IllegalArgumentException.class,
                    () -> new ProcessAcpBackend.Config(null, List.of(), Map.of(), "n", "v", Duration.ofSeconds(1L)));
            assertThat(ex.getMessage()).contains("command must not be blank");
        }

        @Test
        void testRejectsBlankCommand() {
            assertThrows(
                    IllegalArgumentException.class,
                    () -> new ProcessAcpBackend.Config("   ", List.of(), Map.of(), "n", "v", Duration.ofSeconds(1L)));
        }

        @Test
        void testNullArgsBecomesEmptyImmutableList() {
            var cfg = new ProcessAcpBackend.Config("cmd", null, Map.of(), "n", "v", Duration.ofSeconds(1L));
            assertThat(cfg.args()).isEmpty();
            assertThrows(UnsupportedOperationException.class, () -> cfg.args().add("x"));
        }

        @Test
        void testNullEnvBecomesEmptyImmutableMap() {
            var cfg = new ProcessAcpBackend.Config("cmd", List.of(), null, "n", "v", Duration.ofSeconds(1L));
            assertThat(cfg.env()).isEmpty();
            assertThrows(UnsupportedOperationException.class, () -> cfg.env().put("k", "v"));
        }

        @Test
        void testBlankClientNameFallsBackToCampusclaw() {
            var cfg = new ProcessAcpBackend.Config("cmd", List.of(), Map.of(), "  ", "v", Duration.ofSeconds(1L));
            assertThat(cfg.clientName()).isEqualTo("campusclaw");
        }

        @Test
        void testNullClientNameFallsBackToCampusclaw() {
            var cfg = new ProcessAcpBackend.Config("cmd", List.of(), Map.of(), null, "v", Duration.ofSeconds(1L));
            assertThat(cfg.clientName()).isEqualTo("campusclaw");
        }

        @Test
        void testBlankClientVersionFallsBackTo100() {
            var cfg = new ProcessAcpBackend.Config("cmd", List.of(), Map.of(), "n", "", Duration.ofSeconds(1L));
            assertThat(cfg.clientVersion()).isEqualTo("1.0.0");
        }

        @Test
        void testNullClientVersionFallsBackTo100() {
            var cfg = new ProcessAcpBackend.Config("cmd", List.of(), Map.of(), "n", null, Duration.ofSeconds(1L));
            assertThat(cfg.clientVersion()).isEqualTo("1.0.0");
        }

        @Test
        void testNullPromptTimeoutFallsBackToTenMinutes() {
            var cfg = new ProcessAcpBackend.Config("cmd", List.of(), Map.of(), "n", "v", null);
            assertThat(cfg.promptTimeout()).isEqualTo(Duration.ofMinutes(10L));
        }

        @Test
        void testPreservesExplicitValues() {
            var cfg = new ProcessAcpBackend.Config(
                    "claude",
                    List.of("--acp", "--verbose"),
                    Map.of("KEY", "VAL"),
                    "custom-name",
                    "2.5.0",
                    Duration.ofMinutes(7L));
            assertThat(cfg.command()).isEqualTo("claude");
            assertThat(cfg.args()).containsExactly("--acp", "--verbose");
            assertThat(cfg.env()).containsEntry("KEY", "VAL");
            assertThat(cfg.clientName()).isEqualTo("custom-name");
            assertThat(cfg.clientVersion()).isEqualTo("2.5.0");
            assertThat(cfg.promptTimeout()).isEqualTo(Duration.ofMinutes(7L));
        }
    }

    // ----------------------------------------------------------------------
    // Operations on a session that was never opened (cancel/close/prompt/clientFor)
    // ----------------------------------------------------------------------

    @Nested
    class NoSessionOpened {

        private final ProcessAcpBackend backend = backendWith(catConfig());

        private SubAgentSession syntheticSession() {
            // Build a session whose key is NOT registered in the backend's handles map.
            SubAgentSessionKey key = SubAgentSessionKey.newKey("parent", "test-backend");
            return new SubAgentSession(key, "remote-id", backend);
        }

        @Test
        void testCancelOnUnknownSessionIsNoOp() {
            // handles map is empty — handle lookup returns null and the method short-circuits.
            SubAgentSession session = syntheticSession();
            assertDoesNotThrow(() -> backend.cancel(session, "test"));
        }

        @Test
        void testCloseOnUnknownSessionIsNoOp() {
            // Same: missing handle → early return without touching any process state.
            SubAgentSession session = syntheticSession();
            assertDoesNotThrow(() -> backend.close(session, "test"));
        }

        @Test
        void testPromptOnUnknownSessionThrowsAcpSessionGone() {
            SubAgentSession session = syntheticSession();
            SubAgentException ex = assertThrows(SubAgentException.class, () -> backend.prompt(session, "hello", null));
            assertThat(ex.code()).isEqualTo("ACP_SESSION_GONE");
        }

        @Test
        void testClientForUnknownSessionThrowsAcpSessionGone() {
            SubAgentSession session = syntheticSession();
            SubAgentException ex = assertThrows(SubAgentException.class, () -> backend.clientFor(session));
            assertThat(ex.code()).isEqualTo("ACP_SESSION_GONE");
        }
    }

    // ----------------------------------------------------------------------
    // open() spawn-failure path: bogus command → IOException → ACP_SPAWN_FAILED
    // ----------------------------------------------------------------------

    @Nested
    class SpawnFailures {

        @Test
        void testUnknownCommandThrowsAcpSpawnFailed() {
            ProcessAcpBackend backend = backendWith(bogusConfig());
            var req = new SubAgentBackend.OpenRequest("parent", null, null, null, Map.of(), Duration.ofSeconds(1L));

            SubAgentException ex = assertThrows(SubAgentException.class, () -> backend.open(req));
            assertThat(ex.code()).isEqualTo("ACP_SPAWN_FAILED");
            assertThat(ex.getMessage()).contains("failed to launch");
            assertThat(ex.getCause()).isInstanceOf(java.io.IOException.class);
        }
    }

    // ----------------------------------------------------------------------
    // open() init-timeout path on Unix: /bin/cat echoes frames, initialize never
    // resolves, the open() RuntimeException branch runs destroyTree + enrich +
    // drainStderr + safeExitCode.
    // ----------------------------------------------------------------------

    @Nested
    class OpenInitTimeoutWithCat {

        @Test
        void testOpenTimesOutAndCleansUpChildProcess() {
            assumeThat(isWindows())
                    .as("This test relies on /bin/cat; skip on Windows")
                    .isFalse();

            ProcessAcpBackend backend = backendWith(catConfig());
            var req = new SubAgentBackend.OpenRequest(
                    "parent", null, null, null, Map.of("EXTRA_ENV", "v"), Duration.ofSeconds(1L));

            SubAgentException ex = assertThrows(SubAgentException.class, () -> backend.open(req));
            assertThat(ex.code()).isEqualTo("ACP_OPEN_FAILED");

            // Either "still alive" (parent process killed but exit code may race) or "exited
            // code=" — both branches of safeExitCode are valid here.
            assertThat(ex.getMessage()).containsAnyOf("still alive", "exited code=");
        }

        @Test
        void testOpenWithCwdRoutesToProcessBuilder() {
            assumeThat(isWindows()).isFalse();

            String cwd = System.getProperty("java.io.tmpdir");
            ProcessAcpBackend backend = backendWith(catConfig());
            var req = new SubAgentBackend.OpenRequest("parent", cwd, null, null, Map.of(), Duration.ofSeconds(1L));

            // Even with a valid cwd, /bin/cat doesn't speak ACP → initialize times out.
            SubAgentException ex = assertThrows(SubAgentException.class, () -> backend.open(req));
            assertThat(ex.code()).isEqualTo("ACP_OPEN_FAILED");
        }

        @Test
        void testOpenCapturesStderrTailWhenChildWritesToStderr() {
            assumeThat(isWindows()).isFalse();

            // /bin/sh -c 'echo error-line-1 >&2; echo error-line-2 >&2; exec cat'
            //   - prints two lines to stderr (drainer reads them into StderrTail)
            //   - then runs cat to keep the process alive until kill — initialize will time out
            ProcessAcpBackend.Config cfg = new ProcessAcpBackend.Config(
                    "/bin/sh",
                    List.of("-c", "echo error-line-1 >&2; echo error-line-2 >&2; exec cat"),
                    Map.of(),
                    "test-client",
                    "1.0.0",
                    Duration.ofSeconds(2L));
            ProcessAcpBackend backend = backendWith(cfg);
            var req = new SubAgentBackend.OpenRequest("parent", null, null, null, Map.of(), Duration.ofSeconds(1L));

            SubAgentException ex = assertThrows(SubAgentException.class, () -> backend.open(req));
            assertThat(ex.code()).isEqualTo("ACP_OPEN_FAILED");

            // Stderr was drained and appended to the tail before enrich() ran.
            assertThat(ex.getMessage()).containsAnyOf("error-line-1", "error-line-2", "child stderr tail");
        }

        @Test
        void testOpenWithChildExitsImmediatelyReportsExitCode() {
            assumeThat(isWindows()).isFalse();

            // /bin/sh -c 'exit 7' → child dies immediately. initialize times out (or fails on
            // EOF), and enrich() reads exitValue() → safeExitCode returns "7".
            ProcessAcpBackend.Config cfg = new ProcessAcpBackend.Config(
                    "/bin/sh", List.of("-c", "exit 7"), Map.of(), "test-client", "1.0.0", Duration.ofSeconds(2L));
            ProcessAcpBackend backend = backendWith(cfg);
            var req = new SubAgentBackend.OpenRequest("parent", null, null, null, Map.of(), Duration.ofSeconds(1L));

            SubAgentException ex = assertThrows(SubAgentException.class, () -> backend.open(req));
            assertThat(ex.code()).isEqualTo("ACP_OPEN_FAILED");

            // safeExitCode now goes down its success branch (process.exitValue() returns 7
            // because the child has already terminated).
            assertThat(ex.getMessage()).contains("exited code=");
        }
    }

    // ----------------------------------------------------------------------
    // Wiring: backend accepts configured collaborators and null collaborators alike.
    // The permission-resolution branches inside open() lambda cannot be invoked without a
    // successful ACP handshake (which requires a real ACP server); we cover the constructor
    // wiring and let the existing AcpClient/Approval* tests cover the resolver logic itself.
    // ----------------------------------------------------------------------

    @Nested
    class WiringWithCollaborators {

        @Test
        void testBackendAcceptsConfiguredCollaborators() {
            ApprovalClassifier classifier = new ApprovalClassifier();
            ApprovalPolicy policy = (risk, tool) -> ApprovalDecision.AUTO_ALLOW;
            ParentPermissionResolver resolver = (req, timeout) -> ParentPermissionDecision.cancelled();

            ProcessAcpBackend backend =
                    new ProcessAcpBackend("with-collab", catConfig(), MAPPER, classifier, policy, resolver);

            assertThat(backend.id()).isEqualTo("with-collab");
        }

        @Test
        void testBackendAcceptsNullCollaborators() {
            ProcessAcpBackend backend = new ProcessAcpBackend("nulls-ok", catConfig(), MAPPER, null, null, null);

            assertThat(backend.id()).isEqualTo("nulls-ok");
        }
    }

    // ----------------------------------------------------------------------
    // Happy-path tests that spawn the in-repo FakeAcpServer as a child JVM. The fake answers
    // the initialize/session-new/prompt requests with canned JSON-RPC frames so the parent's
    // open() succeeds and the post-handshake paths (prompt, cancel, close, destroyTree's
    // waitFor/onExit/safeExitCode-IllegalThreadStateException) all run.
    // ----------------------------------------------------------------------

    @Nested
    class HappyPathWithFakeServer {

        private static ProcessAcpBackend.Config fakeServerConfig(String... extraSysProps) {
            String javaBin =
                    Paths.get(System.getProperty("java.home"), "bin", "java").toString();
            String classpath = System.getProperty("java.class.path");
            List<String> args = new ArrayList<>();
            args.add("-cp");
            args.add(classpath);
            for (String p : extraSysProps) {
                args.add(p);
            }
            args.add(FakeAcpServer.class.getName());
            return new ProcessAcpBackend.Config(
                    javaBin, args, Map.of(), "test-client", "1.0.0", Duration.ofSeconds(20L));
        }

        private static SubAgentBackend.OpenRequest openRequest() {
            return new SubAgentBackend.OpenRequest(
                    "parent", null, null, null, Map.of("FAKE_SERVER_TEST", "1"), Duration.ofSeconds(30L));
        }

        @Test
        void testOpenSucceedsAndCloseTearsDownChildProcess() {
            assumeThat(isWindows()).isFalse();
            ProcessAcpBackend backend = backendWith(fakeServerConfig());

            SubAgentSession session = backend.open(openRequest());
            assertThat(session.runtimeSessionId()).isEqualTo("sess-fake");

            // clientFor returns the live AcpClient stored under handles
            assertThat(backend.clientFor(session)).isNotNull();

            // close exercises destroyTree (with descendants), waitFor, onExit drain, then
            // AcpClient.close.
            backend.close(session, "test-shutdown");

            // After close: handles map entry is removed → clientFor throws SESSION_GONE.
            SubAgentException reuse = assertThrows(SubAgentException.class, () -> backend.clientFor(session));
            assertThat(reuse.code()).isEqualTo("ACP_SESSION_GONE");
        }

        @Test
        void testPromptEmitsDoneEvent() throws Exception {
            assumeThat(isWindows()).isFalse();
            ProcessAcpBackend backend = backendWith(fakeServerConfig());
            SubAgentSession session = backend.open(openRequest());
            try {
                CountDownLatch doneLatch = new CountDownLatch(1);
                List<SubAgentEvent> events = new ArrayList<>();
                var subscription = backend.prompt(session, "hello", null).subscribe(event -> {
                    events.add(event);
                    if (event instanceof SubAgentEvent.Done) {
                        doneLatch.countDown();
                    }
                });
                try {
                    assertThat(doneLatch.await(5L, TimeUnit.SECONDS))
                            .as("prompt should produce a Done event, captured=%s", events)
                            .isTrue();
                    assertThat(events).anyMatch(e -> e instanceof SubAgentEvent.Done);
                } finally {
                    subscription.dispose();
                }
            } finally {
                backend.close(session, "test-cleanup");
            }
        }

        @Test
        void testPromptHonoursCancellationSignal() throws Exception {
            assumeThat(isWindows()).isFalse();
            ProcessAcpBackend backend = backendWith(fakeServerConfig());
            SubAgentSession session = backend.open(openRequest());
            try {
                CancellationToken token = new CancellationToken();
                AtomicBoolean cancelArrived = new AtomicBoolean(false);

                // Subscribe but immediately fire the cancellation; the backend wires
                // signal.onCancel(() -> client.cancel()) which sends a session/cancel.
                var subscription = backend.prompt(session, "cancel me", token).subscribe(event -> {
                    if (event instanceof SubAgentEvent.Done) {
                        cancelArrived.set(true);
                    }
                });
                token.cancel();

                // Even with the cancel, the fake server replies to prompt with end_turn → Done.
                Thread.sleep(500L);
                subscription.dispose();
                assertThat(cancelArrived.get())
                        .as("Done event should still arrive even after cancel (fake server "
                                + "treats session/cancel as a notification it can ignore)")
                        .isTrue();
            } finally {
                backend.close(session, "test-cleanup");
            }
        }

        @Test
        void testCancelOnLiveSessionRoutesToClient() {
            assumeThat(isWindows()).isFalse();
            ProcessAcpBackend backend = backendWith(fakeServerConfig());
            SubAgentSession session = backend.open(openRequest());
            try {
                // cancel on a live handle exercises the !=null branch of cancel().
                assertDoesNotThrow(() -> backend.cancel(session, "test"));
            } finally {
                backend.close(session, "test-cleanup");
            }
        }

        @Test
        void testStderrTailDrainsBeyondMaxLines() {
            assumeThat(isWindows()).isFalse();

            // Make the fake server print 200 lines to stderr at startup so StderrTail's
            // pollFirst() / overflow branch is taken (MAX_LINES = 80).
            ProcessAcpBackend.Config cfg = fakeServerConfig("-Dfakeacp.stderr.lines=200");
            ProcessAcpBackend backend = backendWith(cfg);
            SubAgentSession session = backend.open(openRequest());

            // Give the drainer thread time to consume the noise.
            try {
                Thread.sleep(200L);
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
            }

            // Now teardown — the StderrTail.snapshot path will run during normal close.
            assertDoesNotThrow(() -> backend.close(session, "test-cleanup"));
        }
    }

    // ----------------------------------------------------------------------
    // Permission-resolution paths. FakeAcpServer sends a server-initiated
    // session/request_permission before responding to prompt; the AcpClient's permission
    // handler (= ProcessAcpBackend.resolvePermission lambda) runs and replies. By varying the
    // policy / resolver / classifier wiring and the shape of the permission request, we
    // exercise resolvePermission + askParent + parsePermissionOptions + parseToolParams +
    // extractToolName + pickOption.
    // ----------------------------------------------------------------------

    @Nested
    class PermissionResolution {

        private static List<String> fakeServerArgs(String... extraSysProps) {
            String classpath = System.getProperty("java.class.path");
            List<String> args = new ArrayList<>();
            args.add("-cp");
            args.add(classpath);
            for (String p : extraSysProps) {
                args.add(p);
            }
            args.add(FakeAcpServer.class.getName());
            return args;
        }

        private static ProcessAcpBackend.Config configFor(String... extraSysProps) {
            String javaBin =
                    Paths.get(System.getProperty("java.home"), "bin", "java").toString();
            return new ProcessAcpBackend.Config(
                    javaBin, fakeServerArgs(extraSysProps), Map.of(), "test-client", "1.0.0", Duration.ofSeconds(20L));
        }

        private static SubAgentBackend.OpenRequest openRequest() {
            return new SubAgentBackend.OpenRequest("parent", null, null, null, Map.of(), Duration.ofSeconds(30L));
        }

        private static List<SubAgentEvent> runPromptUntilDone(ProcessAcpBackend backend, SubAgentSession session)
                throws InterruptedException {
            CountDownLatch latch = new CountDownLatch(1);
            List<SubAgentEvent> events = new ArrayList<>();
            var subscription = backend.prompt(session, "do-thing", null).subscribe(event -> {
                events.add(event);
                if (event instanceof SubAgentEvent.Done) {
                    latch.countDown();
                }
            });
            try {
                boolean reachedDone = latch.await(10L, TimeUnit.SECONDS);
                if (!reachedDone) {
                    throw new AssertionError("prompt did not complete within 10s; events=" + events);
                }
            } finally {
                subscription.dispose();
            }
            return events;
        }

        @Test
        void testAutoAllowSelectsAllowOption() throws Exception {
            assumeThat(isWindows()).isFalse();
            ApprovalClassifier classifier = new ApprovalClassifier();
            ApprovalPolicy policy = (risk, tool) -> ApprovalDecision.AUTO_ALLOW;
            ProcessAcpBackend backend = new ProcessAcpBackend(
                    "auto-allow", configFor("-Dfakeacp.send.permission=true"), MAPPER, classifier, policy, null);

            SubAgentSession session = backend.open(openRequest());
            try {
                List<SubAgentEvent> events = runPromptUntilDone(backend, session);
                assertThat(events).anyMatch(e -> e instanceof SubAgentEvent.Done);
            } finally {
                backend.close(session, "test");
            }
        }

        @Test
        void testAutoAllowFallsBackToFirstOptionWhenNoAllowKind() throws Exception {
            assumeThat(isWindows()).isFalse();

            // Only reject options exposed — pickOption("allow") falls back to first option.
            ProcessAcpBackend backend = new ProcessAcpBackend(
                    "auto-allow-fallback",
                    configFor("-Dfakeacp.send.permission=true", "-Dfakeacp.options.shape=reject_only"),
                    MAPPER,
                    new ApprovalClassifier(),
                    (risk, tool) -> ApprovalDecision.AUTO_ALLOW,
                    null);
            SubAgentSession session = backend.open(openRequest());
            try {
                List<SubAgentEvent> events = runPromptUntilDone(backend, session);
                assertThat(events).anyMatch(e -> e instanceof SubAgentEvent.Done);
            } finally {
                backend.close(session, "test");
            }
        }

        @Test
        void testAutoAllowWithNoOptionsCancels() throws Exception {
            assumeThat(isWindows()).isFalse();

            // empty options array → pickOption returns null even with fallback → cancelled
            ProcessAcpBackend backend = new ProcessAcpBackend(
                    "auto-allow-no-opts",
                    configFor("-Dfakeacp.send.permission=true", "-Dfakeacp.options.shape=empty"),
                    MAPPER,
                    new ApprovalClassifier(),
                    (risk, tool) -> ApprovalDecision.AUTO_ALLOW,
                    null);
            SubAgentSession session = backend.open(openRequest());
            try {
                List<SubAgentEvent> events = runPromptUntilDone(backend, session);
                assertThat(events).anyMatch(e -> e instanceof SubAgentEvent.Done);
            } finally {
                backend.close(session, "test");
            }
        }

        @Test
        void testAskParentWithNullResolverCancels() throws Exception {
            assumeThat(isWindows()).isFalse();
            ProcessAcpBackend backend = new ProcessAcpBackend(
                    "ask-no-resolver",
                    configFor("-Dfakeacp.send.permission=true"),
                    MAPPER,
                    new ApprovalClassifier(),
                    (risk, tool) -> ApprovalDecision.ASK_PARENT,
                    null);
            SubAgentSession session = backend.open(openRequest());
            try {
                List<SubAgentEvent> events = runPromptUntilDone(backend, session);
                assertThat(events).anyMatch(e -> e instanceof SubAgentEvent.Done);
            } finally {
                backend.close(session, "test");
            }
        }

        @Test
        void testAskParentResolverReturnsSelectedOption() throws Exception {
            assumeThat(isWindows()).isFalse();
            ParentPermissionResolver resolver = (req, timeout) -> ParentPermissionDecision.selected("allow_once");
            ProcessAcpBackend backend = new ProcessAcpBackend(
                    "ask-selected",
                    configFor("-Dfakeacp.send.permission=true"),
                    MAPPER,
                    new ApprovalClassifier(),
                    (risk, tool) -> ApprovalDecision.ASK_PARENT,
                    resolver);
            SubAgentSession session = backend.open(openRequest());
            try {
                List<SubAgentEvent> events = runPromptUntilDone(backend, session);
                assertThat(events).anyMatch(e -> e instanceof SubAgentEvent.Done);
            } finally {
                backend.close(session, "test");
            }
        }

        @Test
        void testAskParentResolverReturnsCancelled() throws Exception {
            assumeThat(isWindows()).isFalse();
            ParentPermissionResolver resolver = (req, timeout) -> ParentPermissionDecision.cancelled();
            ProcessAcpBackend backend = new ProcessAcpBackend(
                    "ask-cancelled",
                    configFor("-Dfakeacp.send.permission=true"),
                    MAPPER,
                    new ApprovalClassifier(),
                    (risk, tool) -> ApprovalDecision.ASK_PARENT,
                    resolver);
            SubAgentSession session = backend.open(openRequest());
            try {
                List<SubAgentEvent> events = runPromptUntilDone(backend, session);
                assertThat(events).anyMatch(e -> e instanceof SubAgentEvent.Done);
            } finally {
                backend.close(session, "test");
            }
        }

        @Test
        void testAskParentResolverThrowsIsCaught() throws Exception {
            assumeThat(isWindows()).isFalse();
            ParentPermissionResolver throwing = (req, timeout) -> {
                throw new IllegalStateException("resolver-boom");
            };
            ProcessAcpBackend backend = new ProcessAcpBackend(
                    "ask-throws",
                    configFor("-Dfakeacp.send.permission=true"),
                    MAPPER,
                    new ApprovalClassifier(),
                    (risk, tool) -> ApprovalDecision.ASK_PARENT,
                    throwing);
            SubAgentSession session = backend.open(openRequest());
            try {
                List<SubAgentEvent> events = runPromptUntilDone(backend, session);
                assertThat(events).anyMatch(e -> e instanceof SubAgentEvent.Done);
            } finally {
                backend.close(session, "test");
            }
        }

        @Test
        void testAskParentResolverSelectedWithoutOptionIdFallsBackToCancelled() throws Exception {
            assumeThat(isWindows()).isFalse();

            // SELECTED outcome but optionId blank — backend logs warning and cancels.
            ParentPermissionResolver resolver =
                    (req, timeout) -> new ParentPermissionDecision(ParentPermissionDecision.Outcome.SELECTED, " ");
            ProcessAcpBackend backend = new ProcessAcpBackend(
                    "ask-blank-id",
                    configFor("-Dfakeacp.send.permission=true"),
                    MAPPER,
                    new ApprovalClassifier(),
                    (risk, tool) -> ApprovalDecision.ASK_PARENT,
                    resolver);
            SubAgentSession session = backend.open(openRequest());
            try {
                List<SubAgentEvent> events = runPromptUntilDone(backend, session);
                assertThat(events).anyMatch(e -> e instanceof SubAgentEvent.Done);
            } finally {
                backend.close(session, "test");
            }
        }

        @Test
        void testAskParentNullDecisionCancels() throws Exception {
            assumeThat(isWindows()).isFalse();
            ParentPermissionResolver resolver = (req, timeout) -> null;
            ProcessAcpBackend backend = new ProcessAcpBackend(
                    "ask-null",
                    configFor("-Dfakeacp.send.permission=true"),
                    MAPPER,
                    new ApprovalClassifier(),
                    (risk, tool) -> ApprovalDecision.ASK_PARENT,
                    resolver);
            SubAgentSession session = backend.open(openRequest());
            try {
                List<SubAgentEvent> events = runPromptUntilDone(backend, session);
                assertThat(events).anyMatch(e -> e instanceof SubAgentEvent.Done);
            } finally {
                backend.close(session, "test");
            }
        }

        @Test
        void testDenySelectsRejectOption() throws Exception {
            assumeThat(isWindows()).isFalse();
            ProcessAcpBackend backend = new ProcessAcpBackend(
                    "deny-with-reject",
                    configFor("-Dfakeacp.send.permission=true"),
                    MAPPER,
                    new ApprovalClassifier(),
                    (risk, tool) -> ApprovalDecision.DENY,
                    null);
            SubAgentSession session = backend.open(openRequest());
            try {
                List<SubAgentEvent> events = runPromptUntilDone(backend, session);
                assertThat(events).anyMatch(e -> e instanceof SubAgentEvent.Done);
            } finally {
                backend.close(session, "test");
            }
        }

        @Test
        void testDenyWithoutRejectOptionCancels() throws Exception {
            assumeThat(isWindows()).isFalse();
            ProcessAcpBackend backend = new ProcessAcpBackend(
                    "deny-no-reject",
                    configFor("-Dfakeacp.send.permission=true", "-Dfakeacp.options.shape=empty"),
                    MAPPER,
                    new ApprovalClassifier(),
                    (risk, tool) -> ApprovalDecision.DENY,
                    null);
            SubAgentSession session = backend.open(openRequest());
            try {
                List<SubAgentEvent> events = runPromptUntilDone(backend, session);
                assertThat(events).anyMatch(e -> e instanceof SubAgentEvent.Done);
            } finally {
                backend.close(session, "test");
            }
        }

        @Test
        void testNullPolicyDefaultsToAskParent() throws Exception {
            assumeThat(isWindows()).isFalse();

            // No policy provided → resolvePermission falls back to ASK_PARENT, then to null
            // resolver → cancelled.
            ProcessAcpBackend backend = new ProcessAcpBackend(
                    "null-policy",
                    configFor("-Dfakeacp.send.permission=true"),
                    MAPPER,
                    new ApprovalClassifier(),
                    null,
                    null);
            SubAgentSession session = backend.open(openRequest());
            try {
                List<SubAgentEvent> events = runPromptUntilDone(backend, session);
                assertThat(events).anyMatch(e -> e instanceof SubAgentEvent.Done);
            } finally {
                backend.close(session, "test");
            }
        }

        @Test
        void testNullClassifierFallsBackToUnknownRisk() throws Exception {
            assumeThat(isWindows()).isFalse();

            // Null classifier → resolvePermission uses Risk.UNKNOWN. Policy returns AUTO_ALLOW.
            ProcessAcpBackend backend = new ProcessAcpBackend(
                    "null-classifier",
                    configFor("-Dfakeacp.send.permission=true"),
                    MAPPER,
                    null,
                    (risk, tool) -> ApprovalDecision.AUTO_ALLOW,
                    null);
            SubAgentSession session = backend.open(openRequest());
            try {
                List<SubAgentEvent> events = runPromptUntilDone(backend, session);
                assertThat(events).anyMatch(e -> e instanceof SubAgentEvent.Done);
            } finally {
                backend.close(session, "test");
            }
        }

        @Test
        void testToolNameExtractedFromToolNameField() throws Exception {
            assumeThat(isWindows()).isFalse();

            // toolCall uses "toolName" not "name" — extractToolName's second branch runs.
            ProcessAcpBackend backend = new ProcessAcpBackend(
                    "toolname-field",
                    configFor("-Dfakeacp.send.permission=true", "-Dfakeacp.tool.name.field=toolName"),
                    MAPPER,
                    new ApprovalClassifier(),
                    (risk, tool) -> ApprovalDecision.AUTO_ALLOW,
                    null);
            SubAgentSession session = backend.open(openRequest());
            try {
                List<SubAgentEvent> events = runPromptUntilDone(backend, session);
                assertThat(events).anyMatch(e -> e instanceof SubAgentEvent.Done);
            } finally {
                backend.close(session, "test");
            }
        }

        @Test
        void testToolCallMissingHasEmptyToolName() throws Exception {
            assumeThat(isWindows()).isFalse();

            // toolCall=null → extractToolName returns ""; classifier.classify("") returns UNKNOWN.
            ProcessAcpBackend backend = new ProcessAcpBackend(
                    "no-toolcall",
                    configFor("-Dfakeacp.send.permission=true", "-Dfakeacp.include.toolcall=false"),
                    MAPPER,
                    new ApprovalClassifier(),
                    (risk, tool) -> ApprovalDecision.AUTO_ALLOW,
                    null);
            SubAgentSession session = backend.open(openRequest());
            try {
                List<SubAgentEvent> events = runPromptUntilDone(backend, session);
                assertThat(events).anyMatch(e -> e instanceof SubAgentEvent.Done);
            } finally {
                backend.close(session, "test");
            }
        }

        @Test
        void testToolCallMissingParamsBecomesEmptyMap() throws Exception {
            assumeThat(isWindows()).isFalse();

            // toolCall present but no "params" key → parseToolParams returns Map.of().
            // Need ASK_PARENT path to hit parseToolParams (via askParent → parseToolParams).
            ParentPermissionResolver resolver = (req, timeout) -> ParentPermissionDecision.cancelled();
            ProcessAcpBackend backend = new ProcessAcpBackend(
                    "no-params",
                    configFor("-Dfakeacp.send.permission=true", "-Dfakeacp.include.params=false"),
                    MAPPER,
                    new ApprovalClassifier(),
                    (risk, tool) -> ApprovalDecision.ASK_PARENT,
                    resolver);
            SubAgentSession session = backend.open(openRequest());
            try {
                List<SubAgentEvent> events = runPromptUntilDone(backend, session);
                assertThat(events).anyMatch(e -> e instanceof SubAgentEvent.Done);
            } finally {
                backend.close(session, "test");
            }
        }

        @Test
        void testOptionsNotAnArrayBecomesEmptyList() throws Exception {
            assumeThat(isWindows()).isFalse();

            // options is an object, not an array → parsePermissionOptions returns empty list.
            // ASK_PARENT routes through askParent which calls parsePermissionOptions.
            ParentPermissionResolver resolver = (req, timeout) -> ParentPermissionDecision.cancelled();
            ProcessAcpBackend backend = new ProcessAcpBackend(
                    "options-not-array",
                    configFor("-Dfakeacp.send.permission=true", "-Dfakeacp.options.shape=options_object"),
                    MAPPER,
                    new ApprovalClassifier(),
                    (risk, tool) -> ApprovalDecision.ASK_PARENT,
                    resolver);
            SubAgentSession session = backend.open(openRequest());
            try {
                List<SubAgentEvent> events = runPromptUntilDone(backend, session);
                assertThat(events).anyMatch(e -> e instanceof SubAgentEvent.Done);
            } finally {
                backend.close(session, "test");
            }
        }
    }
}
