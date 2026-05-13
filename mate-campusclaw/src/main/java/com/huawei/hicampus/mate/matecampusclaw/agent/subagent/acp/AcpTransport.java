/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.hicampus.mate.matecampusclaw.agent.subagent.acp;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
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
