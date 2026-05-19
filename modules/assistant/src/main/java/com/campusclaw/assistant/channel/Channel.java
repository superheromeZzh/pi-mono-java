/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.campusclaw.assistant.channel;

/**
 * Outbound messaging abstraction used by the assistant module to push messages to a named
 * channel such as the WebSocket gateway or an IM bridge. Implementations declare their
 * channel identifier via {@link #getName()} and accept text payloads through
 * {@link #sendMessage(String)}.
 *
 * @version [br_eCampusCore 25.1.0_Next, 2026/05/13]
 * @since [br_eCampusCore 25.1.0_Next]
 */
public interface Channel {

    String getName();

    void sendMessage(String message);
}
