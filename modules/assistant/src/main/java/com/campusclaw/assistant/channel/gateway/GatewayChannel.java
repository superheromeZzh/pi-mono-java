package com.campusclaw.assistant.channel.gateway;

import com.campusclaw.assistant.channel.Channel;
import com.campusclaw.assistant.channel.ChannelRegistry;
import com.campusclaw.assistant.channel.MessageSubmitter;
import io.netty.channel.ChannelHandlerContext;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Gateway Channel implementation.
 * Acts as a WebSocket server that chat tools can connect to directly.
 * Incoming messages are forwarded to the current interactive session's agent
 * via MessageSubmitter.submitMessage().
 */
@Component
@ConditionalOnProperty(prefix = "pi.assistant.gateway", name = "enabled", havingValue = "true")
public class GatewayChannel implements Channel {

    private static final Logger log = LoggerFactory.getLogger(GatewayChannel.class);

    private final WebSocketGatewayProperties properties;
    private final ChannelRegistry channelRegistry;
    private final MessageSubmitter messageSubmitter; // LoopManager from coding-agent-cli, lazily resolved

    // channelId -> ChannelHandlerContext (for sending responses)
    private final Map<String, ChannelHandlerContext> sessionContexts = new ConcurrentHashMap<>();

    // sessionKey -> channelId (for routing responses)
    private final Map<String, String> sessionKeyToChannel = new ConcurrentHashMap<>();

    // channelId -> current sessionKey (for tracking active sessions)
    private final Map<String, String> channelToSessionKey = new ConcurrentHashMap<>();

    // reqId -> PendingRequest (for tracking sessions.send requests awaiting final response)
    private final Map<String, PendingRequest> pendingSessionsSend = new ConcurrentHashMap<>();

    record PendingRequest(String reqId, String channelId, String sessionKey) {}

    public GatewayChannel(
        WebSocketGatewayProperties properties,
        ChannelRegistry channelRegistry,
        @Lazy @Autowired(required = false) MessageSubmitter messageSubmitter
    ) {
        this.properties = properties;
        this.channelRegistry = channelRegistry;
        this.messageSubmitter = messageSubmitter;
    }

    @PostConstruct
    public void register() {
        channelRegistry.register(this);
    }

    @Override
    public String getName() {
        return properties.getName();
    }

    @Override
    public void sendMessage(String message) {
        for (Map.Entry<String, ChannelHandlerContext> entry : sessionContexts.entrySet()) {
            String channelId = entry.getKey();
            String sessionKey = channelToSessionKey.get(channelId);
            if (sessionKey != null) {
                sendMessageToSession(channelId, sessionKey, message);
            }
        }
    }

    /**
     * Register a WebSocket session.
     */
    public void registerSession(String channelId, ChannelHandlerContext ctx) {
        sessionContexts.put(channelId, ctx);
    }

    /**
     * Remove a WebSocket session.
     */
    public void removeSession(String channelId) {
        sessionContexts.remove(channelId);
        String sessionKey = channelToSessionKey.remove(channelId);
        if (sessionKey != null) {
            sessionKeyToChannel.remove(sessionKey);
        }
        // Clean up pending requests for this channel
        pendingSessionsSend.entrySet().removeIf(entry -> entry.getValue().channelId().equals(channelId));
    }

    /**
     * Register a pending sessions.send request so the final result
     * can be sent as a response frame with the original reqId.
     */
    public void registerPendingSessionsSend(String reqId, String channelId, String sessionKey) {
        pendingSessionsSend.put(reqId, new PendingRequest(reqId, channelId, sessionKey));
    }

    /**
     * Handle an incoming message from a client.
     * Forwards the message to the current interactive session's agent via LoopManager,
     * so the agent can process it using any available tools (CronTool, LoopTool, etc.).
     */
    public void handleIncomingMessage(String channelId, String sessionKey, String content) {
        // Associate sessionKey with channel
        sessionKeyToChannel.put(sessionKey, channelId);
        channelToSessionKey.put(channelId, sessionKey);

        // Forward to interactive session agent
        if (messageSubmitter != null) {
            boolean submitted = messageSubmitter.submitMessage(content);
            if (submitted) {
                return;
            } else {
                log.warn("Failed to forward message to agent session (submit returned false)");
            }
        }

        // Fallback: send error if no session available
        completePendingSessionsSendForChannel(channelId, sessionKey,
            "[Gateway] No active session. Message not processed.");
    }

    /**
     * Listen for AgentResponseEvent and send the result as a response frame
     * to the pending sessions.send caller.
     */
    @EventListener
    public void onAgentResponse(AgentResponseEvent event) {
        String message = event.getMessage();

        if (pendingSessionsSend.isEmpty()) {
            return;
        }

        // Complete all pending requests for all channels with the agent response.
        // Since the agent processes one message at a time, the response goes to
        // whichever channel has a pending request.
        for (Map.Entry<String, PendingRequest> entry : pendingSessionsSend.entrySet()) {
            PendingRequest pending = entry.getValue();
            completePendingSessionsSend(pending.reqId(), pending.channelId(),
                pending.sessionKey(), message);
        }
    }

    /**
     * Send a message to a specific session.
     */
    public void sendMessageToSession(String channelId, String sessionKey, String message) {
        ChannelHandlerContext ctx = sessionContexts.get(channelId);
        if (ctx == null) {
            log.warn("No context found for channel: {}", channelId);
            return;
        }

        GatewayWebSocketHandler handler = getHandler(channelId);
        if (handler == null) {
            log.warn("No handler found for channel: {}", channelId);
            return;
        }

        String runId = UUID.randomUUID().toString();
        handler.sendEvent(ctx, "chat", runId, sessionKey, "final", message);
    }

    /**
     * Send a streaming delta to a session.
     */
    public void sendDeltaToSession(String channelId, String sessionKey, String delta) {
        ChannelHandlerContext ctx = sessionContexts.get(channelId);
        if (ctx == null) return;

        GatewayWebSocketHandler handler = getHandler(channelId);
        if (handler == null) return;

        String runId = UUID.randomUUID().toString();
        handler.sendEvent(ctx, "chat", runId, sessionKey, "delta", delta);
    }

    // ── Internal methods ──────────────────────────────────────────

    /**
     * Complete a pending sessions.send request by sending a response frame
     * with the agent's result.
     */
    private void completePendingSessionsSend(String reqId, String channelId, String sessionKey,
                                              String resultMessage) {
        pendingSessionsSend.remove(reqId);

        ChannelHandlerContext ctx = sessionContexts.get(channelId);
        if (ctx == null || !ctx.channel().isActive()) {
            log.warn("Channel {} no longer active for pending sessions.send {}", channelId, reqId);
            return;
        }

        GatewayWebSocketHandler handler = getHandler(channelId);
        if (handler == null) {
            log.warn("No handler found for channel: {}", channelId);
            return;
        }

        String runId = UUID.randomUUID().toString();
        Map<String, Object> payload = Map.of(
            "runId", runId,
            "sessionKey", sessionKey,
            "status", "final",
            "message", Map.of(
                "id", UUID.randomUUID().toString(),
                "content", resultMessage,
                "role", "assistant",
                "timestamp", System.currentTimeMillis()
            )
        );

        handler.sendResponseFrame(ctx, reqId, payload);
    }

    /**
     * Complete all pending requests for a given channel (used for fallback/error cases).
     */
    private void completePendingSessionsSendForChannel(String channelId, String sessionKey,
                                                        String resultMessage) {
        pendingSessionsSend.entrySet().removeIf(entry -> {
            PendingRequest pending = entry.getValue();
            if (pending.channelId().equals(channelId)) {
                completePendingSessionsSend(pending.reqId(), channelId, sessionKey, resultMessage);
                return true;
            }
            return false;
        });
    }

    private GatewayWebSocketHandler getHandler(String channelId) {
        ChannelHandlerContext ctx = sessionContexts.get(channelId);
        if (ctx == null) return null;
        try {
            return (GatewayWebSocketHandler) ctx.pipeline().get("messageHandler");
        } catch (Exception e) {
            log.error("Failed to get message handler: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Get the number of connected sessions.
     */
    public int getSessionCount() {
        return sessionContexts.size();
    }

    /**
     * Check if a session is connected.
     */
    public boolean hasSession(String channelId) {
        return sessionContexts.containsKey(channelId);
    }
}
