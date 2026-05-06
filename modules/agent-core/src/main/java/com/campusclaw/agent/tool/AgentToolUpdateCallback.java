/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.campusclaw.agent.tool;

/**
 * Callback for streaming partial tool results while a tool is executing.
 *
 * @version [br_eCampusCore 25.1.0_Next, 2026/05/06]
 * @since [br_eCampusCore 25.1.0_Next]
 */
@FunctionalInterface
public interface AgentToolUpdateCallback {

    void onUpdate(AgentToolResult partialResult);
}
