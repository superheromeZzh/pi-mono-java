package com.mariozechner.pi.codingagent.command.builtin;

import com.mariozechner.pi.codingagent.command.SlashCommand;
import com.mariozechner.pi.codingagent.command.SlashCommandContext;

public class NewCommand implements SlashCommand {

    @Override
    public String name() {
        return "new";
    }

    @Override
    public String description() {
        return "Create a new session";
    }

    @Override
    public void execute(SlashCommandContext context, String arguments) {
        context.output().println("New session creation is not yet implemented.");
    }
}
