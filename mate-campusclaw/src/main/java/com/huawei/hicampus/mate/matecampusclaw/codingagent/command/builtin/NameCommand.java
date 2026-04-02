package com.huawei.hicampus.mate.matecampusclaw.codingagent.command.builtin;

import com.huawei.hicampus.mate.matecampusclaw.codingagent.command.SlashCommand;
import com.huawei.hicampus.mate.matecampusclaw.codingagent.command.SlashCommandContext;

/**
 * Sets or displays the session display name matching campusclaw TS /name command.
 */
public class NameCommand implements SlashCommand {

    private String sessionName;

    @Override
    public String name() {
        return "name";
    }

    @Override
    public String description() {
        return "Set session display name";
    }

    @Override
    public void execute(SlashCommandContext context, String arguments) {
        if (arguments.isEmpty()) {
            if (sessionName != null && !sessionName.isEmpty()) {
                context.output().println("Session name: " + sessionName);
            } else {
                context.output().println("No session name set. Usage: /name <name>");
            }
        } else {
            sessionName = arguments.trim();
            // Persist session name
            var sm = context.session().getSessionManager();
            if (sm != null) {
                sm.appendSessionName(sessionName);
            }
            context.output().println("Session name set to: " + sessionName);
        }
    }

    /** Returns the current session name, or null if not set. */
    public String getSessionName() {
        return sessionName;
    }
}
