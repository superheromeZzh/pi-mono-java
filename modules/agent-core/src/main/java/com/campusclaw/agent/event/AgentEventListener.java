/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.campusclaw.agent.event;

/**
 * Listener for agent runtime events.
 *
 * @version [br_eCampusCore 25.1.0_Next, 2026/05/06]
 * @since [br_eCampusCore 25.1.0_Next]
 */
@FunctionalInterface
public interface AgentEventListener {

    @SuppressWarnings("checkstyle:java_doc_format_missing")
    void onEvent(AgentEvent event);
}
