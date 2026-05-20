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

    @Test
    void decodesThoughtStreamTextDelta() {
        SubAgentEvent event = HttpAgentProtocol.decodeEvent(
                "{\"type\":\"text_delta\",\"stream\":\"thought\",\"text\":\"thinking\"}", mapper);
        var delta = (SubAgentEvent.TextDelta) event;
        assertThat(delta.stream()).isEqualTo(SubAgentEvent.Stream.THOUGHT);
        assertThat(delta.text()).isEqualTo("thinking");
    }

    @Test
    void toolCallStatusVariantsAllParse() {
        assertThat(((SubAgentEvent.ToolCall) HttpAgentProtocol.decodeEvent(
                                "{\"type\":\"tool_call\",\"toolCallId\":\"t1\",\"name\":\"x\",\"status\":\"failed\"}",
                                mapper))
                        .status())
                .isEqualTo(SubAgentEvent.ToolCallStatus.FAILED);
        assertThat(((SubAgentEvent.ToolCall) HttpAgentProtocol.decodeEvent(
                                "{\"type\":\"tool_call\",\"toolCallId\":\"t1\",\"name\":\"x\",\"status\":\"started\"}",
                                mapper))
                        .status())
                .isEqualTo(SubAgentEvent.ToolCallStatus.STARTED);
        assertThat(((SubAgentEvent.ToolCall) HttpAgentProtocol.decodeEvent(
                                "{\"type\":\"tool_call\",\"toolCallId\":\"t1\",\"name\":\"x\",\"status\":\"weird\"}",
                                mapper))
                        .status())
                .isEqualTo(SubAgentEvent.ToolCallStatus.IN_PROGRESS);
    }

    @Test
    void toolCallFallsBackToNameWhenTitleAbsent() {
        var call = (SubAgentEvent.ToolCall) HttpAgentProtocol.decodeEvent(
                "{\"type\":\"tool_call\",\"toolCallId\":\"t1\",\"name\":\"bash\",\"status\":\"started\"}", mapper);
        assertThat(call.title()).isEqualTo("bash");
    }

    @Test
    void decodesStatusEventWithDetailsMap() {
        var status = (SubAgentEvent.Status) HttpAgentProtocol.decodeEvent(
                "{\"type\":\"status\",\"summary\":\"running\",\"details\":{\"step\":2,\"flag\":true}}", mapper);
        assertThat(status.summary()).isEqualTo("running");
        assertThat(status.details()).containsEntry("step", 2);
        assertThat(status.details()).containsEntry("flag", true);
    }

    @Test
    void decodesStatusEventWithoutDetails() {
        var status = (SubAgentEvent.Status)
                HttpAgentProtocol.decodeEvent("{\"type\":\"status\",\"summary\":\"working\"}", mapper);
        assertThat(status.summary()).isEqualTo("working");
        assertThat(status.details()).isEmpty();
    }

    @Test
    void decodesPermissionRequest() {
        var req = (SubAgentEvent.PermissionRequest) HttpAgentProtocol.decodeEvent(
                "{\"type\":\"permission_request\",\"requestId\":\"r1\",\"toolName\":\"write\","
                        + "\"params\":{\"path\":\"/tmp/x\"}}",
                mapper);
        assertThat(req.requestId()).isEqualTo("r1");
        assertThat(req.toolName()).isEqualTo("write");
        assertThat(req.params()).containsEntry("path", "/tmp/x");
    }

    @Test
    void decodesPermissionRequestWithoutParams() {
        var req = (SubAgentEvent.PermissionRequest) HttpAgentProtocol.decodeEvent(
                "{\"type\":\"permission_request\",\"requestId\":\"r1\",\"toolName\":\"write\"}", mapper);
        assertThat(req.params()).isEmpty();
    }

    @Test
    void doneStopReasonVariants() {
        assertThat(((SubAgentEvent.Done)
                                HttpAgentProtocol.decodeEvent("{\"type\":\"done\",\"stopReason\":\"refusal\"}", mapper))
                        .stopReason())
                .isEqualTo(SubAgentEvent.StopReason.REFUSAL);
        assertThat(((SubAgentEvent.Done) HttpAgentProtocol.decodeEvent(
                                "{\"type\":\"done\",\"stopReason\":\"cancelled\"}", mapper))
                        .stopReason())
                .isEqualTo(SubAgentEvent.StopReason.CANCELLED);
        assertThat(((SubAgentEvent.Done)
                                HttpAgentProtocol.decodeEvent("{\"type\":\"done\",\"stopReason\":\"error\"}", mapper))
                        .stopReason())
                .isEqualTo(SubAgentEvent.StopReason.ERROR);
        assertThat(((SubAgentEvent.Done) HttpAgentProtocol.decodeEvent(
                                "{\"type\":\"done\",\"stopReason\":\"whatever\"}", mapper))
                        .stopReason())
                .isEqualTo(SubAgentEvent.StopReason.END_TURN);
    }

    @Test
    void errorEventDefaultsRetryableToFalse() {
        var err = (SubAgentEvent.Error)
                HttpAgentProtocol.decodeEvent("{\"type\":\"error\",\"code\":\"OOPS\",\"message\":\"boom\"}", mapper);
        assertThat(err.retryable()).isFalse();
    }

    @Test
    void requestRecordsExposeFields() {
        var req = new HttpAgentProtocol.NewSessionRequest("parent-1", "/tmp", "claude", "high");
        assertThat(req.parentAgentId()).isEqualTo("parent-1");
        assertThat(req.cwd()).isEqualTo("/tmp");
        assertThat(req.model()).isEqualTo("claude");
        assertThat(req.thinking()).isEqualTo("high");

        var resp = new HttpAgentProtocol.NewSessionResponse("sess-1");
        assertThat(resp.sessionId()).isEqualTo("sess-1");

        var prompt = new HttpAgentProtocol.PromptRequest("write tests");
        assertThat(prompt.task()).isEqualTo("write tests");

        var cancel = new HttpAgentProtocol.CancelRequest("user-aborted");
        assertThat(cancel.reason()).isEqualTo("user-aborted");
    }
}
