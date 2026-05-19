/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.campusclaw.codingagent.extension;

/**
 * Extension points where external extensions can hook into the coding agent.
 *
 * @version [br_eCampusCore 25.1.0_Next, 2026/05/06]
 * @since [br_eCampusCore 25.1.0_Next]
 */
public enum ExtensionPoint {

    /**
     * Register additional agent tools.
     */
    TOOL,

    /**
     * Register additional slash commands.
     */
    COMMAND,

    /**
     * Register before-tool-call hooks.
     */
    BEFORE_TOOL_CALL,

    /**
     * Register after-tool-call hooks.
     */
    AFTER_TOOL_CALL,

    /**
     * Register context transformers.
     */
    CONTEXT_TRANSFORMER,

    /**
     * Register event listeners.
     */
    EVENT_LISTENER
}
