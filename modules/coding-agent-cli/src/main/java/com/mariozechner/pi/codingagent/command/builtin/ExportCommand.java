package com.mariozechner.pi.codingagent.command.builtin;

import com.mariozechner.pi.codingagent.command.SlashCommand;
import com.mariozechner.pi.codingagent.command.SlashCommandContext;

public class ExportCommand implements SlashCommand {

    @Override
    public String name() {
        return "export";
    }

    @Override
    public String description() {
        return "Export conversation as HTML";
    }

    @Override
    public void execute(SlashCommandContext context, String arguments) {
        context.output().println("HTML export is not yet implemented.");
    }
}
