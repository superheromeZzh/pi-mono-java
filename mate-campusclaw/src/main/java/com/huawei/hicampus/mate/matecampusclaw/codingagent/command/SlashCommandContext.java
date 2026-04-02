package com.huawei.hicampus.mate.matecampusclaw.codingagent.command;

import com.huawei.hicampus.mate.matecampusclaw.codingagent.session.AgentSession;

/** Context passed to slash commands for accessing session, printing output, etc. */
public record SlashCommandContext(
    AgentSession session,
    OutputWriter output
) {
    @FunctionalInterface
    public interface OutputWriter {
        void println(String message);
    }
}
