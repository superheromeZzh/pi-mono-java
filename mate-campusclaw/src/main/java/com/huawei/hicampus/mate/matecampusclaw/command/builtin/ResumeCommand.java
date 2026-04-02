package com.huawei.hicampus.mate.matecampusclaw.codingagent.command.builtin;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;

import com.huawei.hicampus.mate.matecampusclaw.ai.types.Message;
import com.huawei.hicampus.mate.matecampusclaw.codingagent.command.SlashCommand;
import com.huawei.hicampus.mate.matecampusclaw.codingagent.command.SlashCommandContext;
import com.huawei.hicampus.mate.matecampusclaw.codingagent.config.AppPaths;

/**
 * Resume a different session. Lists recent sessions and allows selection.
 */
public class ResumeCommand implements SlashCommand {

    @Override
    public String name() { return "resume"; }

    @Override
    public String description() { return "Resume a different session"; }

    @Override
    public void execute(SlashCommandContext context, String arguments) {
        var sm = context.session().getSessionManager();
        if (sm == null) {
            context.output().println("Session persistence is disabled (--no-session)");
            return;
        }

        // Find session directories
        Path sessionsDir = AppPaths.SESSIONS_DIR;
        if (!Files.isDirectory(sessionsDir)) {
            context.output().println("No sessions found.");
            return;
        }

        // Get cwd-based session dir
        String cwd = System.getProperty("user.dir", "");
        String safePath = "--" + cwd.replaceFirst("^[/\\\\]", "").replaceAll("[/\\\\:]", "-") + "--";
        Path sessionDir = sessionsDir.resolve(safePath);

        if (!Files.isDirectory(sessionDir)) {
            context.output().println("No sessions found for current directory.");
            return;
        }

        try (var stream = Files.list(sessionDir)) {
            var files = stream
                    .filter(p -> p.toString().endsWith(".jsonl"))
                    .sorted(Comparator.comparingLong((Path p) -> {
                        try { return Files.getLastModifiedTime(p).toMillis(); }
                        catch (IOException e) { return 0; }
                    }).reversed())
                    .limit(10)
                    .toList();

            if (files.isEmpty()) {
                context.output().println("No sessions found.");
                return;
            }

            // If an argument is given, try to match it as a session ID
            if (arguments != null && !arguments.isBlank()) {
                String target = arguments.trim();
                for (Path file : files) {
                    String name = file.getFileName().toString().replace(".jsonl", "");
                    if (name.equals(target) || name.startsWith(target)) {
                        var messages = sm.loadSession(file);
                        if (!messages.isEmpty()) {
                            context.session().getAgent().clearMessages();
                            for (Message msg : messages) {
                                context.session().getAgent().getState().appendMessage(msg);
                            }
                            context.output().println("Resumed session " + name
                                    + " (" + messages.size() + " messages)");
                        } else {
                            context.output().println("Session " + name + " has no messages.");
                        }
                        return;
                    }
                }
                context.output().println("Session not found: " + target);
                return;
            }

            // List available sessions
            context.output().println("Recent sessions:");
            for (Path file : files) {
                String name = file.getFileName().toString().replace(".jsonl", "");
                try {
                    var mtime = Files.getLastModifiedTime(file);
                    long size = Files.size(file);
                    context.output().println("  " + name + "  ("
                            + mtime.toInstant().toString().substring(0, 16) + ", "
                            + (size / 1024) + "KB)");
                } catch (IOException e) {
                    context.output().println("  " + name);
                }
            }
            context.output().println("Use /resume <session-id> to resume.");
        } catch (IOException e) {
            context.output().println("Error listing sessions: " + e.getMessage());
        }
    }
}
