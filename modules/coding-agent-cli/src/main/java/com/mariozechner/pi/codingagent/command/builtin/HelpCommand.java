package com.mariozechner.pi.codingagent.command.builtin;

import com.mariozechner.pi.codingagent.command.SlashCommand;
import com.mariozechner.pi.codingagent.command.SlashCommandContext;
import com.mariozechner.pi.codingagent.command.SlashCommandRegistry;

public class HelpCommand implements SlashCommand {

    private final SlashCommandRegistry registry;

    public HelpCommand(SlashCommandRegistry registry) {
        this.registry = registry;
    }

    @Override
    public String name() {
        return "help";
    }

    @Override
    public String description() {
        return "List all available commands";
    }

    @Override
    public void execute(SlashCommandContext context, String arguments) {
        context.output().println("Available commands:");
        for (SlashCommand cmd : registry.getAll()) {
            context.output().println("  /" + cmd.name() + " - " + cmd.description());
        }
    }
}
