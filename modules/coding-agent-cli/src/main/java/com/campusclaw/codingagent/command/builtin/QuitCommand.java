/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.campusclaw.codingagent.command.builtin;

import com.campusclaw.codingagent.command.QuitException;
import com.campusclaw.codingagent.command.SlashCommand;
import com.campusclaw.codingagent.command.SlashCommandContext;

@SuppressWarnings("checkstyle:top_class_comment")
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
