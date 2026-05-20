/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.hicampus.mate.matecampusclaw.agent.subagent.acp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import com.huawei.hicampus.mate.matecampusclaw.agent.subagent.SubAgentEvent;
import com.huawei.hicampus.mate.matecampusclaw.agent.subagent.SubAgentException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

class AcpClientTest {

    private static final Logger log = LoggerFactory.getLogger(AcpClientTest.class);

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void happyPathRunsHandshakeAndStreamsMessageChunks() throws Exception {
        var clientToServer = new PipedOutputStream();
        var serverFromClient = new PipedInputStream(clientToServer, 64 * 1024);
        var serverToClient = new PipedOutputStream();
        var clientFromServer = new PipedInputStream(serverToClient, 64 * 1024);

        var serverReady = new CountDownLatch(1);
        var serverThread = Thread.ofVirtual().start(() -> runFakeServer(serverFromClient, serverToClient, serverReady));

        var client = new AcpClient(mapper, clientFromServer, clientToServer);
        client.start("acp-client-test-reader");

        var events = new ArrayList<SubAgentEvent>();
        var done = new CountDownLatch(1);
        client.events().subscribe(event -> {
            events.add(event);
            if (event instanceof SubAgentEvent.Done) {
                done.countDown();
            }
        });

        assertThat(serverReady.await(5L, TimeUnit.SECONDS)).isTrue();
        client.initialize("test-client", "1.0.0", Duration.ofSeconds(5L));
        String sessionId = client.newSession("/tmp", Duration.ofSeconds(5L));
        assertThat(sessionId).isEqualTo("sess-1");

        AcpStopReason stop = client.prompt("hello", Duration.ofSeconds(5L));
        assertThat(stop).isEqualTo(AcpStopReason.END_TURN);

        boolean reachedDone = done.await(2L, TimeUnit.SECONDS);
        assertThat(reachedDone)
                .as("Done event was not received; captured=%s", events)
                .isTrue();

        boolean sawText = events.stream()
                .anyMatch(e ->
                        e instanceof SubAgentEvent.TextDelta td && td.text().equals("world"));
        assertThat(sawText).isTrue();

        // Done must arrive strictly after every TextDelta — Done is now emitted on the reader
        // thread from handleResponse(METHOD_PROMPT), which guarantees no in-flight update can be
        // dropped by downstream takeUntil(Done). See AcpClient.handleResponse.
        int doneIndex = -1;
        int lastTextIndex = -1;
        for (int i = 0; i < events.size(); i++) {
            SubAgentEvent e = events.get(i);
            if (e instanceof SubAgentEvent.Done) {
                doneIndex = i;
            } else if (e instanceof SubAgentEvent.TextDelta) {
                lastTextIndex = i;
            }
        }
        assertThat(doneIndex).as("Done event index").isGreaterThanOrEqualTo(0);
        assertThat(doneIndex).as("Done must follow every TextDelta").isGreaterThan(lastTextIndex);

        client.close();
        serverThread.join(2_000L);
    }

    private void runFakeServer(PipedInputStream input, OutputStream output, CountDownLatch ready) {
        try (var reader = new BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8))) {
            ready.countDown();
            handleInitialize(reader, output);
            handleNewSession(reader, output);
            handlePrompt(reader, output);
            output.flush();

            // Block until the client closes its end of the pipe, otherwise the piped reader on the
            // client side raises "Write end dead" as soon as this thread exits and races the Done
            // emit from AcpClient.prompt().
            while (reader.readLine() != null) {
                // drain quietly
            }
        } catch (Exception e) {
            // closed
            log.debug("ACP test server reader exited (pipe closed)", e);
        }
    }

    private void handleInitialize(BufferedReader reader, OutputStream output) throws Exception {
        AcpProtocol.Envelope envelope = readEnvelope(reader);
        assertThat(envelope.method()).isEqualTo(AcpProtocol.METHOD_INITIALIZE);
        var result = mapper.valueToTree(new AcpProtocol.InitializeResponse(1, null, null, null));
        writeLine(output, AcpProtocol.Envelope.ok(envelope.id(), result));
    }

    private void handleNewSession(BufferedReader reader, OutputStream output) throws Exception {
        AcpProtocol.Envelope envelope = readEnvelope(reader);
        assertThat(envelope.method()).isEqualTo(AcpProtocol.METHOD_NEW_SESSION);
        var result = mapper.valueToTree(new AcpProtocol.NewSessionResponse("sess-1"));
        writeLine(output, AcpProtocol.Envelope.ok(envelope.id(), result));
    }

    private void handlePrompt(BufferedReader reader, OutputStream output) throws Exception {
        AcpProtocol.Envelope envelope = readEnvelope(reader);
        assertThat(envelope.method()).isEqualTo(AcpProtocol.METHOD_PROMPT);

        var updatePayload = mapper.createObjectNode();
        updatePayload.put("sessionId", "sess-1");
        var updateBody = mapper.createObjectNode();
        updateBody.put("sessionUpdate", AcpProtocol.UPDATE_AGENT_MESSAGE);
        var content = mapper.createObjectNode();
        content.put("type", "text");
        content.put("text", "world");
        updateBody.set("content", content);
        updatePayload.set("update", updateBody);
        writeLine(output, AcpProtocol.Envelope.notification(AcpProtocol.METHOD_UPDATE, updatePayload));

        var promptResult = mapper.valueToTree(new AcpProtocol.PromptResponse("end_turn"));
        writeLine(output, AcpProtocol.Envelope.ok(envelope.id(), promptResult));
    }

    private AcpProtocol.Envelope readEnvelope(BufferedReader reader) throws Exception {
        String line = reader.readLine();
        return mapper.readValue(line, AcpProtocol.Envelope.class);
    }

    private void writeLine(OutputStream output, AcpProtocol.Envelope envelope) throws Exception {
        output.write(mapper.writeValueAsBytes(envelope));
        output.write('\n');
        output.flush();
    }

    @SuppressWarnings("unused")
    private static List<JsonNode> emptyOptions() {
        return List.of();
    }

    @Test
    void promptWithoutSessionThrowsSubAgentException() throws Exception {
        try (var clientToServer = new PipedOutputStream();
                var serverToClient = new PipedOutputStream();
                var clientFromServer = new PipedInputStream(serverToClient, 4096)) {
            var client = new AcpClient(mapper, clientFromServer, clientToServer);
            assertThatThrownBy(() -> client.prompt("text", Duration.ofMillis(500L)))
                    .isInstanceOf(SubAgentException.class)
                    .hasMessageContaining("session/new must be called");
            client.close();
        }
    }

    @Test
    void cancelWithoutSessionIsNoOp() throws Exception {
        try (var clientToServer = new PipedOutputStream();
                var serverToClient = new PipedOutputStream();
                var clientFromServer = new PipedInputStream(serverToClient, 4096)) {
            var client = new AcpClient(mapper, clientFromServer, clientToServer);

            // Should silently no-op since sessionId is null.
            client.cancel();
            client.close();
        }
    }

    @Test
    void setPermissionHandlerRejectsNull() throws Exception {
        try (var clientToServer = new PipedOutputStream();
                var serverToClient = new PipedOutputStream();
                var clientFromServer = new PipedInputStream(serverToClient, 4096)) {
            var client = new AcpClient(mapper, clientFromServer, clientToServer);
            assertThatThrownBy(() -> client.setPermissionHandler(null)).isInstanceOf(IllegalArgumentException.class);
            client.close();
        }
    }

    @Test
    void permissionHandlerIsInvokedForInboundRequestPermission() throws Exception {
        var clientToServer = new PipedOutputStream();
        var serverFromClient = new PipedInputStream(clientToServer, 64 * 1024);
        var serverToClient = new PipedOutputStream();
        var clientFromServer = new PipedInputStream(serverToClient, 64 * 1024);

        var serverReady = new CountDownLatch(1);
        var responded = new CountDownLatch(1);
        Thread serverThread = Thread.ofVirtual().start(() -> {
            try (var reader = new BufferedReader(new InputStreamReader(serverFromClient, StandardCharsets.UTF_8))) {
                serverReady.countDown();
                var req = mapper.createObjectNode();
                req.put("sessionId", "sess-perm");
                req.put("toolCall", "delete-file");
                var options = mapper.createArrayNode();
                var opt = mapper.createObjectNode();
                opt.put("optionId", "allow_once");
                opt.put("name", "Allow once");
                opt.put("kind", "allow_once");
                options.add(opt);
                req.set("options", options);
                writeLine(
                        serverToClient, AcpProtocol.Envelope.request(99L, AcpProtocol.METHOD_REQUEST_PERMISSION, req));
                String reply = reader.readLine();
                if (reply != null && reply.contains("\"id\":99")) {
                    responded.countDown();
                }
                while (reader.readLine() != null) {
                    // drain
                }
            } catch (Exception e) {
                // closed
                log.debug("ACP test reader thread exited (pipe closed)", e);
            }
        });

        var client = new AcpClient(mapper, clientFromServer, clientToServer);
        var invoked = new java.util.concurrent.atomic.AtomicBoolean();
        client.setPermissionHandler((request, ctx) -> {
            invoked.set(true);
            return AcpProtocol.RequestPermissionResponse.Outcome.selected("allow_once");
        });
        client.start("acp-perm-test");

        assertThat(serverReady.await(5L, TimeUnit.SECONDS)).isTrue();
        assertThat(responded.await(5L, TimeUnit.SECONDS))
                .as("client must answer the permission request envelope")
                .isTrue();
        assertThat(invoked.get())
                .as("PermissionHandler must be invoked for METHOD_REQUEST_PERMISSION")
                .isTrue();

        client.close();
        serverThread.join(2_000L);
    }

    @Test
    void eventsFluxCompletesOnClose() throws Exception {
        try (var clientToServer = new PipedOutputStream();
                var serverToClient = new PipedOutputStream();
                var clientFromServer = new PipedInputStream(serverToClient, 4096)) {
            var client = new AcpClient(mapper, clientFromServer, clientToServer);
            var completed = new CountDownLatch(1);
            client.events().subscribe(e -> {}, err -> completed.countDown(), completed::countDown);
            client.close();
            assertThat(completed.await(2L, TimeUnit.SECONDS))
                    .as("events flux must complete (or error) when client.close() is invoked")
                    .isTrue();
        }
    }
}
