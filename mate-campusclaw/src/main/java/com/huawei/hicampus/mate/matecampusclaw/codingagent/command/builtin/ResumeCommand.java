/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.hicampus.mate.matecampusclaw.codingagent.command.builtin;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;

import com.huawei.hicampus.mate.matecampusclaw.ai.types.Message;
import com.huawei.hicampus.mate.matecampusclaw.codingagent.command.SlashCommand;
import com.huawei.hicampus.mate.matecampusclaw.codingagent.command.SlashCommandContext;
import com.huawei.hicampus.mate.matecampusclaw.codingagent.config.AppPaths;
import com.huawei.hicampus.mate.matecampusclaw.codingagent.session.SessionManager;

/**
 * Resume a different session. Lists recent sessions and allows selection.
 *
 * @version [br_eCampusCore 25.1.0_Next, 2026/05/06]
 * @since [br_eCampusCore 25.1.0_Next]
 */
public class ResumeCommand implements SlashCommand {

    @Override
    public String name() {
        return "resume";
    }

    @Override
    public String description() {
        return "Resume a different session";
    }

    @Override
    public void execute(SlashCommandContext context, String arguments) {
        var sm = context.session().getSessionManager();
        if (sm == null) {
            context.output().println("Session persistence is disabled (--no-session)");
            return;
        }
        Path sessionDir = resolveSessionDir();
        if (sessionDir == null) {
            context.output().println("No sessions found for current directory.");
            return;
        }
        try (var stream = Files.list(sessionDir)) {
            var files = recentSessionFiles(stream);
            if (files.isEmpty()) {
                context.output().println("No sessions found.");
                return;
            }
            if (arguments != null && !arguments.isBlank()) {
                resumeByArgument(context, sm, files, arguments.trim());
            } else {
                listSessions(context, files);
            }
        } catch (IOException e) {
            context.output().println("Error listing sessions: " + e.getMessage());
        }
    }

    private static Path resolveSessionDir() {
        Path sessionsDir = AppPaths.SESSIONS_DIR;
        if (!Files.isDirectory(sessionsDir)) {
            return null;
        }
        String cwd = System.getProperty("user.dir", "");
        String safePath = "--" + cwd.replaceFirst("^[/\\\\]", "").replaceAll("[/\\\\:]", "-") + "--";
        Path sessionDir = sessionsDir.resolve(safePath);
        return Files.isDirectory(sessionDir) ? sessionDir : null;
    }

    private static List<Path> recentSessionFiles(java.util.stream.Stream<Path> stream) {
        return stream.filter(p -> p.toString().endsWith(".jsonl"))
                .sorted(Comparator.comparingLong((Path p) -> {
                            try {
                                return Files.getLastModifiedTime(p).toMillis();
                            } catch (IOException e) {
                                return 0L;
                            }
                        })
                        .reversed())
                .limit(10)
                .toList();
    }

    private static void resumeByArgument(
            SlashCommandContext context, SessionManager sm, List<Path> files, String target) {
        for (Path file : files) {
            String name = file.getFileName().toString().replace(".jsonl", "");
            if (!name.equals(target) && !name.startsWith(target)) {
                continue;
            }
            var messages = sm.loadSession(file);
            if (messages.isEmpty()) {
                context.output().println("Session " + name + " has no messages.");
                return;
            }
            context.session().getAgent().clearMessages();
            for (Message msg : messages) {
                context.session().getAgent().getState().appendMessage(msg);
            }
            context.output().println("Resumed session " + name + " (" + messages.size() + " messages)");
            return;
        }
        context.output().println("Session not found: " + target);
    }

    private static void listSessions(SlashCommandContext context, List<Path> files) {
        context.output().println("Recent sessions:");
        for (Path file : files) {
            String name = file.getFileName().toString().replace(".jsonl", "");
            try {
                var mtime = Files.getLastModifiedTime(file);
                long size = Files.size(file);
                context.output()
                        .println("  " + name + "  ("
                                + mtime.toInstant().toString().substring(0, 16) + ", "
                                + (size / 1024) + "KB)");
            } catch (IOException e) {
                context.output().println("  " + name);
            }
        }
        context.output().println("Use /resume <session-id> to resume.");
    }
}
