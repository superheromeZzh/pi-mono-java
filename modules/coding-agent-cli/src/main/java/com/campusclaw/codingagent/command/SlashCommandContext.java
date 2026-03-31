package com.campusclaw.codingagent.command;

import com.campusclaw.codingagent.session.AgentSession;

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
