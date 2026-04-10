package com.campusclaw.assistant.channel.gateway;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration properties for WebSocket Gateway server.
 * The gateway acts as a WebSocket server that chat tools can connect to directly.
 */
@ConfigurationProperties(prefix = "pi.assistant.gateway")
public class WebSocketGatewayProperties {

    private boolean enabled = false;
    private String name = "gateway";
    private int port = 18788;
    private String path = "/";
    private String token;
    private int tickIntervalMs = 30000;
    private int protocolVersion = 3;
    private String serverVersion = "1.0.0";
    private int maxPayload = 16777216;       // 16MB
    private int maxBufferedBytes = 1048576;  // 1MB

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public int getPort() {
        return port;
    }

    public void setPort(int port) {
        this.port = port;
    }

    public String getPath() {
        return path;
    }

    public void setPath(String path) {
        this.path = path;
    }

    public String getToken() {
        return token;
    }

    public void setToken(String token) {
        this.token = token;
    }

    public int getTickIntervalMs() {
        return tickIntervalMs;
    }

    public void setTickIntervalMs(int tickIntervalMs) {
        this.tickIntervalMs = tickIntervalMs;
    }

    public int getProtocolVersion() {
        return protocolVersion;
    }

    public void setProtocolVersion(int protocolVersion) {
        this.protocolVersion = protocolVersion;
    }

    public String getServerVersion() {
        return serverVersion;
    }

    public void setServerVersion(String serverVersion) {
        this.serverVersion = serverVersion;
    }

    public int getMaxPayload() {
        return maxPayload;
    }

    public void setMaxPayload(int maxPayload) {
        this.maxPayload = maxPayload;
    }

    public int getMaxBufferedBytes() {
        return maxBufferedBytes;
    }

    public void setMaxBufferedBytes(int maxBufferedBytes) {
        this.maxBufferedBytes = maxBufferedBytes;
    }
}
