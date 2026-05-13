/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.campusclaw.codingagent.mode.acp;

import java.io.InputStream;
import java.io.OutputStream;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;

import com.campusclaw.agent.event.AgentEndEvent;
import com.campusclaw.agent.event.MessageEndEvent;
import com.campusclaw.agent.event.MessageUpdateEvent;
import com.campusclaw.agent.event.ToolExecutionEndEvent;
import com.campusclaw.agent.event.ToolExecutionStartEvent;
import com.campusclaw.agent.subagent.acp.AcpProtocol;
import com.campusclaw.agent.subagent.acp.AcpTransport;
import com.campusclaw.ai.types.AssistantMessage;
import com.campusclaw.ai.types.ContentBlock;
import com.campusclaw.ai.types.Message;
import com.campusclaw.ai.types.TextContent;
import com.campusclaw.codingagent.session.AgentSession;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Runs CampusClaw as an ACP server. Reads JSON-RPC envelopes from {@code stdin} and writes
 * responses + {@code session/update} notifications to {@code stdout}.
 *
 * <p>Wire methods supported: {@code initialize}, {@code session/new}, {@code session/prompt},
 * {@code session/cancel}. The single underlying {@link AgentSession} is reused across one ACP
 * {@code sessionId}; calling {@code session/new} again resets the conversation.
 *
 * @version [br_eCampusCore 25.1.0_Next, 2026/05/12]
 * @since [br_eCampusCore 25.1.0_Next]
 */
public class AcpMode {

    private static final Logger log = LoggerFactory.getLogger(AcpMode.class);

    private final ObjectMapper mapper;
    private final AgentSession session;
    private final InputStream input;
    private final OutputStream output;

    private AcpTransport transport;
    private volatile String currentSessionId;
    private volatile CompletableFuture<Void> activeTurn;
    private final StringBuilder turnText = new StringBuilder();
    private final AtomicReference<Long> activePromptId = new AtomicReference<>();

    public AcpMode(AgentSession session) {
        this(session, new ObjectMapper(), System.in, System.out);
    }

    public AcpMode(AgentSession session, ObjectMapper mapper, InputStream input, OutputStream output) {
        this.session = session;
        this.mapper = mapper;
        this.input = input;
        this.output = output;
    }

    public void run() {
        transport = new AcpTransport(mapper, input, output, this::onEnvelope, this::onTransportError);
        session.subscribe(event -> {
            if (event instanceof MessageUpdateEvent mu) {
                handleMessageUpdate(mu);
            } else if (event instanceof MessageEndEvent me) {
                handleMessageEnd(me);
            } else if (event instanceof ToolExecutionStartEvent te) {
                emitToolCall(te.toolCallId(), te.toolName(), "started");
            } else if (event instanceof ToolExecutionEndEvent te) {
                emitToolCall(te.toolCallId(), null, "completed");
            } else if (event instanceof AgentEndEvent) {
                handleAgentEnd();
            }
        });
        transport.start("acp-mode-reader");
        // Block until stdin closes / process exits.
        try {
            while (!Thread.currentThread().isInterrupted() && input.available() >= 0) {
                Thread.sleep(200L);
            }
        } catch (Exception ignored) {
            // exit on close
        } finally {
            if (transport != null) {
                transport.close();
            }
        }
    }

    private void onEnvelope(AcpProtocol.Envelope envelope) {
        if (envelope.isRequest()) {
            handleRequest(envelope);
        } else if (envelope.isNotification()) {
            handleNotification(envelope);
        }
    }

    private void handleRequest(AcpProtocol.Envelope envelope) {
        try {
            switch (envelope.method()) {
                case AcpProtocol.METHOD_INITIALIZE -> respondInitialize(envelope);
                case AcpProtocol.METHOD_NEW_SESSION -> respondNewSession(envelope);
                case AcpProtocol.METHOD_PROMPT -> handlePrompt(envelope);
                default ->
                    sendError(
                            envelope.id(),
                            AcpProtocol.Error.METHOD_NOT_FOUND,
                            "unsupported method: " + envelope.method());
            }
        } catch (RuntimeException ex) {
            log.warn("ACP request {} failed: {}", envelope.method(), ex.toString());
            sendError(envelope.id(), AcpProtocol.Error.INTERNAL_ERROR, ex.getMessage());
        }
    }

    private void handleNotification(AcpProtocol.Envelope envelope) {
        if (AcpProtocol.METHOD_CANCEL.equals(envelope.method())) {
            try {
                session.abort();
            } catch (RuntimeException ex) {
                log.debug("abort failed on session/cancel: {}", ex.toString());
            }
        }
    }

    private void respondInitialize(AcpProtocol.Envelope envelope) {
        var caps = mapper.createObjectNode();
        caps.put("promptCapability", true);
        caps.put("cancelCapability", true);
        var info = mapper.createObjectNode();
        info.put("name", "campusclaw");
        info.put("version", "1.0.0");
        var response = new AcpProtocol.InitializeResponse(AcpProtocol.PROTOCOL_VERSION, caps, info, null);
        transport.send(AcpProtocol.Envelope.ok(envelope.id(), mapper.valueToTree(response)));
    }

    private void respondNewSession(AcpProtocol.Envelope envelope) {
        String sessionId = "campusclaw-" + UUID.randomUUID();
        try {
            session.newSession();
        } catch (RuntimeException ex) {
            log.debug("newSession reset failed (continuing): {}", ex.toString());
        }
        currentSessionId = sessionId;
        var response = new AcpProtocol.NewSessionResponse(sessionId);
        transport.send(AcpProtocol.Envelope.ok(envelope.id(), mapper.valueToTree(response)));
    }

    private void handlePrompt(AcpProtocol.Envelope envelope) {
        var promptRequest = mapper.convertValue(envelope.params(), AcpProtocol.PromptRequest.class);
        if (currentSessionId == null || !currentSessionId.equals(promptRequest.sessionId())) {
            sendError(
                    envelope.id(), AcpProtocol.Error.INVALID_PARAMS, "unknown sessionId: " + promptRequest.sessionId());
            return;
        }
        String text = joinText(promptRequest);
        if (text.isBlank()) {
            sendError(envelope.id(), AcpProtocol.Error.INVALID_PARAMS, "prompt is empty");
            return;
        }
        turnText.setLength(0);
        Object requestId = envelope.id();
        if (requestId instanceof Number n) {
            activePromptId.set(n.longValue());
        } else {
            activePromptId.set(null);
        }
        try {
            activeTurn = session.prompt(text);
        } catch (RuntimeException ex) {
            sendError(envelope.id(), AcpProtocol.Error.INTERNAL_ERROR, ex.getMessage());
            return;
        }
        activeTurn.whenComplete((unused, error) -> respondToPrompt(envelope.id(), error));
    }

    private void respondToPrompt(Object requestId, Throwable error) {
        if (error != null) {
            sendError(requestId, AcpProtocol.Error.INTERNAL_ERROR, "agent failed: " + error.getMessage());
            return;
        }
        var response = new AcpProtocol.PromptResponse("end_turn");
        transport.send(AcpProtocol.Envelope.ok(requestId, mapper.valueToTree(response)));
        activePromptId.set(null);
    }

    private void handleMessageUpdate(MessageUpdateEvent update) {
        if (currentSessionId == null || update.message() == null) {
            return;
        }
        String delta = textOf(update.message());
        if (delta == null || delta.isEmpty() || delta.length() <= turnText.length()) {
            return;
        }
        String diff = delta.substring(turnText.length());
        turnText.setLength(0);
        turnText.append(delta);
        sendChunk(diff);
    }

    private void handleMessageEnd(MessageEndEvent end) {
        if (currentSessionId == null) {
            return;
        }
        String full = textOf(end.message());
        if (full != null && full.length() > turnText.length()) {
            sendChunk(full.substring(turnText.length()));
            turnText.setLength(0);
            turnText.append(full);
        }
    }

    private void handleAgentEnd() {
        // Final stop reason is sent by respondToPrompt when the future completes.
    }

    private void emitToolCall(String toolCallId, String name, String status) {
        if (currentSessionId == null) {
            return;
        }
        ObjectNode update = mapper.createObjectNode();
        update.put("sessionUpdate", AcpProtocol.UPDATE_TOOL_CALL);
        if (toolCallId != null) {
            update.put("toolCallId", toolCallId);
        }
        if (name != null) {
            update.put("name", name);
        }
        if (status != null) {
            update.put("status", status);
        }
        sendUpdate(update);
    }

    private void sendChunk(String text) {
        ObjectNode update = mapper.createObjectNode();
        update.put("sessionUpdate", AcpProtocol.UPDATE_AGENT_MESSAGE);
        ObjectNode content = mapper.createObjectNode();
        content.put("type", "text");
        content.put("text", text);
        update.set("content", content);
        sendUpdate(update);
    }

    private void sendUpdate(ObjectNode update) {
        ObjectNode params = mapper.createObjectNode();
        params.put("sessionId", currentSessionId);
        params.set("update", update);
        transport.send(AcpProtocol.Envelope.notification(AcpProtocol.METHOD_UPDATE, params));
    }

    private void sendError(Object id, int code, String message) {
        transport.send(AcpProtocol.Envelope.fail(id, new AcpProtocol.Error(code, message, null)));
    }

    private void onTransportError(Throwable cause) {
        log.debug("ACP transport error: {}", cause.toString());
    }

    private static String joinText(AcpProtocol.PromptRequest request) {
        if (request.prompt() == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (AcpProtocol.ContentBlock block : request.prompt()) {
            if ("text".equals(block.type()) && block.text() != null) {
                sb.append(block.text());
            }
        }
        return sb.toString();
    }

    private static String textOf(Message message) {
        if (!(message instanceof AssistantMessage assistant) || assistant.content() == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (ContentBlock block : assistant.content()) {
            if (block instanceof TextContent tc) {
                sb.append(tc.text());
            }
        }
        return sb.toString();
    }

    @SuppressWarnings("unused")
    private JsonNode placeholder() {
        return mapper.createObjectNode();
    }
}
