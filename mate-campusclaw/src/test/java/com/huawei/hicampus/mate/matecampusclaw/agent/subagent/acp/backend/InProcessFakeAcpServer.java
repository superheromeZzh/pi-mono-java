/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.hicampus.mate.matecampusclaw.agent.subagent.acp.backend;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.stream.Stream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * {@link Process} subclass that runs {@link FakeAcpServer#runDispatchLoop} on a virtual thread
 * inside the same JVM, exposing piped streams so {@link ProcessAcpBackend} sees a real-looking
 * child without any {@code fork(2)} / {@code execve(2)} cost. The dispatch loop reads the
 * parent's ACP requests from its {@code getOutputStream} and writes canned JSON-RPC responses
 * to its {@code getInputStream}; optional stderr noise is emitted to {@code getErrorStream}
 * so the parent's {@code StderrTail} drainer path is also covered.
 *
 * <p>This lets the test suite exercise the post-open code paths (initialize handshake,
 * prompt/cancel/close, destroyTree → waitFor + onExit + safeExitCode-IllegalThreadStateException,
 * stderr drainer overflow) without the ~1-2s JVM cold start that doing the same with a real
 * {@code java -cp ... FakeAcpServer} child would incur on CI.
 *
 * <p>Lifecycle:
 * <ul>
 *   <li>Construction starts the dispatch thread immediately.</li>
 *   <li>{@link #destroyForcibly()} closes both ends of every pipe and completes the exit-code
 *       future with 143 (matches SIGTERM convention); the dispatch thread's next pipe read /
 *       write throws IOException and the thread returns.</li>
 *   <li>{@link #descendants()} is overridden to return an empty stream — there are no real
 *       OS-level descendants, which is exactly the assumption {@code destroyTree}'s descendant
 *       drain loop already handles.</li>
 * </ul>
 *
 * @version [br_eCampusCore 25.1.0_Next, 2026/05/20]
 * @since [br_eCampusCore 25.1.0_Next]
 */
final class InProcessFakeAcpServer extends Process {

    private static final Logger log = LoggerFactory.getLogger(InProcessFakeAcpServer.class);

    private static final int PIPE_BUFFER_BYTES = 64 * 1024;

    private final PipedInputStream parentReadsChildStdout;
    private final PipedOutputStream parentWritesChildStdin;
    private final PipedInputStream parentReadsChildStderr;
    private final CompletableFuture<Integer> exitFuture = new CompletableFuture<>();
    private final Thread dispatchThread;
    private final List<String> permissionResponses = Collections.synchronizedList(new ArrayList<>());

    /**
     * Construct an in-process fake server.
     *
     * @param opts dispatch-loop behaviour switches
     * @param stderrLines number of {@code stderr-noise-line-N} lines to write to stderr at
     *     startup before entering the dispatch loop (set to a value &gt; 80 to exercise
     *     {@code StderrTail}'s ring-buffer overflow branch)
     * @throws IOException if pipe wiring fails
     */
    InProcessFakeAcpServer(FakeAcpServer.ServerOptions opts, int stderrLines) throws IOException {
        // child stdout: child writes → parent reads
        PipedOutputStream childWritesStdout = new PipedOutputStream();
        this.parentReadsChildStdout = new PipedInputStream(childWritesStdout, PIPE_BUFFER_BYTES);

        // child stdin: parent writes → child reads
        this.parentWritesChildStdin = new PipedOutputStream();
        PipedInputStream childReadsStdin = new PipedInputStream(this.parentWritesChildStdin, PIPE_BUFFER_BYTES);

        // child stderr: child writes → parent reads
        PipedOutputStream childWritesStderr = new PipedOutputStream();
        this.parentReadsChildStderr = new PipedInputStream(childWritesStderr, PIPE_BUFFER_BYTES);

        this.dispatchThread = Thread.ofVirtual()
                .name("in-proc-fake-acp")
                .unstarted(() ->
                        runDispatchLoop(childWritesStdout, childReadsStdin, childWritesStderr, opts, stderrLines));
        this.dispatchThread.start();
    }

    private void runDispatchLoop(
            PipedOutputStream stdout,
            PipedInputStream stdin,
            PipedOutputStream stderr,
            FakeAcpServer.ServerOptions opts,
            int stderrLines) {
        int exitCode = 0;
        try {
            PrintStream err = new PrintStream(stderr, true, StandardCharsets.UTF_8);
            for (int i = 0; i < stderrLines; i++) {
                err.println("stderr-noise-line-" + i);
            }
            err.flush();

            PrintStream out = new PrintStream(stdout, false, StandardCharsets.UTF_8);
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(stdin, StandardCharsets.UTF_8))) {
                FakeAcpServer.runDispatchLoop(reader, out, opts, permissionResponses::add);
            }
        } catch (IOException ex) {
            // Pipe closed by destroyForcibly — normal teardown path.
            exitCode = 143;
        } catch (Exception ex) {
            exitCode = 1;
        } finally {
            closeQuietly(stdout);
            closeQuietly(stderr);
            if (!exitFuture.isDone()) {
                exitFuture.complete(exitCode);
            }
        }
    }

    @Override
    public OutputStream getOutputStream() {
        return parentWritesChildStdin;
    }

    @Override
    public InputStream getInputStream() {
        return parentReadsChildStdout;
    }

    @Override
    public InputStream getErrorStream() {
        return parentReadsChildStderr;
    }

    @Override
    public int waitFor() throws InterruptedException {
        try {
            return exitFuture.get();
        } catch (ExecutionException ex) {
            return -1;
        }
    }

    @Override
    public boolean waitFor(long timeout, TimeUnit unit) throws InterruptedException {
        try {
            exitFuture.get(timeout, unit);
            return true;
        } catch (TimeoutException ex) {
            return false;
        } catch (ExecutionException ex) {
            return true;
        }
    }

    @Override
    public int exitValue() {
        if (!exitFuture.isDone()) {
            throw new IllegalThreadStateException("fake child still running");
        }
        try {
            return exitFuture.get();
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            return -1;
        } catch (ExecutionException ex) {
            return -1;
        }
    }

    @Override
    public void destroy() {
        destroyForcibly();
    }

    @Override
    public Process destroyForcibly() {
        closeQuietly(parentReadsChildStdout);
        closeQuietly(parentWritesChildStdin);
        closeQuietly(parentReadsChildStderr);
        if (!exitFuture.isDone()) {
            exitFuture.complete(143);
        }
        dispatchThread.interrupt();
        return this;
    }

    @Override
    public boolean isAlive() {
        return !exitFuture.isDone();
    }

    @Override
    public long pid() {
        // We never report a real OS pid; -1 is what ProcessAcpBackend's trace messages log
        // when the platform can't provide one. The value is informational, not behavioural.
        return -1L;
    }

    @Override
    public Stream<ProcessHandle> descendants() {
        // No real OS-level descendants exist; destroyTree's descendant drain loop handles
        // an empty stream as a no-op (which is the documented behaviour on platforms where
        // Process.descendants() returns empty).
        return Stream.empty();
    }

    @Override
    public CompletableFuture<Process> onExit() {
        return exitFuture.thenApply(code -> this);
    }

    /**
     * Snapshot of every response the parent sent in reply to a server-initiated
     * {@code session/request_permission}. Tests assert on this to verify the permission outcome
     * (selected option vs cancelled) the backend chose for a given policy/resolver/options shape.
     *
     * @return a defensive copy of the captured response lines, in dispatch order
     */
    List<String> permissionResponses() {
        synchronized (permissionResponses) {
            return new ArrayList<>(permissionResponses);
        }
    }

    private static void closeQuietly(java.io.Closeable closeable) {
        try {
            closeable.close();
        } catch (IOException ex) {
            log.debug("best-effort close failed on {}", closeable.getClass().getSimpleName(), ex);
        }
    }
}
