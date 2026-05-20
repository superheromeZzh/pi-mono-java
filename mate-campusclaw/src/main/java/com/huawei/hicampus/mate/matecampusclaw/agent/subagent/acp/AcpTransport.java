/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.hicampus.mate.matecampusclaw.agent.subagent.acp;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

import com.huawei.hicampus.mate.matecampusclaw.agent.subagent.SubAgentException;
import com.huawei.hicampus.mate.matecampusclaw.agent.util.LoggingUncaughtExceptionHandler;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * NdJSON transport that reads {@link AcpProtocol.Envelope} values from an {@link InputStream} on a
 * virtual thread and writes them to an {@link OutputStream}. One line == one JSON-RPC message.
 *
 * @version [br_eCampusCore 25.1.0_Next, 2026/05/12]
 * @since [br_eCampusCore 25.1.0_Next]
 */
public class AcpTransport implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(AcpTransport.class);

    /**
     * JSONL trace of every ACP envelope (in/out). Default ON to make "no answer" issues
     * diagnosable without env-var rituals — TUI swallows logback console output and Windows
     * env propagation is unreliable. Opt out with {@code CAMPUSCLAW_ACP_TRACE=0} (or
     * {@code -Dcampusclaw.acp.trace=0}). File lives at {@code ~/.campusclaw/acp-trace.jsonl}
     * (TRUNCATE on each JVM start). Writes a startup marker line so the file always exists
     * after class load — its presence/contents proves the JVM ran with the latest code.
     */
    private static final PrintWriter TRACE = openTrace();

    private static PrintWriter openTrace() {
        String env = System.getenv("CAMPUSCLAW_ACP_TRACE");
        String prop = System.getProperty("campusclaw.acp.trace");
        if (isFalsy(env) || isFalsy(prop)) {
            log.info("ACP trace: disabled by CAMPUSCLAW_ACP_TRACE=0 / -Dcampusclaw.acp.trace=0");
            return null;
        }
        try {
            Path dir = Paths.get(System.getProperty("user.home", "."), ".campusclaw");
            Files.createDirectories(dir);
            Path file = dir.resolve("acp-trace.jsonl");
            PrintWriter writer = new PrintWriter(Files.newBufferedWriter(
                    file,
                    StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.TRUNCATE_EXISTING,
                    StandardOpenOption.WRITE));
            writer.println("# acp-trace started at " + java.time.Instant.now() + " (jvm pid="
                    + ProcessHandle.current().pid() + ")");
            writer.flush();
            log.info("ACP trace: writing to {}", file.toAbsolutePath());
            return writer;
        } catch (IOException ex) {
            log.warn("ACP trace: failed to open trace file: {}", ex.toString());
            return null;
        }
    }

    private static boolean isFalsy(String s) {
        if (s == null) {
            return false;
        }
        String t = s.trim();
        return "0".equals(t) || "false".equalsIgnoreCase(t) || "off".equalsIgnoreCase(t);
    }

    private static void trace(String direction, String payload) {
        if (TRACE == null) {
            return;
        }
        synchronized (TRACE) {
            TRACE.print(direction);
            TRACE.print(' ');
            TRACE.println(payload);
            TRACE.flush();
        }
    }

    /**
     * Public diagnostic marker written to the same trace file. Used by other classes
     * (AcpClient, SpawnAgentTool) to record pipeline checkpoints alongside raw envelopes.
     *
     * @param message free-form message to record
     */
    public static void note(String message) {
        if (TRACE == null) {
            return;
        }
        synchronized (TRACE) {
            TRACE.print("# ");
            TRACE.println(message);
            TRACE.flush();
        }
    }

    private final ObjectMapper mapper;
    private final InputStream input;
    private final OutputStream output;
    private final Consumer<AcpProtocol.Envelope> onMessage;
    private final Consumer<Throwable> onError;
    private final Object writeLock = new Object();
    private final AtomicBoolean closed = new AtomicBoolean(false);
    private Thread reader;

    public AcpTransport(
            ObjectMapper mapper,
            InputStream input,
            OutputStream output,
            Consumer<AcpProtocol.Envelope> onMessage,
            Consumer<Throwable> onError) {
        this.mapper = mapper;
        this.input = input;
        this.output = output;
        this.onMessage = onMessage;
        this.onError = onError;
    }

    public void start(String name) {
        if (reader != null) {
            throw new IllegalStateException("transport already started");
        }
        reader = Thread.ofVirtual().name(name).start(this::readLoop);
    }

    public void send(AcpProtocol.Envelope envelope) {
        try {
            byte[] payload = mapper.writeValueAsBytes(envelope);
            synchronized (writeLock) {
                output.write(payload);
                output.write('\n');
                output.flush();
            }
            trace(">", new String(payload, StandardCharsets.UTF_8));
        } catch (IOException ex) {
            throw new SubAgentException("ACP_WRITE_FAILED", "failed to write ACP envelope", ex);
        }
    }

    @Override
    public void close() {
        if (!closed.compareAndSet(false, true)) {
            return;
        }

        // On Windows JDK, input.close() blocks waiting for the reader thread's pending readLine
        // to complete. That read only finishes when the pipe sees EOF, which only happens after
        // the entire child process tree is dead. ProcessAcpBackend.close kills the tree before
        // calling us, but as a defense-in-depth measure we run stream-close asynchronously so a
        // missed grandchild can't freeze the agent loop. The closer is a daemon virtual thread,
        // and once the OS tears down the orphan, input.close unblocks and the thread exits.
        Thread closer = Thread.ofVirtual().name("acp-transport-closer").unstarted(() -> {
            try {
                input.close();
            } catch (IOException e) {
                // best-effort: child may already be gone
                log.debug("AcpTransport input.close best-effort failure", e);
            }
            try {
                output.close();
            } catch (IOException e) {
                // best-effort
                log.debug("AcpTransport output.close best-effort failure", e);
            }
            note("AcpTransport.close streams closed (async)");
        });
        closer.setUncaughtExceptionHandler(LoggingUncaughtExceptionHandler.INSTANCE);
        closer.start();

        if (reader != null) {
            reader.interrupt();
        }
    }

    private void readLoop() {
        try (var bufferedReader = new BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8))) {
            String line;
            while (!closed.get() && (line = bufferedReader.readLine()) != null) {
                handleLine(line);
            }
        } catch (IOException ex) {
            if (!closed.get()) {
                fail(ex);
            }
        } catch (RuntimeException ex) {
            fail(ex);
        }
    }

    private void handleLine(String line) {
        String trimmed = line.trim();
        if (trimmed.isEmpty()) {
            return;
        }
        trace("<", trimmed);
        try {
            AcpProtocol.Envelope envelope = mapper.readValue(trimmed, AcpProtocol.Envelope.class);
            onMessage.accept(envelope);
        } catch (IOException ex) {
            log.warn("dropping malformed ACP line: {}", ex.toString());
        } catch (RuntimeException ex) {
            fail(ex);
        }
    }

    private void fail(Throwable cause) {
        if (closed.get()) {
            return;
        }
        try {
            onError.accept(cause);
        } catch (RuntimeException ex) {
            log.warn("onError handler threw: {}", ex.toString());
        }
    }
}
