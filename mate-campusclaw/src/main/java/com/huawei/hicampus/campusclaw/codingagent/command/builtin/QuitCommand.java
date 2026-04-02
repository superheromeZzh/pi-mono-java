package com.huawei.hicampus.campusclaw.codingagent.command.builtin;

import com.huawei.hicampus.campusclaw.codingagent.command.SlashCommand;
import com.huawei.hicampus.campusclaw.codingagent.command.SlashCommandContext;

public class QuitCommand implements SlashCommand {

    @Override
    public String name() {
        return "quit";
    }

    @Override
    public String description() {
        return "Exit the application";
    }

    @Override
    public void execute(SlashCommandContext context, String arguments) {
        context.output().println("Goodbye.");
        System.exit(0);
    }
}
