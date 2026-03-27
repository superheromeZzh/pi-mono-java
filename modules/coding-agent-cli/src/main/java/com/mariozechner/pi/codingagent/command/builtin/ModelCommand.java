package com.mariozechner.pi.codingagent.command.builtin;

import com.mariozechner.pi.codingagent.command.SlashCommand;
import com.mariozechner.pi.codingagent.command.SlashCommandContext;

public class ModelCommand implements SlashCommand {

    @Override
    public String name() {
        return "model";
    }

    @Override
    public String description() {
        return "Print or set the current model";
    }

    @Override
    public void execute(SlashCommandContext context, String arguments) {
        if (arguments.isEmpty()) {
            var model = context.session().getAgent().getState().getModel();
            context.output().println("Current model: " + (model != null ? model.id() : "unknown"));
        } else {
            context.output().println("Model switching not yet implemented. Requested: " + arguments);
        }
    }
}
