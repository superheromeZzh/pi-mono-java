/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.hicampus.mate.matecampusclaw.codingagent.command;

@SuppressWarnings("checkstyle:top_class_comment")
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
