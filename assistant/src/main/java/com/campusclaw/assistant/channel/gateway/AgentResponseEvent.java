package com.campusclaw.assistant.channel.gateway;

import org.springframework.context.ApplicationEvent;

/**
 * Published when the agent finishes processing a message.
 * Used to relay the agent's response back through the WebSocket gateway
 * as a response frame (type: "res") so OpenClaw's GatewayClient can receive it.
 */
public class AgentResponseEvent extends ApplicationEvent {

    private final String message;

    public AgentResponseEvent(Object source, String message) {
        super(source);
        this.message = message;
    }

    public String getMessage() {
        return message;
    }
}
