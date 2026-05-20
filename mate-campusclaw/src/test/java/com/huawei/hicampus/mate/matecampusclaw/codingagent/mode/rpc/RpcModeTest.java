/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.hicampus.mate.matecampusclaw.codingagent.mode.rpc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import com.huawei.hicampus.mate.matecampusclaw.agent.event.AgentEndEvent;
import com.huawei.hicampus.mate.matecampusclaw.agent.event.AgentEventListener;
import com.huawei.hicampus.mate.matecampusclaw.agent.event.AgentStartEvent;
import com.huawei.hicampus.mate.matecampusclaw.agent.event.MessageEndEvent;
import com.huawei.hicampus.mate.matecampusclaw.agent.event.MessageStartEvent;
import com.huawei.hicampus.mate.matecampusclaw.agent.event.MessageUpdateEvent;
import com.huawei.hicampus.mate.matecampusclaw.agent.event.ToolExecutionEndEvent;
import com.huawei.hicampus.mate.matecampusclaw.agent.event.ToolExecutionStartEvent;
import com.huawei.hicampus.mate.matecampusclaw.ai.types.UserMessage;
import com.huawei.hicampus.mate.matecampusclaw.codingagent.session.AgentSession;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class RpcModeTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Mock
    AgentSession session;

    ByteArrayOutputStream outBytes;
    PrintStream out;
    InputStream originalIn;
    RpcMode rpcMode;

    @BeforeEach
    void setUp() {
        outBytes = new ByteArrayOutputStream();
        out = new PrintStream(outBytes, true, StandardCharsets.UTF_8);
        originalIn = System.in;
        rpcMode = new RpcMode(session, out);
    }

    @AfterEach
    void tearDown() {
        System.setIn(originalIn);
    }

    // ---------- helpers ----------

    private void feedStdin(String content) {
        System.setIn(new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8)));
    }

    private List<JsonNode> stdoutLines() throws Exception {
        var result = new ArrayList<JsonNode>();
        for (var line : outBytes.toString(StandardCharsets.UTF_8).split("\n")) {
            if (!line.isBlank()) {
                result.add(MAPPER.readTree(line));
            }
        }
        return result;
    }

    private AgentEventListener captureListener() {
        var captor = ArgumentCaptor.forClass(AgentEventListener.class);
        verify(session).subscribe(captor.capture());
        return captor.getValue();
    }

    // ---------- constructor contracts ----------

    @Test
    void constructorRejectsNullSession() {
        // given / when / then
        assertThrows(NullPointerException.class, () -> new RpcMode(null, out));
    }

    @Test
    void constructorRejectsNullOut() {
        // given / when / then
        assertThrows(NullPointerException.class, () -> new RpcMode(session, null));
    }

    @Test
    void defaultConstructor_wiresSessionAndUsesSystemOut() throws Exception {
        // given: redirect System.out so the default constructor's stdout
        // routing doesn't pollute the test runner's output
        PrintStream originalOut = System.out;
        ByteArrayOutputStream capturedSystemOut = new ByteArrayOutputStream();
        System.setOut(new PrintStream(capturedSystemOut, true, StandardCharsets.UTF_8));
        try {
            feedStdin("{\"type\":\"abort\",\"id\":\"d1\"}\n");

            // when
            new RpcMode(session).run();

            // then: the default constructor produced a working instance
            // that subscribed to the session and routed protocol output
            // through System.out (the only stdout target available to it)
            verify(session).subscribe(any(AgentEventListener.class));
            verify(session).abort();
            JsonNode ev = MAPPER.readTree(
                    capturedSystemOut.toString(StandardCharsets.UTF_8).trim());
            assertEquals("ack", ev.get("type").asText());
            assertEquals("d1", ev.get("id").asText());
        } finally {
            System.setOut(originalOut);
        }
    }

    // ---------- stdin loop ----------

    @Test
    void run_skipsEmptyAndBlankLines() throws Exception {
        // given
        feedStdin("\n   \n\t\n");

        // when
        rpcMode.run();

        // then
        verify(session, never()).prompt(any());
        verify(session, never()).abort();
        assertTrue(stdoutLines().isEmpty());
    }

    @Test
    void run_emitsErrorOnMalformedJson() throws Exception {
        // given
        feedStdin("not json at all\n");

        // when
        rpcMode.run();

        // then
        var events = stdoutLines();
        assertEquals(1, events.size());
        assertEquals("error", events.get(0).get("type").asText());
        assertTrue(events.get(0).get("error").asText().startsWith("Invalid command:"));
    }

    // ---------- handleCommand switch ----------

    @Test
    void handle_prompt_callsSessionPromptAndEmitsAck() throws Exception {
        // given
        when(session.prompt(any())).thenReturn(CompletableFuture.completedFuture(null));
        feedStdin("{\"type\":\"prompt\",\"id\":\"r1\",\"message\":\"hello\"}\n");

        // when
        rpcMode.run();

        // then
        verify(session).prompt("hello");
        var events = stdoutLines();
        assertEquals(1, events.size());
        assertEquals("ack", events.get(0).get("type").asText());
        assertEquals("r1", events.get(0).get("id").asText());
    }

    @Test
    void handle_prompt_skipsWhenMessageNull() throws Exception {
        // given
        feedStdin("{\"type\":\"prompt\",\"id\":\"r1\"}\n");

        // when
        rpcMode.run();

        // then
        verify(session, never()).prompt(any());
        assertTrue(stdoutLines().isEmpty());
    }

    @Test
    void handle_steer_callsSessionSteerAndEmitsAck() throws Exception {
        // given
        feedStdin("{\"type\":\"steer\",\"id\":\"r2\",\"message\":\"slow down\"}\n");

        // when
        rpcMode.run();

        // then
        verify(session).steer("slow down");
        var events = stdoutLines();
        assertEquals("ack", events.get(0).get("type").asText());
    }

    @Test
    void handle_steer_skipsWhenMessageNull() throws Exception {
        // given
        feedStdin("{\"type\":\"steer\",\"id\":\"r2\"}\n");

        // when
        rpcMode.run();

        // then
        verify(session, never()).steer(any());
        assertTrue(stdoutLines().isEmpty());
    }

    @Test
    void handle_abort_callsSessionAbort() throws Exception {
        // given
        feedStdin("{\"type\":\"abort\",\"id\":\"r3\"}\n");

        // when
        rpcMode.run();

        // then
        verify(session).abort();
        var events = stdoutLines();
        assertEquals("ack", events.get(0).get("type").asText());
        assertEquals("r3", events.get(0).get("id").asText());
    }

    @Test
    void handle_getState_returnsModelAndStreamingFlag() throws Exception {
        // given
        when(session.getModelId()).thenReturn("glm-5");
        when(session.isStreaming()).thenReturn(true);
        feedStdin("{\"type\":\"get_state\",\"id\":\"r4\"}\n");

        // when
        rpcMode.run();

        // then
        var events = stdoutLines();
        assertEquals(1, events.size());
        var ev = events.get(0);
        assertEquals("state", ev.get("type").asText());
        assertEquals("r4", ev.get("id").asText());
        assertEquals("glm-5", ev.get("data").get("model").asText());
        assertTrue(ev.get("data").get("isStreaming").asBoolean());
    }

    @Test
    void handle_setModel_callsSessionSetModel() throws Exception {
        // given
        feedStdin("{\"type\":\"set_model\",\"id\":\"r5\",\"model\":\"glm-5-pro\"}\n");

        // when
        rpcMode.run();

        // then
        verify(session).setModel("glm-5-pro");
        assertEquals("ack", stdoutLines().get(0).get("type").asText());
    }

    @Test
    void handle_setModel_skipsWhenModelNull() throws Exception {
        // given
        feedStdin("{\"type\":\"set_model\",\"id\":\"r5\"}\n");

        // when
        rpcMode.run();

        // then
        verify(session, never()).setModel(any());
        assertTrue(stdoutLines().isEmpty());
    }

    @Test
    void handle_newSession_callsSessionNewSession() throws Exception {
        // given
        feedStdin("{\"type\":\"new_session\",\"id\":\"r6\"}\n");

        // when
        rpcMode.run();

        // then
        verify(session).newSession();
        assertEquals("ack", stdoutLines().get(0).get("type").asText());
    }

    @Test
    void handle_unknownType_emitsError() throws Exception {
        // given
        feedStdin("{\"type\":\"bogus\",\"id\":\"r7\"}\n");

        // when
        rpcMode.run();

        // then
        var events = stdoutLines();
        assertEquals(1, events.size());
        assertEquals("error", events.get(0).get("type").asText());
        assertEquals("r7", events.get(0).get("id").asText());
        assertTrue(events.get(0).get("error").asText().contains("Unknown command"));
    }

    @Test
    void handle_exceptionDuringDispatch_emitsErrorWithMessage() throws Exception {
        // given
        doThrow(new RuntimeException("session failed")).when(session).abort();
        feedStdin("{\"type\":\"abort\",\"id\":\"r8\"}\n");

        // when
        rpcMode.run();

        // then
        var events = stdoutLines();
        assertEquals(1, events.size());
        var ev = events.get(0);
        assertEquals("error", ev.get("type").asText());
        assertEquals("r8", ev.get("id").asText());
        assertEquals("session failed", ev.get("error").asText());
    }

    // ---------- subscribe lambda (event forwarding) ----------

    @Test
    void run_forwardsMessageStartEvent() throws Exception {
        // given
        feedStdin("");
        rpcMode.run();
        AgentEventListener listener = captureListener();

        // when
        listener.onEvent(new MessageStartEvent(new UserMessage("hi", 0L)));

        // then
        var events = stdoutLines();
        assertEquals(1, events.size());
        assertEquals("message_start", events.get(0).get("type").asText());
    }

    @Test
    void run_forwardsMessageUpdateEvent_whenMessagePresent() throws Exception {
        // given
        feedStdin("");
        rpcMode.run();
        AgentEventListener listener = captureListener();

        // when
        listener.onEvent(new MessageUpdateEvent(new UserMessage("partial", 0L), null));

        // then
        var events = stdoutLines();
        assertEquals(1, events.size());
        assertEquals("message_update", events.get(0).get("type").asText());
        assertTrue(events.get(0).has("data"));
    }

    @Test
    void run_skipsMessageUpdateEvent_whenMessageNull() throws Exception {
        // given
        feedStdin("");
        rpcMode.run();
        AgentEventListener listener = captureListener();

        // when
        listener.onEvent(new MessageUpdateEvent(null, null));

        // then
        assertTrue(stdoutLines().isEmpty());
    }

    @Test
    void run_forwardsMessageEndEvent() throws Exception {
        // given
        feedStdin("");
        rpcMode.run();
        AgentEventListener listener = captureListener();

        // when
        listener.onEvent(new MessageEndEvent(new UserMessage("done", 0L)));

        // then
        var events = stdoutLines();
        assertEquals(1, events.size());
        assertEquals("message_end", events.get(0).get("type").asText());
    }

    @Test
    void run_forwardsToolExecutionStartEvent() throws Exception {
        // given
        feedStdin("");
        rpcMode.run();
        AgentEventListener listener = captureListener();

        // when
        listener.onEvent(new ToolExecutionStartEvent("call-1", "Read", null));

        // then
        var events = stdoutLines();
        assertEquals(1, events.size());
        assertEquals("tool_start", events.get(0).get("type").asText());
        assertEquals("call-1", events.get(0).get("data").get("toolCallId").asText());
        assertEquals("Read", events.get(0).get("data").get("toolName").asText());
    }

    @Test
    void run_forwardsToolExecutionEndEvent() throws Exception {
        // given
        feedStdin("");
        rpcMode.run();
        AgentEventListener listener = captureListener();

        // when
        listener.onEvent(new ToolExecutionEndEvent("call-1", "Read", "ok", false));

        // then
        var events = stdoutLines();
        assertEquals(1, events.size());
        assertEquals("tool_end", events.get(0).get("type").asText());
        assertEquals("call-1", events.get(0).get("data").get("toolCallId").asText());
    }

    @Test
    void run_forwardsAgentEndEventAsDone() throws Exception {
        // given
        feedStdin("");
        rpcMode.run();
        AgentEventListener listener = captureListener();

        // when
        listener.onEvent(new AgentEndEvent(List.of()));

        // then
        var events = stdoutLines();
        assertEquals(1, events.size());
        assertEquals("done", events.get(0).get("type").asText());
    }

    @Test
    void run_ignoresUnrelatedEvent() throws Exception {
        // given
        feedStdin("");
        rpcMode.run();
        AgentEventListener listener = captureListener();

        // when
        listener.onEvent(new AgentStartEvent());

        // then
        assertTrue(stdoutLines().isEmpty());
    }
}
