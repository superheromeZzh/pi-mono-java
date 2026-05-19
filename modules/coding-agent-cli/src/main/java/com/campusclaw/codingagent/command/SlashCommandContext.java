/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.campusclaw.codingagent.command;

import com.campusclaw.codingagent.session.AgentSession;

/**
 * Context passed to slash commands for accessing session, printing output, etc.
 *
 * @version [br_eCampusCore 25.1.0_Next, 2026/05/06]
 * @since [br_eCampusCore 25.1.0_Next]
 */
public record SlashCommandContext(AgentSession session, OutputWriter output) {
    @SuppressWarnings("checkstyle:top_class_comment")
    @FunctionalInterface
    public interface OutputWriter {
        void println(String message);
    }
}
