/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.campusclaw.assistant.channel;

import java.time.Instant;

/**
 * Application event published when an external channel receives an inbound message.
 * Carries the originating channel name, the raw message payload, and a receive timestamp
 * for downstream handlers.
 *
 * @version [br_eCampusCore 25.1.0_Next, 2026/05/13]
 * @since [br_eCampusCore 25.1.0_Next]
 */
public record ChannelMessageReceivedEvent(String channelName, String message, Instant timestamp) {
    public ChannelMessageReceivedEvent(String channelName, String message) {
        this(channelName, message, Instant.now());
    }
}
