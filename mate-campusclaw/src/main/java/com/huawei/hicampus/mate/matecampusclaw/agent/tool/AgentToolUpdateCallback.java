/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.hicampus.mate.matecampusclaw.agent.tool;

/**
 * Callback for streaming partial tool results while a tool is executing.
 *
 * @version [br_eCampusCore 25.1.0_Next, 2026/05/06]
 * @since [br_eCampusCore 25.1.0_Next]
 */
@FunctionalInterface
public interface AgentToolUpdateCallback {

    @SuppressWarnings("checkstyle:java_doc_format_missing")
    void onUpdate(AgentToolResult partialResult);
}
