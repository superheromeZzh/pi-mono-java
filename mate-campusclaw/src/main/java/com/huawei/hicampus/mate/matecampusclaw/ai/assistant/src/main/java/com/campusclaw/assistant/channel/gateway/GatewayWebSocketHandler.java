package com.campusclaw.assistant.channel.gateway;

import com.campusclaw.assistant.channel.gateway.protocol.*;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import io.netty.handler.codec.http.websocketx.WebSocketServerProtocolHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Handles WebSocket messages from connected chat clients.
 * Implements OpenClaw protocol for message framing.
 */
public class GatewayWebSocketHandler extends SimpleChannelInboundHandler<TextWebSocketFrame> {

    private static final Logger log = LoggerFactory.getLogger(GatewayWebSocketHandler.class);

    private final WebSocketGatewayProperties properties;
    private final GatewayChannel gatewayChannel;
    private final ObjectMapper objectMapper;

    private final Map<String, ChannelHandlerContext> sessions = new ConcurrentHashMap<>();
    private final Map<String, Boolean> authenticatedSessions = new ConcurrentHashMap<>();
    private final Map<String, AtomicInteger> sessionSeqCounters = new ConcurrentHashMap<>();
    private final Map<String, ScheduledFuture<?>> tickFutures = new ConcurrentHashMap<>();

    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "gateway-tick");
        t.setDaemon(true);
        return t;
    });

    public GatewayWebSocketHandler(WebSocketGatewayProperties properties, GatewayChannel gatewayChannel,
                                   ObjectMapper objectMapper) {
        this.properties = properties;
        this.gatewayChannel = gatewayChannel;
        this.objectMapper = objectMapper;
    }

    @Override
    public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
        if (evt instanceof WebSocketServerProtocolHandler.HandshakeComplete) {
            String channelId = ctx.channel().id().asShortText();
            // Send connect.challenge immediately after handshake
            sendChallenge(ctx);

            // Start periodic tick event
            ScheduledFuture<?> tickFuture = scheduler.scheduleAtFixedRate(
                () -> sendTickEvent(ctx),
                properties.getTickIntervalMs(),
                properties.getTickIntervalMs(),
                TimeUnit.MILLISECONDS
            );
            tickFutures.put(channelId, tickFuture);
        }
        super.userEventTriggered(ctx, evt);
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, TextWebSocketFrame frame) throws Exception {
        String text = frame.text();
        String channelId = ctx.channel().id().asShortText();

        try {
            GatewayFrame frameObj = objectMapper.readValue(text, GatewayFrame.class);

            switch (frameObj.type()) {
                case "req" -> handleRequest(ctx, frameObj);
                case "event" -> handleClientEvent(ctx, frameObj);
                default -> log.warn("Unknown frame type '{}' from {}", frameObj.type(), channelId);
            }
        } catch (JsonProcessingException e) {
            log.error("JSON parse error from {}: {}", channelId, e.getMessage());
            sendError(ctx, null, "parseError", "Invalid JSON: " + e.getMessage());
        } catch (Exception e) {
            log.error("Error processing message from {}: {}", channelId, e.getMessage());
            sendError(ctx, null, "processingError", e.getMessage());
        }
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
        String channelId = ctx.channel().id().asShortText();
        sessions.put(channelId, ctx);
        sessionSeqCounters.put(channelId, new AtomicInteger(0));
        super.channelActive(ctx);
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        String channelId = ctx.channel().id().asShortText();
        sessions.remove(channelId);
        authenticatedSessions.remove(channelId);
        sessionSeqCounters.remove(channelId);

        ScheduledFuture<?> tickFuture = tickFutures.remove(channelId);
        if (tickFuture != null) {
            tickFuture.cancel(false);
        }

        gatewayChannel.removeSession(channelId);
        super.channelInactive(ctx);
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        log.error("WebSocket error for {}: {}", ctx.channel().id().asShortText(), cause.getMessage());
        ctx.close();
    }

    // ── Connect challenge ──────────────────────────────────────────

    private void sendChallenge(ChannelHandlerContext ctx) {
        String channelId = ctx.channel().id().asShortText();
        String nonce = UUID.randomUUID().toString();
        long ts = System.currentTimeMillis();
        int seq = sessionSeqCounters.computeIfAbsent(channelId, k -> new AtomicInteger(0)).getAndIncrement();

        Map<String, Object> payload = Map.of("nonce", nonce, "ts", ts);
        Map<String, Object> frame = new LinkedHashMap<>();
        frame.put("type", "event");
        frame.put("event", "connect.challenge");
        frame.put("payload", payload);
        frame.put("seq", seq);
        frame.put("stateVersion", Map.of("presence", 0, "health", 0));
        writeFrame(ctx, frame);
    }

    // ── Request handling ───────────────────────────────────────────

    private void handleRequest(ChannelHandlerContext ctx, GatewayFrame frame) {
        String channelId = ctx.channel().id().asShortText();
        String reqId = frame.id();

        // Connect method does not require prior authentication
        if ("connect".equals(frame.method())) {
            handleConnect(ctx, frame);
            return;
        }

        // All other methods require authentication
        if (!authenticatedSessions.containsKey(channelId)) {
            log.warn("Unauthenticated request from {}", channelId);
            sendError(ctx, reqId, "authRequired", "Not authenticated");
            return;
        }

        switch (frame.method()) {
            case "sessions.send", "chat.send" -> handleSessionsSend(ctx, frame);
            case "policy.tick" -> sendResponse(ctx, reqId, Map.of("tick", true));
            default -> {
                log.warn("Unknown method '{}' from {}", frame.method(), channelId);
                sendError(ctx, reqId, "unknownMethod", "Unknown method: " + frame.method());
            }
        }
    }

    private void handleConnect(ChannelHandlerContext ctx, GatewayFrame frame) {
        String channelId = ctx.channel().id().asShortText();
        String reqId = frame.id();

        try {
            ConnectParams params = objectMapper.treeToValue(
                objectMapper.valueToTree(frame.params()), ConnectParams.class);

            // Validate token
            String token = params.auth() != null ? params.auth().token() : null;
            if (properties.getToken() != null && !properties.getToken().isEmpty()) {
                if (token == null || !properties.getToken().equals(token)) {
                    log.warn("Invalid token from {}", channelId);
                    sendError(ctx, reqId, "authFailed", "Invalid or missing token");
                    ctx.close();
                    return;
                }
            }

            // Mark session as authenticated
            authenticatedSessions.put(channelId, true);
            gatewayChannel.registerSession(channelId, ctx);

            // Send hello-ok response
            sendHelloOk(ctx, reqId);
        } catch (Exception e) {
            log.error("Error handling connect: {}", e.getMessage(), e);
            sendError(ctx, reqId, "connectError", e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName());
        }
    }

    private void handleSessionsSend(ChannelHandlerContext ctx, GatewayFrame frame) {
        String channelId = ctx.channel().id().asShortText();
        String reqId = frame.id();

        try {
            SessionsSendParams params = objectMapper.treeToValue(
                objectMapper.valueToTree(frame.params()), SessionsSendParams.class);

            if (params.key() == null) {
                sendError(ctx, reqId, "invalidParams", "Missing required field: key");
                return;
            }

            // Extract message content — could be string or object
            String messageContent;
            if (params.message() instanceof String s) {
                messageContent = s;
            } else if (params.message() != null) {
                messageContent = objectMapper.writeValueAsString(params.message());
            } else {
                messageContent = "";
            }

            // Register pending request so the final result can be sent as a response frame
            gatewayChannel.registerPendingSessionsSend(reqId, channelId, params.key());

            // Acknowledge the request (client with expectFinal will ignore this)
            sendResponse(ctx, reqId, Map.of("status", "accepted"));

            // Create task for message processing
            gatewayChannel.handleIncomingMessage(channelId, params.key(), messageContent);
        } catch (Exception e) {
            log.error("Error handling sessions.send: {}", e.getMessage());
            sendError(ctx, reqId, "processingError", e.getMessage());
        }
    }

    private void handleClientEvent(ChannelHandlerContext ctx, GatewayFrame frame) {
        // Client-initiated events (if any) — ignore for now
    }

    // ── Response sending ───────────────────────────────────────────

    private void sendHelloOk(ChannelHandlerContext ctx, String reqId) {
        String channelId = ctx.channel().id().asShortText();

        HelloOkPayload payload = new HelloOkPayload(
            "hello-ok",
            properties.getProtocolVersion(),
            new ServerInfo(properties.getServerVersion(), channelId),
            new FeaturesInfo(
                List.of("sessions.send", "chat.send", "sessions.list", "policy.tick"),
                List.of("chat", "tick", "connect.challenge")
            ),
            new LinkedHashMap<>(Map.of(
                "presence", List.of(),
                "uptimeMs", System.currentTimeMillis()
            )) {{
                put("health", null);
            }},
            new PolicyInfo(
                properties.getMaxPayload(),
                properties.getMaxBufferedBytes(),
                properties.getTickIntervalMs()
            )
        );

        writeFrame(ctx, Map.of(
            "type", "res",
            "id", reqId,
            "ok", true,
            "payload", payload
        ));
    }

    private void sendResponse(ChannelHandlerContext ctx, String reqId, Object payload) {
        writeFrame(ctx, Map.of(
            "type", "res",
            "id", reqId,
            "ok", true,
            "payload", payload
        ));
    }

    /**
     * Send a response frame with the given id and payload.
     * Called by GatewayChannel to complete pending sessions.send requests.
     */
    public void sendResponseFrame(ChannelHandlerContext ctx, String reqId, Object payload) {
        writeFrame(ctx, Map.of(
            "type", "res",
            "id", reqId,
            "ok", true,
            "payload", payload
        ));
    }

    private void sendError(ChannelHandlerContext ctx, String reqId, String code, String message) {
        Map<String, Object> frame = new LinkedHashMap<>();
        frame.put("type", "res");
        frame.put("ok", false);
        frame.put("error", new ErrorBody(code, message, null, false, 0));
        if (reqId != null) {
            frame.put("id", reqId);
        }
        writeFrame(ctx, frame);
    }

    // ── Event sending ──────────────────────────────────────────────

    /**
     * Send a chat event with proper OpenClaw framing including seq.
     */
    public void sendEvent(ChannelHandlerContext ctx, String event, String runId, String sessionKey,
                          String state, String content) {
        String channelId = ctx.channel().id().asShortText();
        int seq = sessionSeqCounters.computeIfAbsent(channelId, k -> new AtomicInteger(0)).getAndIncrement();

        ChatEventPayload payload = new ChatEventPayload(
            runId,
            sessionKey,
            seq,
            state,
            Map.of(
                "id", UUID.randomUUID().toString(),
                "content", content,
                "role", "assistant",
                "timestamp", System.currentTimeMillis()
            ),
            null,
            null,
            "final".equals(state) ? "endTurn" : null
        );

        Map<String, Object> frame = new LinkedHashMap<>();
        frame.put("type", "event");
        frame.put("event", event);
        frame.put("payload", payload);
        frame.put("seq", seq);
        frame.put("stateVersion", Map.of("presence", seq, "health", 0));
        writeFrame(ctx, frame);
    }

    private void sendTickEvent(ChannelHandlerContext ctx) {
        if (!ctx.channel().isActive()) return;

        String channelId = ctx.channel().id().asShortText();
        int seq = sessionSeqCounters.computeIfAbsent(channelId, k -> new AtomicInteger(0)).getAndIncrement();

        Map<String, Object> frame = new LinkedHashMap<>();
        frame.put("type", "event");
        frame.put("event", "tick");
        frame.put("payload", Map.of("ts", System.currentTimeMillis()));
        frame.put("seq", seq);
        frame.put("stateVersion", Map.of("presence", 0, "health", 0));
        writeFrame(ctx, frame);
    }

    // ── Utilities ──────────────────────────────────────────────────

    private void writeFrame(ChannelHandlerContext ctx, Object obj) {
        try {
            String json = objectMapper.writeValueAsString(obj);
            ctx.writeAndFlush(new TextWebSocketFrame(json));
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize frame: {}", e.getMessage());
        }
    }

    /**
     * Get session by channel ID.
     */
    public ChannelHandlerContext getSession(String channelId) {
        return sessions.get(channelId);
    }
}
