/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.campusclaw.agent.event;

/**
 * Listener for agent runtime events.
 */
@FunctionalInterface
public interface AgentEventListener {

    void onEvent(AgentEvent event);
}
