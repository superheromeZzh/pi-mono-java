package com.huawei.hicampus.mate.matecampusclaw.codingagent.command.builtin;

import com.huawei.hicampus.mate.matecampusclaw.codingagent.command.SlashCommand;
import com.huawei.hicampus.mate.matecampusclaw.codingagent.command.SlashCommandContext;

/**
 * Logout from OAuth provider.
 */
public class LogoutCommand implements SlashCommand {

    @Override
    public String name() { return "logout"; }

    @Override
    public String description() { return "Logout from OAuth provider"; }

    @Override
    public void execute(SlashCommandContext context, String arguments) {
        context.output().println("OAuth logout is not yet implemented in campusclaw.");
    }
}
