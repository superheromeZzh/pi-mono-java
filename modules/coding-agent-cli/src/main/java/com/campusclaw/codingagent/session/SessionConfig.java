/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.campusclaw.codingagent.session;

import java.nio.file.Path;

/**
 * Configuration for initializing an {@link AgentSession}.
 *
 * @param model        model identifier string (e.g. "claude-sonnet-4-20250514")
 * @param cwd          working directory for the session
 * @param customPrompt additional user-supplied system prompt text (may be null)
 * @param mode         execution mode: "interactive", "one-shot", or "print"
 *
 * @version [br_eCampusCore 25.1.0_Next, 2026/05/06]
 * @since [br_eCampusCore 25.1.0_Next]
 */
public record SessionConfig(String model, Path cwd, String customPrompt, String mode) {}
