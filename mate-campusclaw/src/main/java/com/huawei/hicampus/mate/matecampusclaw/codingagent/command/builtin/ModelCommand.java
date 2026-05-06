/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.hicampus.mate.matecampusclaw.codingagent.command.builtin;

import com.huawei.hicampus.mate.matecampusclaw.codingagent.command.SlashCommand;
import com.huawei.hicampus.mate.matecampusclaw.codingagent.command.SlashCommandContext;

@SuppressWarnings("checkstyle:top_class_comment")
public class ModelCommand implements SlashCommand {

    private final String name;

    @SuppressWarnings("checkstyle:java_doc_format_missing")
    public ModelCommand() {
        this("model");
    }

    @SuppressWarnings("checkstyle:java_doc_format_missing")
    public ModelCommand(String name) {
        this.name = name;
    }

    @Override
    public String name() {
        return name;
    }

    @Override
    public String description() {
        return "Print or switch the current model (no args opens the picker)";
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
