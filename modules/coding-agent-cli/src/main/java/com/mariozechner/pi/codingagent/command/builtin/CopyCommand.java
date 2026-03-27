package com.mariozechner.pi.codingagent.command.builtin;

import com.mariozechner.pi.codingagent.command.SlashCommand;
import com.mariozechner.pi.codingagent.command.SlashCommandContext;

public class CopyCommand implements SlashCommand {

    @Override
    public String name() {
        return "copy";
    }

    @Override
    public String description() {
        return "Copy last reply to clipboard";
    }

    @Override
    public void execute(SlashCommandContext context, String arguments) {
        context.output().println("Copy to clipboard is not yet implemented.");
    }
}
