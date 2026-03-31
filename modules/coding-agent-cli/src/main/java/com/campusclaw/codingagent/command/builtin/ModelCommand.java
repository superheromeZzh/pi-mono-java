package com.campusclaw.codingagent.command.builtin;

import com.campusclaw.codingagent.command.SlashCommand;
import com.campusclaw.codingagent.command.SlashCommandContext;

public class ModelCommand implements SlashCommand {

    @Override
    public String name() {
        return "model";
    }

    @Override
    public String description() {
        return "Print or switch the current model";
    }

    @Override
    public void execute(SlashCommandContext context, String arguments) {
        if (arguments.isEmpty()) {
            var model = context.session().getAgent().getState().getModel();
            context.output().println("Current model: " + (model != null ? model.id() : "unknown"));
        } else {
            try {
                context.session().setModel(arguments.trim());
                var model = context.session().getAgent().getState().getModel();
                context.output().println("Switched to model: " + (model != null ? model.id() : arguments.trim()));
            } catch (IllegalArgumentException e) {
                context.output().println("Unknown model: " + arguments.trim());
            }
        }
    }
}
