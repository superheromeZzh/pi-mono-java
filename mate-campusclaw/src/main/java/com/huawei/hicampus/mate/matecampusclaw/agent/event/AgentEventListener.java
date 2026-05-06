/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.hicampus.mate.matecampusclaw.agent.event;

/**
 * Listener for agent runtime events.
 */
@FunctionalInterface
public interface AgentEventListener {

    void onEvent(AgentEvent event);
}
