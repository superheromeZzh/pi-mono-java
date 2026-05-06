/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.hicampus.mate.matecampusclaw.assistant.channel.gateway;

import org.springframework.context.ApplicationEvent;

/**
 * Published when the agent finishes processing a message.
 * Used to relay the agent's response back through the WebSocket gateway
 * as a response frame (type: "res") so OpenClaw's GatewayClient can receive it.
 *
 * @version [br_eCampusCore 25.1.0_Next, 2026/05/06]
 * @since [br_eCampusCore 25.1.0_Next]
 */
public class AgentResponseEvent extends ApplicationEvent {

    private final String message;

    @SuppressWarnings("checkstyle:java_doc_format_missing")
    public AgentResponseEvent(Object source, String message) {
        super(source);
        this.message = message;
    }

    public String getMessage() {
        return message;
    }
}
