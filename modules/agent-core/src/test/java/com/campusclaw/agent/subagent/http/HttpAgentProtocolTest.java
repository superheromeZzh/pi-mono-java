/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.campusclaw.agent.subagent.http;

import static org.assertj.core.api.Assertions.assertThat;

import com.campusclaw.agent.subagent.SubAgentEvent;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.junit.jupiter.api.Test;

class HttpAgentProtocolTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void decodesTextDelta() {
        SubAgentEvent event = HttpAgentProtocol.decodeEvent(
                "{\"type\":\"text_delta\",\"stream\":\"output\",\"text\":\"hi\"}", mapper);
        assertThat(event).isInstanceOf(SubAgentEvent.TextDelta.class);
        var delta = (SubAgentEvent.TextDelta) event;
        assertThat(delta.stream()).isEqualTo(SubAgentEvent.Stream.OUTPUT);
        assertThat(delta.text()).isEqualTo("hi");
    }

    @Test
    void decodesToolCallWithStatus() {
        SubAgentEvent event = HttpAgentProtocol.decodeEvent(
                "{\"type\":\"tool_call\",\"toolCallId\":\"t1\",\"name\":\"read\",\"status\":\"completed\"}", mapper);
        assertThat(event).isInstanceOf(SubAgentEvent.ToolCall.class);
        var call = (SubAgentEvent.ToolCall) event;
        assertThat(call.name()).isEqualTo("read");
        assertThat(call.status()).isEqualTo(SubAgentEvent.ToolCallStatus.COMPLETED);
    }

    @Test
    void decodesDoneWithKnownStopReason() {
        SubAgentEvent event =
                HttpAgentProtocol.decodeEvent("{\"type\":\"done\",\"stopReason\":\"max_tokens\"}", mapper);
        assertThat(event).isInstanceOf(SubAgentEvent.Done.class);
        assertThat(((SubAgentEvent.Done) event).stopReason()).isEqualTo(SubAgentEvent.StopReason.MAX_TOKENS);
    }

    @Test
    void decodesErrorRetryableFlag() {
        SubAgentEvent event = HttpAgentProtocol.decodeEvent(
                "{\"type\":\"error\",\"code\":\"BUSY\",\"message\":\"throttled\",\"retryable\":true}", mapper);
        var error = (SubAgentEvent.Error) event;
        assertThat(error.code()).isEqualTo("BUSY");
        assertThat(error.retryable()).isTrue();
    }

    @Test
    void unknownTypeYieldsNull() {
        SubAgentEvent event = HttpAgentProtocol.decodeEvent("{\"type\":\"???\"}", mapper);
        assertThat(event).isNull();
    }

    @Test
    void emptyLineYieldsNull() {
        assertThat(HttpAgentProtocol.decodeEvent("", mapper)).isNull();
        assertThat(HttpAgentProtocol.decodeEvent("   ", mapper)).isNull();
        assertThat(HttpAgentProtocol.decodeEvent(null, mapper)).isNull();
    }

    @Test
    void malformedLineProducesErrorEvent() {
        SubAgentEvent event = HttpAgentProtocol.decodeEvent("not json", mapper);
        assertThat(event).isInstanceOf(SubAgentEvent.Error.class);
        assertThat(((SubAgentEvent.Error) event).code()).isEqualTo("HTTP_BAD_EVENT");
    }
}
