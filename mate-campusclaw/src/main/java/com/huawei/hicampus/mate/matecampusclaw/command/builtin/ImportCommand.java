package com.huawei.hicampus.mate.matecampusclaw.codingagent.command.builtin;

import java.nio.file.Files;
import java.nio.file.Path;

import com.huawei.hicampus.mate.matecampusclaw.ai.types.Message;
import com.huawei.hicampus.mate.matecampusclaw.codingagent.command.SlashCommand;
import com.huawei.hicampus.mate.matecampusclaw.codingagent.command.SlashCommandContext;

/**
 * Import and resume a session from a JSONL file.
 */
public class ImportCommand implements SlashCommand {

    @Override
    public String name() { return "import"; }

    @Override
    public String description() { return "Import and resume a session from a JSONL file"; }

    @Override
    public void execute(SlashCommandContext context, String arguments) {
        if (arguments == null || arguments.isBlank()) {
            context.output().println("Usage: /import <path-to-session.jsonl>");
            return;
        }

        Path file = Path.of(arguments.trim());
        if (!Files.exists(file)) {
            context.output().println("File not found: " + file);
            return;
        }

        var sm = context.session().getSessionManager();
        if (sm == null) {
            context.output().println("Session persistence is disabled (--no-session)");
            return;
        }

        var messages = sm.loadSession(file);
        if (messages.isEmpty()) {
            context.output().println("No messages found in session file.");
            return;
        }

        // Clear current messages and load imported ones
        context.session().getAgent().clearMessages();
        for (Message msg : messages) {
            context.session().getAgent().getState().appendMessage(msg);
        }

        context.output().println("Imported session " + sm.getSessionId()
                + " with " + messages.size() + " messages.");
    }
}
