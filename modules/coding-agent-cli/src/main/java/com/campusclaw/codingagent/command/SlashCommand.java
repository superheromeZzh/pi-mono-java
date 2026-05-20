/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.campusclaw.codingagent.command;

/**
 * Contract for a slash command exposed through the CLI (e.g. {@code /help}, {@code /model}).
 * Each command advertises a name and description and executes against the supplied
 * {@link SlashCommandContext} with the raw argument string.
 *
 * @version [br_eCampusCore 25.1.0_Next, 2026/05/13]
 * @since [br_eCampusCore 25.1.0_Next]
 */
public interface SlashCommand {
    /**
     * Command name without the slash (e.g., "model", "settings").
     *
     * @return the result
     */
    String name();

    /**
     * Short description for help text.
     *
     * @return the result
     */
    String description();

    /**
     * Execute the command with the given arguments.
     *
     * @param context the context
     * @param arguments the arguments
     */
    void execute(SlashCommandContext context, String arguments);
}
