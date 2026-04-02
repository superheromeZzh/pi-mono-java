package com.huawei.hicampus.mate.matecampusclaw.codingagent.command.builtin;

import com.huawei.hicampus.mate.matecampusclaw.codingagent.command.SlashCommand;
import com.huawei.hicampus.mate.matecampusclaw.codingagent.command.SlashCommandContext;
import com.huawei.hicampus.mate.matecampusclaw.codingagent.loop.LoopManager;

/**
 * Slash command for managing in-session recurring prompts.
 *
 * Usage:
 *   /loop [interval] <prompt>   — start a loop (default interval: 10m)
 *   /loop stop [id]             — stop one or all loops
 *   /loop list                  — list active loops
 */
public class LoopCommand implements SlashCommand {

    private final LoopManager loopManager;

    public LoopCommand(LoopManager loopManager) {
        this.loopManager = loopManager;
    }

    @Override
    public String name() {
        return "loop";
    }

    @Override
    public String description() {
        return "Run a prompt repeatedly at an interval (e.g. /loop 5m check deploy status)";
    }

    @Override
    public void execute(SlashCommandContext context, String arguments) {
        if (!loopManager.isInitialized()) {
            context.output().println("Error: /loop is only available in interactive mode");
            return;
        }

        if (arguments == null || arguments.isBlank()) {
            printUsage(context);
            return;
        }

        String trimmed = arguments.trim();
        String[] parts = trimmed.split("\\s+", 2);

        if ("stop".equals(parts[0])) {
            handleStop(context, parts.length > 1 ? parts[1].trim() : null);
            return;
        }

        if ("list".equals(parts[0])) {
            handleList(context);
            return;
        }

        // Parse: [interval] <prompt>
        long intervalMs = parseInterval(parts[0]);
        String prompt;
        if (intervalMs > 0 && parts.length > 1) {
            prompt = parts[1];
        } else {
            // No valid interval — treat entire argument as prompt with default 10m
            intervalMs = 10 * 60 * 1000;
            prompt = trimmed;
        }

        if (prompt.isBlank()) {
            context.output().println("Error: prompt cannot be empty");
            return;
        }

        String id = loopManager.start(prompt, intervalMs);
        context.output().println("Started loop #" + id + " (every " + formatInterval(intervalMs) + "): " + prompt);
    }

    private void handleStop(SlashCommandContext context, String id) {
        if (id != null && !id.isBlank()) {
            boolean stopped = loopManager.stop(id);
            context.output().println(stopped ? "Stopped loop #" + id : "Loop not found: #" + id);
        } else {
            int count = loopManager.stopAll();
            context.output().println("Stopped " + count + " loop(s)");
        }
    }

    private void handleList(SlashCommandContext context) {
        var loops = loopManager.list();
        if (loops.isEmpty()) {
            context.output().println("No active loops.");
            return;
        }
        var sb = new StringBuilder();
        sb.append("Active loops (").append(loops.size()).append("):\n");
        for (var entry : loops) {
            sb.append("  #").append(entry.id())
              .append("  every ").append(formatInterval(entry.intervalMs()))
              .append("  ").append(truncate(entry.prompt(), 60))
              .append("\n");
        }
        context.output().println(sb.toString().stripTrailing());
    }

    private void printUsage(SlashCommandContext context) {
        context.output().println("""
            Usage:
              /loop [interval] <prompt>   Start a recurring prompt (default: 10m)
              /loop stop [id]             Stop one or all loops
              /loop list                  List active loops

            Intervals: 5s, 2m, 1h (seconds, minutes, hours)

            Examples:
              /loop 5m check deploy status
              /loop 30s output a random emoji
              /loop list
              /loop stop 1""".stripIndent().stripTrailing());
    }

    /**
     * Parse an interval string like "5s", "2m", "1h", "1min", "30sec", "2hr".
     * Returns -1 if not a valid interval.
     */
    static long parseInterval(String s) {
        if (s == null || s.length() < 2) return -1;
        // Extract trailing non-digit suffix
        int i = 0;
        while (i < s.length() && (Character.isDigit(s.charAt(i)) || s.charAt(i) == '.')) {
            i++;
        }
        if (i == 0 || i == s.length()) return -1;
        String numStr = s.substring(0, i);
        String unit = s.substring(i).toLowerCase();
        try {
            long value = Long.parseLong(numStr);
            if (value <= 0) return -1;
            return switch (unit) {
                case "s", "sec", "secs", "second", "seconds" -> value * 1000;
                case "m", "min", "mins", "minute", "minutes" -> value * 60 * 1000;
                case "h", "hr", "hrs", "hour", "hours" -> value * 3600 * 1000;
                default -> -1;
            };
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    static String formatInterval(long ms) {
        if (ms >= 3_600_000 && ms % 3_600_000 == 0) return (ms / 3_600_000) + "h";
        if (ms >= 60_000 && ms % 60_000 == 0) return (ms / 60_000) + "m";
        if (ms >= 1000 && ms % 1000 == 0) return (ms / 1000) + "s";
        return ms + "ms";
    }

    private static String truncate(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max - 3) + "...";
    }
}
