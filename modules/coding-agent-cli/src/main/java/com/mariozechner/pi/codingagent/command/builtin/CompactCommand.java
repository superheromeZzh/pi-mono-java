package com.mariozechner.pi.codingagent.command.builtin;

import com.mariozechner.pi.codingagent.command.SlashCommand;
import com.mariozechner.pi.codingagent.command.SlashCommandContext;

public class CompactCommand implements SlashCommand {

    @Override
    public String name() {
        return "compact";
    }

    @Override
    public String description() {
        return "Trigger context compaction";
    }

    @Override
    public void execute(SlashCommandContext context, String arguments) {
        context.output().println("Context compaction is not yet implemented.");
    }
}
