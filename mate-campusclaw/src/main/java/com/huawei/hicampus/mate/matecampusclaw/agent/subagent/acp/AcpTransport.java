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
     * Opt-in JSONL trace of every ACP envelope (in/out). Enable via either
     * {@code CAMPUSCLAW_ACP_TRACE=1} env var or {@code -Dcampusclaw.acp.trace=1} JVM property.
     * File lives at {@code ~/.campusclaw/acp-trace.jsonl} (TRUNCATE on each JVM start).
     * Used to diagnose "no answer" issues when TUI rendering swallows console logs.
     */
    private static final PrintWriter TRACE = openTrace();

    private static PrintWriter openTrace() {
        String env = System.getenv("CAMPUSCLAW_ACP_TRACE");
        String prop = System.getProperty("campusclaw.acp.trace");
        boolean enabled = isTruthy(env) || isTruthy(prop);
        if (!enabled) {
            log.info("ACP trace: disabled (set CAMPUSCLAW_ACP_TRACE=1 or -Dcampusclaw.acp.trace=1)");
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
            log.info("ACP trace: writing to {}", file.toAbsolutePath());
            return writer;
        } catch (IOException ex) {
            log.warn("ACP trace: failed to open trace file: {}", ex.toString());
            return null;
        }
    }

    private static boolean isTruthy(String s) {
        return s != null && !s.isBlank() && !"0".equals(s.trim()) && !"false".equalsIgnoreCase(s.trim());
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
        try {
            input.close();
        } catch (IOException ignored) {
            // best-effort
        }
        try {
            output.close();
        } catch (IOException ignored) {
            // best-effort
        }
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
