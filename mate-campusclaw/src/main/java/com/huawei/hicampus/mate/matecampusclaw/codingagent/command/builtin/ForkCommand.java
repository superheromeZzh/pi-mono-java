package com.huawei.hicampus.mate.matecampusclaw.codingagent.command.builtin;

import com.huawei.hicampus.mate.matecampusclaw.codingagent.command.SlashCommand;
import com.huawei.hicampus.mate.matecampusclaw.codingagent.command.SlashCommandContext;

/**
 * Create a new fork from the current session.
 * Starts a new session file while keeping the current messages.
 */
public class ForkCommand implements SlashCommand {

    @Override
    public String name() { return "fork"; }

    @Override
    public String description() { return "Create a new fork from current session"; }

    @Override
    public void execute(SlashCommandContext context, String arguments) {
        var sm = context.session().getSessionManager();
        if (sm == null) {
            context.output().println("Session persistence is disabled (--no-session)");
            return;
        }

        String cwd = System.getProperty("user.dir", "");
        String oldId = sm.getSessionId();

        // Create a new session file (fork), keeping current messages in memory
        sm.createSession(cwd);
        String newId = sm.getSessionId();

        // Persist all current messages to the new session file
        var messages = context.session().getAgent().getState().getMessages();
        for (var msg : messages) {
            sm.appendMessage(msg);
        }

        context.output().println("Forked session " + oldId + " → " + newId
                + " (" + messages.size() + " messages)");
    }
}
