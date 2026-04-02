package com.huawei.hicampus.mate.matecampusclaw.codingagent.session;

import java.nio.file.Path;

/**
 * Configuration for initializing an {@link AgentSession}.
 *
 * @param model        model identifier string (e.g. "claude-sonnet-4-20250514")
 * @param cwd          working directory for the session
 * @param customPrompt additional user-supplied system prompt text (may be null)
 * @param mode         execution mode: "interactive", "one-shot", or "print"
 */
public record SessionConfig(
        String model,
        Path cwd,
        String customPrompt,
        String mode
) {
}
