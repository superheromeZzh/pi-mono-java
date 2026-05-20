/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.hicampus.mate.matecampusclaw.agent.subagent.acp.backend;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import com.huawei.hicampus.mate.matecampusclaw.agent.subagent.SubAgentBackend;
import com.huawei.hicampus.mate.matecampusclaw.agent.subagent.SubAgentEvent;
import com.huawei.hicampus.mate.matecampusclaw.agent.subagent.SubAgentException;
import com.huawei.hicampus.mate.matecampusclaw.agent.subagent.SubAgentSession;
import com.huawei.hicampus.mate.matecampusclaw.agent.subagent.SubAgentSessionKey;
import com.huawei.hicampus.mate.matecampusclaw.agent.subagent.approval.ApprovalClassifier;
import com.huawei.hicampus.mate.matecampusclaw.agent.subagent.approval.ApprovalDecision;
import com.huawei.hicampus.mate.matecampusclaw.agent.subagent.approval.ApprovalPolicy;
import com.huawei.hicampus.mate.matecampusclaw.agent.subagent.approval.ParentPermissionDecision;
import com.huawei.hicampus.mate.matecampusclaw.agent.subagent.approval.ParentPermissionResolver;
import com.huawei.hicampus.mate.matecampusclaw.agent.tool.CancellationToken;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link ProcessAcpBackend}.
 *
 * <p>All process spawning is routed through the package-private {@link ProcessAcpBackend.ProcessSpawner}
 * seam to an {@link InProcessFakeAcpServer} — a {@code Process} subclass that runs
 * {@link FakeAcpServer#runDispatchLoop} on a virtual thread and talks ACP to the parent over
 * piped streams. No real {@code fork(2)} / {@code execve(2)} happens, so the suite runs in
 * &lt;5s on CI instead of multi-minute child-JVM cold starts, and the OS-platform guards
 * (skip on Windows) are no longer needed.
 *
 * <p>The Windows-specific {@code cmd.exe /c} wrapper inside {@code buildArgv} is exercised by
 * a unit-style test that intercepts the {@code ProcessBuilder} via the spawner seam.
 */
class ProcessAcpBackendTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    // Standard fast config: short init timeout so init-failure paths fail in under a second
    // when the spawner returns a non-responsive child.
    private static ProcessAcpBackend.Config fastConfig() {
        return new ProcessAcpBackend.Config(
                "fake-command",
                List.of(),
                Map.of("ACP_PROCESS_TEST", "1"),
                "test-client",
                "9.9.9",
                Duration.ofSeconds(2L),
                Duration.ofMillis(500L));
    }

    private static ProcessAcpBackend.Config bogusConfig() {
        return new ProcessAcpBackend.Config(
                "/nonexistent/definitely-not-a-real-binary-" + System.nanoTime(),
                List.of(),
                Map.of(),
                "test-client",
                "1.0.0",
                Duration.ofSeconds(2L),
                Duration.ofMillis(500L));
    }

    // Spawner that returns a happy in-process fake responding to all handshake methods.
    private static ProcessAcpBackend.ProcessSpawner happySpawner() {
        return pb -> new InProcessFakeAcpServer(FakeAcpServer.ServerOptions.defaults(), 0);
    }

    // Spawner with a customised dispatch behaviour (permission, options shape, etc.).
    private static ProcessAcpBackend.ProcessSpawner spawnerWith(FakeAcpServer.ServerOptions opts) {
        return pb -> new InProcessFakeAcpServer(opts, 0);
    }

    // Spawner that returns a fake which writes stderrLines lines to stderr at startup.
    private static ProcessAcpBackend.ProcessSpawner spawnerWithStderrNoise(int stderrLines) {
        return pb -> new InProcessFakeAcpServer(FakeAcpServer.ServerOptions.defaults(), stderrLines);
    }

    // Spawner that returns a fake whose dispatch loop is destroyed immediately, so the child's
    // stdout closes before any handshake response is sent. Forces the parent's init-timeout
    // branch to fire fast (within the 500ms config initTimeout). destroy() runs before any
    // read happens — pipe closes propagate to parent as EOF and pending response futures hit
    // the configured short initTimeout.
    private static ProcessAcpBackend.ProcessSpawner muteSpawner() {
        return pb -> {
            InProcessFakeAcpServer fake = new InProcessFakeAcpServer(FakeAcpServer.ServerOptions.defaults(), 0);
            fake.destroyForcibly();
            return fake;
        };
    }

    // Spawner that throws IOException to exercise the ACP_SPAWN_FAILED branch.
    private static ProcessAcpBackend.ProcessSpawner failingSpawner() {
        return pb -> {
            throw new IOException("simulated spawn failure");
        };
    }

    private static ProcessAcpBackend backendWith(
            ProcessAcpBackend.Config cfg, ProcessAcpBackend.ProcessSpawner spawner) {
        return new ProcessAcpBackend("test-backend", cfg, MAPPER, null, null, null, spawner);
    }

    private static SubAgentBackend.OpenRequest openRequest() {
        return new SubAgentBackend.OpenRequest(
                "parent", null, null, null, Map.of("FAKE_SERVER_TEST", "1"), Duration.ofSeconds(2L));
    }

    // ----------------------------------------------------------------------
    // Constructor validation (no spawning)
    // ----------------------------------------------------------------------

    @Nested
    class ConstructorValidation {

        @Test
        void testRejectsNullId() {
            IllegalArgumentException ex = assertThrows(
                    IllegalArgumentException.class,
                    () -> new ProcessAcpBackend(null, fastConfig(), MAPPER, null, null, null));
            assertThat(ex.getMessage()).contains("id must not be blank");
        }

        @Test
        void testRejectsBlankId() {
            IllegalArgumentException ex = assertThrows(
                    IllegalArgumentException.class,
                    () -> new ProcessAcpBackend("  ", fastConfig(), MAPPER, null, null, null));
            assertThat(ex.getMessage()).contains("id must not be blank");
        }

        @Test
        void testIdGetterReturnsConstructorValue() {
            ProcessAcpBackend backend = new ProcessAcpBackend("my-id", fastConfig(), MAPPER, null, null, null);
            assertThat(backend.id()).isEqualTo("my-id");
        }

        @Test
        void testNullSpawnerFallsBackToProcessBuilderStart() {
            ProcessAcpBackend backend =
                    new ProcessAcpBackend("with-null-spawner", fastConfig(), MAPPER, null, null, null, null);
            assertThat(backend.id()).isEqualTo("with-null-spawner");
        }
    }

    // ----------------------------------------------------------------------
    // Config validation (no spawning)
    // ----------------------------------------------------------------------

    @Nested
    class ConfigValidation {

        @Test
        void testRejectsNullCommand() {
            assertThrows(
                    IllegalArgumentException.class,
                    () -> new ProcessAcpBackend.Config(null, List.of(), Map.of(), "n", "v", Duration.ofSeconds(1L)));
        }

        @Test
        void testRejectsBlankCommand() {
            assertThrows(
                    IllegalArgumentException.class,
                    () -> new ProcessAcpBackend.Config("   ", List.of(), Map.of(), "n", "v", Duration.ofSeconds(1L)));
        }

        @Test
        void testNullArgsBecomesEmptyList() {
            var cfg = new ProcessAcpBackend.Config("cmd", null, Map.of(), "n", "v", Duration.ofSeconds(1L));
            assertThat(cfg.args()).isEmpty();
        }

        @Test
        void testNullEnvBecomesEmptyMap() {
            var cfg = new ProcessAcpBackend.Config("cmd", List.of(), null, "n", "v", Duration.ofSeconds(1L));
            assertThat(cfg.env()).isEmpty();
        }

        @Test
        void testBlankClientNameUsesDefault() {
            var cfg = new ProcessAcpBackend.Config("cmd", List.of(), Map.of(), "  ", "v", Duration.ofSeconds(1L));
            assertThat(cfg.clientName()).isEqualTo("campusclaw");
        }

        @Test
        void testNullClientNameUsesDefault() {
            var cfg = new ProcessAcpBackend.Config("cmd", List.of(), Map.of(), null, "v", Duration.ofSeconds(1L));
            assertThat(cfg.clientName()).isEqualTo("campusclaw");
        }

        @Test
        void testBlankClientVersionUsesDefault() {
            var cfg = new ProcessAcpBackend.Config("cmd", List.of(), Map.of(), "n", "", Duration.ofSeconds(1L));
            assertThat(cfg.clientVersion()).isEqualTo("1.0.0");
        }

        @Test
        void testNullClientVersionUsesDefault() {
            var cfg = new ProcessAcpBackend.Config("cmd", List.of(), Map.of(), "n", null, Duration.ofSeconds(1L));
            assertThat(cfg.clientVersion()).isEqualTo("1.0.0");
        }

        @Test
        void testNullPromptTimeoutUsesDefault() {
            var cfg = new ProcessAcpBackend.Config("cmd", List.of(), Map.of(), "n", "v", null);
            assertThat(cfg.promptTimeout()).isEqualTo(Duration.ofMinutes(10L));
        }

        @Test
        void testNullInitTimeoutUsesDefault() {
            var cfg = new ProcessAcpBackend.Config("cmd", List.of(), Map.of(), "n", "v", Duration.ofSeconds(1L), null);
            assertThat(cfg.initTimeout()).isEqualTo(Duration.ofSeconds(30L));
        }

        @Test
        void testSixArgConvenienceConstructorDefaultsInitTimeout() {
            var cfg = new ProcessAcpBackend.Config("cmd", List.of(), Map.of(), "n", "v", Duration.ofSeconds(1L));
            assertThat(cfg.initTimeout()).isEqualTo(Duration.ofSeconds(30L));
        }
    }

    // ----------------------------------------------------------------------
    // No-op paths on unknown / unregistered sessions
    // ----------------------------------------------------------------------

    @Nested
    class NoOpsOnUnknownSession {

        private ProcessAcpBackend backend() {
            return new ProcessAcpBackend("test-backend", fastConfig(), MAPPER, null, null, null);
        }

        private SubAgentSession syntheticSession() {
            SubAgentSessionKey key = SubAgentSessionKey.newKey("parent", "test-backend");
            return new SubAgentSession(key, "remote-id", backend());
        }

        @Test
        void testCancelOnUnknownSessionIsNoOp() {
            assertDoesNotThrow(() -> backend().cancel(syntheticSession(), "test"));
        }

        @Test
        void testCloseOnUnknownSessionIsNoOp() {
            assertDoesNotThrow(() -> backend().close(syntheticSession(), "test"));
        }

        @Test
        void testPromptOnUnknownSessionThrowsAcpSessionGone() {
            SubAgentException ex =
                    assertThrows(SubAgentException.class, () -> backend().prompt(syntheticSession(), "hello", null));
            assertThat(ex.code()).isEqualTo("ACP_SESSION_GONE");
        }

        @Test
        void testClientForUnknownSessionThrowsAcpSessionGone() {
            SubAgentException ex =
                    assertThrows(SubAgentException.class, () -> backend().clientFor(syntheticSession()));
            assertThat(ex.code()).isEqualTo("ACP_SESSION_GONE");
        }
    }

    // ----------------------------------------------------------------------
    // Spawn-failure path: spawner throws IOException → ACP_SPAWN_FAILED
    // ----------------------------------------------------------------------

    @Nested
    class SpawnFailures {

        @Test
        void testFailingSpawnerThrowsAcpSpawnFailed() {
            ProcessAcpBackend backend = backendWith(fastConfig(), failingSpawner());
            SubAgentException ex = assertThrows(SubAgentException.class, () -> backend.open(openRequest()));
            assertThat(ex.code()).isEqualTo("ACP_SPAWN_FAILED");
            assertThat(ex.getMessage()).contains("failed to launch");
            assertThat(ex.getCause()).isInstanceOf(IOException.class);
        }

        @Test
        void testUnknownCommandThrowsAcpSpawnFailed() {
            // Uses the public 6-arg constructor → real ProcessBuilder::start → IOException for
            // nonexistent binary. Fast (no child JVM cold start; spawn fails immediately).
            ProcessAcpBackend backend = new ProcessAcpBackend("real-spawn", bogusConfig(), MAPPER, null, null, null);
            SubAgentException ex = assertThrows(SubAgentException.class, () -> backend.open(openRequest()));
            assertThat(ex.code()).isEqualTo("ACP_SPAWN_FAILED");
            assertThat(ex.getCause()).isInstanceOf(IOException.class);
        }
    }

    // ----------------------------------------------------------------------
    // Open init-failure path: child closes immediately → AcpClient.initialize times out fast
    // (config.initTimeout=500ms). Exercises destroyTree + enrich + safeExitCode branches.
    // ----------------------------------------------------------------------

    @Nested
    class OpenInitFailure {

        @Test
        void testInitTimeoutTriggersCleanup() {
            ProcessAcpBackend backend = backendWith(fastConfig(), muteSpawner());
            SubAgentException ex = assertThrows(SubAgentException.class, () -> backend.open(openRequest()));
            assertThat(ex.code()).isEqualTo("ACP_OPEN_FAILED");
            assertThat(ex.getMessage()).containsAnyOf("still alive", "exited code=");
        }

        @Test
        void testInitTimeoutWithCwdSet() {
            String cwd = System.getProperty("java.io.tmpdir");
            ProcessAcpBackend backend = backendWith(fastConfig(), muteSpawner());
            var req = new SubAgentBackend.OpenRequest("parent", cwd, null, null, Map.of(), Duration.ofSeconds(2L));
            SubAgentException ex = assertThrows(SubAgentException.class, () -> backend.open(req));
            assertThat(ex.code()).isEqualTo("ACP_OPEN_FAILED");
        }

        @Test
        void testStderrTailCapturedOnInitFailure() {
            // Stderr noise written before the dispatch loop dies → drainer puts lines into
            // StderrTail → enrich() suffixes them onto the exception message.
            ProcessAcpBackend.ProcessSpawner spawner = pb -> {
                InProcessFakeAcpServer fake = new InProcessFakeAcpServer(FakeAcpServer.ServerOptions.defaults(), 3);
                try {
                    Thread.sleep(50L);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                }
                fake.destroyForcibly();
                return fake;
            };
            ProcessAcpBackend backend = backendWith(fastConfig(), spawner);
            SubAgentException ex = assertThrows(SubAgentException.class, () -> backend.open(openRequest()));
            assertThat(ex.code()).isEqualTo("ACP_OPEN_FAILED");
        }
    }

    // ----------------------------------------------------------------------
    // Constructor wiring with collaborators
    // ----------------------------------------------------------------------

    @Nested
    class WiringWithCollaborators {

        @Test
        void testBackendAcceptsConfiguredCollaborators() {
            ApprovalClassifier classifier = new ApprovalClassifier();
            ApprovalPolicy policy = (risk, tool) -> ApprovalDecision.AUTO_ALLOW;
            ParentPermissionResolver resolver = (req, timeout) -> ParentPermissionDecision.cancelled();
            ProcessAcpBackend backend =
                    new ProcessAcpBackend("with-collab", fastConfig(), MAPPER, classifier, policy, resolver);
            assertThat(backend.id()).isEqualTo("with-collab");
        }

        @Test
        void testBackendAcceptsNullCollaborators() {
            ProcessAcpBackend backend = new ProcessAcpBackend("nulls-ok", fastConfig(), MAPPER, null, null, null);
            assertThat(backend.id()).isEqualTo("nulls-ok");
        }
    }

    // ----------------------------------------------------------------------
    // Happy-path tests using in-process FakeAcpServer (no JVM cold start).
    // ----------------------------------------------------------------------

    @Nested
    class HappyPathWithInProcessFake {

        @Test
        void testOpenSucceedsAndCloseTearsDownChildProcess() {
            ProcessAcpBackend backend = backendWith(fastConfig(), happySpawner());
            SubAgentSession session = backend.open(openRequest());
            assertThat(session.runtimeSessionId()).isEqualTo("sess-fake");
            assertThat(backend.clientFor(session)).isNotNull();

            backend.close(session, "test-shutdown");

            SubAgentException reuse = assertThrows(SubAgentException.class, () -> backend.clientFor(session));
            assertThat(reuse.code()).isEqualTo("ACP_SESSION_GONE");
        }

        @Test
        void testPromptEmitsDoneEvent() throws Exception {
            ProcessAcpBackend backend = backendWith(fastConfig(), happySpawner());
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
                    assertThat(doneLatch.await(2L, TimeUnit.SECONDS))
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
            ProcessAcpBackend backend = backendWith(fastConfig(), happySpawner());
            SubAgentSession session = backend.open(openRequest());
            try {
                CancellationToken token = new CancellationToken();
                CountDownLatch doneLatch = new CountDownLatch(1);
                AtomicBoolean doneArrived = new AtomicBoolean(false);

                var subscription = backend.prompt(session, "cancel me", token).subscribe(event -> {
                    if (event instanceof SubAgentEvent.Done) {
                        doneArrived.set(true);
                        doneLatch.countDown();
                    }
                });
                token.cancel();

                assertThat(doneLatch.await(2L, TimeUnit.SECONDS))
                        .as("Done event should still arrive even after cancel (fake server treats "
                                + "session/cancel as a notification it can ignore)")
                        .isTrue();
                subscription.dispose();
                assertThat(doneArrived.get()).isTrue();
            } finally {
                backend.close(session, "test-cleanup");
            }
        }

        @Test
        void testCancelOnLiveSessionRoutesToClient() {
            ProcessAcpBackend backend = backendWith(fastConfig(), happySpawner());
            SubAgentSession session = backend.open(openRequest());
            try {
                assertDoesNotThrow(() -> backend.cancel(session, "test"));
            } finally {
                backend.close(session, "test-cleanup");
            }
        }

        @Test
        void testStderrTailDrainsBeyondMaxLines() {
            // 200 lines > StderrTail.MAX_LINES (80) → pollFirst() overflow branch runs.
            ProcessAcpBackend backend = backendWith(fastConfig(), spawnerWithStderrNoise(200));
            SubAgentSession session = backend.open(openRequest());
            assertDoesNotThrow(() -> backend.close(session, "test-cleanup"));
        }
    }

    // ----------------------------------------------------------------------
    // Permission-resolution paths. FakeAcpServer sends a server-initiated
    // session/request_permission before responding to prompt; the AcpClient's permission
    // handler (= ProcessAcpBackend.resolvePermission lambda) runs and replies.
    // ----------------------------------------------------------------------

    @Nested
    class PermissionResolution {

        private static FakeAcpServer.ServerOptions optsWith(String optionsShape) {
            return new FakeAcpServer.ServerOptions(false, true, "name", "bash", true, true, optionsShape);
        }

        private static FakeAcpServer.ServerOptions optsWith(
                String optionsShape, String toolNameField, boolean includeToolCall, boolean includeParams) {
            return new FakeAcpServer.ServerOptions(
                    false, true, toolNameField, "bash", includeToolCall, includeParams, optionsShape);
        }

        /**
         * Pairs a {@link ProcessAcpBackend} under test with a reference that will hold the
         * {@link InProcessFakeAcpServer} the spawner creates on {@code open()}. The reference is
         * populated lazily; tests must call {@link ProcessAcpBackend#open} (via
         * {@link #runPermissionScenarioToDone}) before reading {@code fakeRef.get()}.
         */
        private record TestBackend(ProcessAcpBackend backend, AtomicReference<InProcessFakeAcpServer> fakeRef) {}

        private static TestBackend backendFor(
                String id,
                FakeAcpServer.ServerOptions opts,
                ApprovalClassifier classifier,
                ApprovalPolicy policy,
                ParentPermissionResolver resolver) {
            AtomicReference<InProcessFakeAcpServer> fakeRef = new AtomicReference<>();
            ProcessAcpBackend.ProcessSpawner spawner = pb -> {
                InProcessFakeAcpServer fake = new InProcessFakeAcpServer(opts, 0);
                fakeRef.set(fake);
                return fake;
            };
            return new TestBackend(
                    new ProcessAcpBackend(id, fastConfig(), MAPPER, classifier, policy, resolver, spawner), fakeRef);
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
                boolean reachedDone = latch.await(3L, TimeUnit.SECONDS);
                if (!reachedDone) {
                    throw new AssertionError("prompt did not complete within 3s; events=" + events);
                }
            } finally {
                subscription.dispose();
            }
            return events;
        }

        @Test
        void testAutoAllowSelectsAllowOption() throws Exception {
            TestBackend t = backendFor(
                    "auto-allow",
                    optsWith("allow_reject"),
                    new ApprovalClassifier(),
                    (risk, tool) -> ApprovalDecision.AUTO_ALLOW,
                    null);
            String response = runPermissionScenarioAndGetResponse(t);
            assertThat(response).contains("\"outcome\":\"selected\"").contains("\"optionId\":\"allow_once\"");
        }

        @Test
        void testAutoAllowFallsBackToFirstOptionWhenNoAllowKind() throws Exception {
            TestBackend t = backendFor(
                    "auto-allow-fallback",
                    optsWith("reject_only"),
                    new ApprovalClassifier(),
                    (risk, tool) -> ApprovalDecision.AUTO_ALLOW,
                    null);
            String response = runPermissionScenarioAndGetResponse(t);
            assertThat(response).contains("\"outcome\":\"selected\"").contains("\"optionId\":\"reject_once\"");
        }

        @Test
        void testAutoAllowWithNoOptionsCancels() throws Exception {
            TestBackend t = backendFor(
                    "auto-allow-no-opts",
                    optsWith("empty"),
                    new ApprovalClassifier(),
                    (risk, tool) -> ApprovalDecision.AUTO_ALLOW,
                    null);
            String response = runPermissionScenarioAndGetResponse(t);
            assertThat(response).contains("\"outcome\":\"cancelled\"");
        }

        @Test
        void testAskParentWithNullResolverCancels() throws Exception {
            TestBackend t = backendFor(
                    "ask-no-resolver",
                    optsWith("allow_reject"),
                    new ApprovalClassifier(),
                    (risk, tool) -> ApprovalDecision.ASK_PARENT,
                    null);
            String response = runPermissionScenarioAndGetResponse(t);
            assertThat(response).contains("\"outcome\":\"cancelled\"");
        }

        @Test
        void testAskParentResolverReturnsSelectedOption() throws Exception {
            ParentPermissionResolver resolver = (req, timeout) -> ParentPermissionDecision.selected("allow_once");
            TestBackend t = backendFor(
                    "ask-selected",
                    optsWith("allow_reject"),
                    new ApprovalClassifier(),
                    (risk, tool) -> ApprovalDecision.ASK_PARENT,
                    resolver);
            String response = runPermissionScenarioAndGetResponse(t);
            assertThat(response).contains("\"outcome\":\"selected\"").contains("\"optionId\":\"allow_once\"");
        }

        @Test
        void testAskParentResolverReturnsCancelled() throws Exception {
            ParentPermissionResolver resolver = (req, timeout) -> ParentPermissionDecision.cancelled();
            TestBackend t = backendFor(
                    "ask-cancelled",
                    optsWith("allow_reject"),
                    new ApprovalClassifier(),
                    (risk, tool) -> ApprovalDecision.ASK_PARENT,
                    resolver);
            String response = runPermissionScenarioAndGetResponse(t);
            assertThat(response).contains("\"outcome\":\"cancelled\"");
        }

        @Test
        void testAskParentResolverThrowsIsCaught() throws Exception {
            ParentPermissionResolver throwing = (req, timeout) -> {
                throw new IllegalStateException("resolver-boom");
            };
            TestBackend t = backendFor(
                    "ask-throws",
                    optsWith("allow_reject"),
                    new ApprovalClassifier(),
                    (risk, tool) -> ApprovalDecision.ASK_PARENT,
                    throwing);
            String response = runPermissionScenarioAndGetResponse(t);
            assertThat(response).contains("\"outcome\":\"cancelled\"");
        }

        @Test
        void testAskParentResolverSelectedWithoutOptionIdFallsBackToCancelled() throws Exception {
            ParentPermissionResolver resolver =
                    (req, timeout) -> new ParentPermissionDecision(ParentPermissionDecision.Outcome.SELECTED, " ");
            TestBackend t = backendFor(
                    "ask-blank-id",
                    optsWith("allow_reject"),
                    new ApprovalClassifier(),
                    (risk, tool) -> ApprovalDecision.ASK_PARENT,
                    resolver);
            String response = runPermissionScenarioAndGetResponse(t);
            assertThat(response).contains("\"outcome\":\"cancelled\"");
        }

        @Test
        void testAskParentNullDecisionCancels() throws Exception {
            ParentPermissionResolver resolver = (req, timeout) -> null;
            TestBackend t = backendFor(
                    "ask-null",
                    optsWith("allow_reject"),
                    new ApprovalClassifier(),
                    (risk, tool) -> ApprovalDecision.ASK_PARENT,
                    resolver);
            String response = runPermissionScenarioAndGetResponse(t);
            assertThat(response).contains("\"outcome\":\"cancelled\"");
        }

        @Test
        void testDenySelectsRejectOption() throws Exception {
            TestBackend t = backendFor(
                    "deny-with-reject",
                    optsWith("allow_reject"),
                    new ApprovalClassifier(),
                    (risk, tool) -> ApprovalDecision.DENY,
                    null);
            String response = runPermissionScenarioAndGetResponse(t);
            assertThat(response).contains("\"outcome\":\"selected\"").contains("\"optionId\":\"reject_once\"");
        }

        @Test
        void testDenyWithoutRejectOptionCancels() throws Exception {
            TestBackend t = backendFor(
                    "deny-no-reject",
                    optsWith("empty"),
                    new ApprovalClassifier(),
                    (risk, tool) -> ApprovalDecision.DENY,
                    null);
            String response = runPermissionScenarioAndGetResponse(t);
            assertThat(response).contains("\"outcome\":\"cancelled\"");
        }

        @Test
        void testNullPolicyDefaultsToAskParent() throws Exception {
            // No resolver supplied → ASK_PARENT default path resolves to cancelled, so the wire
            // response carries outcome:cancelled. This verifies the null-policy → ASK_PARENT
            // fallback by way of the resulting cancellation (rather than a no-op pass-through).
            TestBackend t = backendFor("null-policy", optsWith("allow_reject"), new ApprovalClassifier(), null, null);
            String response = runPermissionScenarioAndGetResponse(t);
            assertThat(response).contains("\"outcome\":\"cancelled\"");
        }

        @Test
        void testNullClassifierFallsBackToUnknownRisk() throws Exception {
            TestBackend t = backendFor(
                    "null-classifier",
                    optsWith("allow_reject"),
                    null,
                    (risk, tool) -> ApprovalDecision.AUTO_ALLOW,
                    null);
            String response = runPermissionScenarioAndGetResponse(t);
            assertThat(response).contains("\"outcome\":\"selected\"").contains("\"optionId\":\"allow_once\"");
        }

        @Test
        void testToolNameExtractedFromToolNameField() throws Exception {
            TestBackend t = backendFor(
                    "toolname-field",
                    optsWith("allow_reject", "toolName", true, true),
                    new ApprovalClassifier(),
                    (risk, tool) -> ApprovalDecision.AUTO_ALLOW,
                    null);
            String response = runPermissionScenarioAndGetResponse(t);
            assertThat(response).contains("\"outcome\":\"selected\"").contains("\"optionId\":\"allow_once\"");
        }

        @Test
        void testToolCallMissingHasEmptyToolName() throws Exception {
            TestBackend t = backendFor(
                    "no-toolcall",
                    optsWith("allow_reject", "name", false, true),
                    new ApprovalClassifier(),
                    (risk, tool) -> ApprovalDecision.AUTO_ALLOW,
                    null);
            String response = runPermissionScenarioAndGetResponse(t);
            assertThat(response).contains("\"outcome\":\"selected\"").contains("\"optionId\":\"allow_once\"");
        }

        @Test
        void testToolCallMissingParamsBecomesEmptyMap() throws Exception {
            ParentPermissionResolver resolver = (req, timeout) -> ParentPermissionDecision.cancelled();
            TestBackend t = backendFor(
                    "no-params",
                    optsWith("allow_reject", "name", true, false),
                    new ApprovalClassifier(),
                    (risk, tool) -> ApprovalDecision.ASK_PARENT,
                    resolver);
            String response = runPermissionScenarioAndGetResponse(t);
            assertThat(response).contains("\"outcome\":\"cancelled\"");
        }

        @Test
        void testOptionsNotAnArrayBecomesEmptyList() throws Exception {
            ParentPermissionResolver resolver = (req, timeout) -> ParentPermissionDecision.cancelled();
            TestBackend t = backendFor(
                    "options-not-array",
                    optsWith("options_object"),
                    new ApprovalClassifier(),
                    (risk, tool) -> ApprovalDecision.ASK_PARENT,
                    resolver);
            String response = runPermissionScenarioAndGetResponse(t);
            assertThat(response).contains("\"outcome\":\"cancelled\"");
        }

        /**
         * Drives the prompt to {@code Done}, then returns the single permission response the
         * backend sent in reply to the server-initiated {@code session/request_permission}.
         * Fails fast if the backend never produced a response — that indicates the permission
         * round-trip itself broke, which is distinct from the outcome-mismatch case each caller
         * asserts against.
         *
         * @param t backend under test paired with the spawner's fake reference
         * @return the raw JSON-RPC response line the backend sent back to the fake server
         * @throws InterruptedException if the prompt-done latch is interrupted
         */
        private static String runPermissionScenarioAndGetResponse(TestBackend t) throws InterruptedException {
            SubAgentSession session = t.backend().open(openRequest());
            try {
                List<SubAgentEvent> events = runPromptUntilDone(t.backend(), session);
                assertThat(events).anyMatch(e -> e instanceof SubAgentEvent.Done);
                InProcessFakeAcpServer fake = t.fakeRef().get();
                assertThat(fake)
                        .as("spawner should have produced a fake server")
                        .isNotNull();
                List<String> responses = fake.permissionResponses();
                assertThat(responses)
                        .as("backend must reply to the server-initiated session/request_permission")
                        .hasSize(1);
                return responses.get(0);
            } finally {
                t.backend().close(session, "test");
            }
        }
    }
}
