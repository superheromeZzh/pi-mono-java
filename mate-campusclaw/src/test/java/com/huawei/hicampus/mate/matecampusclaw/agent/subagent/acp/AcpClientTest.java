/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.hicampus.mate.matecampusclaw.agent.subagent.acp;

import static org.assertj.core.api.Assertions.assertThat;

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
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.junit.jupiter.api.Test;

class AcpClientTest {

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
        } catch (Exception ignored) {
            // closed
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
}
