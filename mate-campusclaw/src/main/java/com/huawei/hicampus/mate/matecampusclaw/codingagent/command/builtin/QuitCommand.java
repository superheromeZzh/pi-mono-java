package com.huawei.hicampus.mate.matecampusclaw.codingagent.command.builtin;

import com.huawei.hicampus.mate.matecampusclaw.codingagent.command.QuitException;
import com.huawei.hicampus.mate.matecampusclaw.codingagent.command.SlashCommand;
import com.huawei.hicampus.mate.matecampusclaw.codingagent.command.SlashCommandContext;

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
        throw new QuitException();
    }
}
