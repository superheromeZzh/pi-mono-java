/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.hicampus.mate.matecampusclaw.codingagent.command.builtin;

import com.huawei.hicampus.mate.matecampusclaw.codingagent.command.SlashCommand;
import com.huawei.hicampus.mate.matecampusclaw.codingagent.command.SlashCommandContext;

/**
 * Slash command {@code /new} that resets the current session, clearing conversation history
 * and starting a fresh agent state.
 *
 * @version [br_eCampusCore 25.1.0_Next, 2026/05/13]
 * @since [br_eCampusCore 25.1.0_Next]
 */
public class NewCommand implements SlashCommand {

    @Override
    public String name() {
        return "new";
    }

    @Override
    public String description() {
        return "Start a new session";
    }

    @Override
    public void execute(SlashCommandContext context, String arguments) {
        context.session().newSession();
        context.output().println("Started new session. Conversation history cleared.");
    }
}
