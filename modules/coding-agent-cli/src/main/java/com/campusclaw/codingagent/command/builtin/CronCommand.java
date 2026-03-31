package com.campusclaw.codingagent.command.builtin;

import java.nio.file.Path;

import com.campusclaw.codingagent.command.SlashCommand;
import com.campusclaw.codingagent.command.SlashCommandContext;
import com.campusclaw.codingagent.cron.SystemSchedulerInstaller;

/**
 * Slash command for managing real cron (OS scheduler integration).
 *
 * Usage:
 *   /cron install [interval]  — register with launchd/crontab (default: 60s)
 *   /cron uninstall           — unregister from OS scheduler
 *   /cron status              — check if OS scheduler is configured
 */
public class CronCommand implements SlashCommand {

    @Override
    public String name() {
        return "cron";
    }

    @Override
    public String description() {
        return "Manage real cron scheduler (install/uninstall/status)";
    }

    @Override
    public void execute(SlashCommandContext context, String arguments) {
        if (arguments == null || arguments.isBlank()) {
            printUsage(context);
            return;
        }

        String[] parts = arguments.trim().split("\\s+", 2);
        String action = parts[0];

        Path launcherScript = SystemSchedulerInstaller.detectLauncherScript();
        if (launcherScript == null && !"status".equals(action)) {
            context.output().println("Error: Cannot find campusclaw.sh launcher script.\n"
                    + "Run this command from the project root directory.");
            return;
        }

        var installer = new SystemSchedulerInstaller(
                launcherScript != null ? launcherScript : Path.of("campusclaw.sh"));

        try {
            switch (action) {
                case "install" -> {
                    int interval = 60;
                    if (parts.length > 1) {
                        try {
                            interval = Integer.parseInt(parts[1].trim());
                        } catch (NumberFormatException e) {
                            context.output().println("Error: Invalid interval: " + parts[1]);
                            return;
                        }
                    }
                    context.output().println(installer.install(interval));
                }
                case "uninstall", "remove" -> context.output().println(installer.uninstall());
                case "status" -> context.output().println(installer.status());
                default -> printUsage(context);
            }
        } catch (Exception e) {
            context.output().println("Error: " + e.getMessage());
        }
    }

    private void printUsage(SlashCommandContext context) {
        context.output().println("""
                Usage:
                  /cron install [interval]  Register with OS scheduler (default: 60s)
                  /cron uninstall           Unregister from OS scheduler
                  /cron status              Check scheduler status

                This registers campusclaw --cron-tick with launchd (macOS) or
                crontab (Linux) so cron jobs execute even without an active session."""
                .stripIndent().stripTrailing());
    }
}
