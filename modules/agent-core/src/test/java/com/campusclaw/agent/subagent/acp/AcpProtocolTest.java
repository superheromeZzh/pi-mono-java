/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.campusclaw.agent.subagent.acp;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import com.fasterxml.jackson.databind.ObjectMapper;

import org.junit.jupiter.api.Test;

class AcpProtocolTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void initializeRequestSerialisesToWireShape() throws Exception {
        var req = new AcpProtocol.InitializeRequest(
                1, AcpProtocol.ClientCapabilities.none(), new AcpProtocol.ClientInfo("test", "1.0.0"));
        String json = mapper.writeValueAsString(req);

        assertThat(json).contains("\"protocolVersion\":1");
        assertThat(json).contains("\"clientInfo\":{\"name\":\"test\",\"version\":\"1.0.0\"}");
    }

    @Test
    void promptRequestEncodesContentBlocks() throws Exception {
        var req = new AcpProtocol.PromptRequest("sess-1", List.of(AcpProtocol.ContentBlock.text("hi")));
        String json = mapper.writeValueAsString(req);

        assertThat(json).contains("\"sessionId\":\"sess-1\"");
        assertThat(json).contains("\"type\":\"text\"");
        assertThat(json).contains("\"text\":\"hi\"");
    }

    @Test
    void envelopeRequestRoundTrips() throws Exception {
        var params = mapper.valueToTree(new AcpProtocol.CancelRequest("sess-1"));
        var envelope = AcpProtocol.Envelope.request(7L, AcpProtocol.METHOD_CANCEL, params);

        String json = mapper.writeValueAsString(envelope);
        AcpProtocol.Envelope decoded = mapper.readValue(json, AcpProtocol.Envelope.class);

        assertThat(decoded.method()).isEqualTo(AcpProtocol.METHOD_CANCEL);
        assertThat(decoded.isRequest()).isTrue();
        assertThat(decoded.params().get("sessionId").asText()).isEqualTo("sess-1");
    }

    @Test
    void envelopeNotificationHasNullId() throws Exception {
        var envelope = AcpProtocol.Envelope.notification(AcpProtocol.METHOD_UPDATE, mapper.createObjectNode());
        String json = mapper.writeValueAsString(envelope);

        assertThat(json).doesNotContain("\"id\":");
        var decoded = mapper.readValue(json, AcpProtocol.Envelope.class);
        assertThat(decoded.isNotification()).isTrue();
    }

    @Test
    void envelopeResponseClassifiesCorrectly() throws Exception {
        String raw = "{\"jsonrpc\":\"2.0\",\"id\":3,\"result\":{\"sessionId\":\"abc\"}}";
        var envelope = mapper.readValue(raw, AcpProtocol.Envelope.class);

        assertThat(envelope.isResponse()).isTrue();
        assertThat(envelope.result().get("sessionId").asText()).isEqualTo("abc");
    }

    @Test
    void stopReasonMapsKnownAndUnknownWireValues() {
        assertThat(AcpStopReason.fromWire("end_turn")).isEqualTo(AcpStopReason.END_TURN);
        assertThat(AcpStopReason.fromWire("max_tokens")).isEqualTo(AcpStopReason.MAX_TOKENS);
        assertThat(AcpStopReason.fromWire("cancelled")).isEqualTo(AcpStopReason.CANCELLED);
        assertThat(AcpStopReason.fromWire("anything-else")).isEqualTo(AcpStopReason.END_TURN);
        assertThat(AcpStopReason.fromWire(null)).isEqualTo(AcpStopReason.END_TURN);
    }
}
