package com.mariozechner.pi.codingagent.command.builtin;

import com.mariozechner.pi.codingagent.command.SlashCommand;
import com.mariozechner.pi.codingagent.command.SlashCommandContext;

public class SettingsCommand implements SlashCommand {

    @Override
    public String name() {
        return "settings";
    }

    @Override
    public String description() {
        return "Print current settings";
    }

    @Override
    public void execute(SlashCommandContext context, String arguments) {
        var model = context.session().getAgent().getState().getModel();
        context.output().println("Settings:");
        context.output().println("  Model: " + (model != null ? model.id() : "unknown"));
    }
}
