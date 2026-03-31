package com.campusclaw.codingagent.command;

public interface SlashCommand {
    /** Command name without the slash (e.g., "model", "settings"). */
    String name();
    /** Short description for help text. */
    String description();
    /** Execute the command with the given arguments. */
    void execute(SlashCommandContext context, String arguments);
}
