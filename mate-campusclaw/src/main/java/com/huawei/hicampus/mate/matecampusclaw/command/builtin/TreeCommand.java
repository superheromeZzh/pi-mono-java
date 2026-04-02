package com.huawei.hicampus.mate.matecampusclaw.codingagent.command.builtin;

import com.huawei.hicampus.mate.matecampusclaw.codingagent.command.SlashCommand;
import com.huawei.hicampus.mate.matecampusclaw.codingagent.command.SlashCommandContext;

/**
 * Navigate session tree (switch branches).
 * In interactive mode, this is intercepted by InteractiveMode to show the overlay.
 * This fallback handles the text-mode case.
 */
public class TreeCommand implements SlashCommand {

    @Override
    public String name() { return "tree"; }

    @Override
    public String description() { return "Navigate session tree (switch branches)"; }

    @Override
    public void execute(SlashCommandContext context, String arguments) {
        // This is the fallback — in interactive mode, InteractiveMode intercepts /tree
        context.output().println("Session tree is only available in interactive mode.");
    }
}
