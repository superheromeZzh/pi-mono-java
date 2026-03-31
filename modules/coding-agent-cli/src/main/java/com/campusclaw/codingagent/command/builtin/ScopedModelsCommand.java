package com.campusclaw.codingagent.command.builtin;

import com.campusclaw.codingagent.command.SlashCommand;
import com.campusclaw.codingagent.command.SlashCommandContext;

/**
 * Enable/disable models for Ctrl+P cycling.
 * In interactive mode, this is intercepted by InteractiveMode to show an overlay.
 */
public class ScopedModelsCommand implements SlashCommand {

    @Override
    public String name() { return "scoped-models"; }

    @Override
    public String description() { return "Enable/disable models for Ctrl+P cycling"; }

    @Override
    public void execute(SlashCommandContext context, String arguments) {
        context.output().println("Use this command in interactive mode to configure model cycling scope.");
    }
}
