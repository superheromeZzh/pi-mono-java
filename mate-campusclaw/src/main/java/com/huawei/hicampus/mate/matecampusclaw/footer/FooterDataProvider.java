package com.huawei.hicampus.mate.matecampusclaw.codingagent.footer;

import java.util.List;

import com.huawei.hicampus.mate.matecampusclaw.ai.types.Model;
import com.huawei.hicampus.mate.matecampusclaw.ai.types.ThinkingLevel;
import com.huawei.hicampus.mate.matecampusclaw.ai.types.Usage;

import jakarta.annotation.Nullable;

/**
 * Provides data for the status bar / footer display in the TUI.
 * Aggregates model info, token usage, session stats, and keybinding hints.
 */
public class FooterDataProvider {

    public record FooterData(
        String modelName,
        @Nullable String providerName,
        @Nullable ThinkingLevel thinkingLevel,
        @Nullable TokenStats tokenStats,
        @Nullable SessionStats sessionStats,
        List<FooterHint> hints,
        boolean isStreaming
    ) {}

    public record TokenStats(
        int inputTokens,
        int outputTokens,
        int cacheReadTokens,
        int totalTokens,
        double totalCostUsd
    ) {
        public static TokenStats from(Usage usage) {
            double cost = usage.cost() != null ? usage.cost().total() : 0.0;
            return new TokenStats(usage.input(), usage.output(),
                usage.cacheRead(), usage.totalTokens(), cost);
        }

        public String formatTokens() {
            if (totalTokens < 1000) return totalTokens + " tok";
            if (totalTokens < 1_000_000) return String.format("%.1fK tok", totalTokens / 1000.0);
            return String.format("%.1fM tok", totalTokens / 1_000_000.0);
        }

        public String formatCost() {
            if (totalCostUsd < 0.01) return String.format("$%.4f", totalCostUsd);
            if (totalCostUsd < 1.0) return String.format("$%.3f", totalCostUsd);
            return String.format("$%.2f", totalCostUsd);
        }
    }

    public record SessionStats(
        int turnCount,
        int messageCount,
        long sessionDurationMs
    ) {
        public String formatDuration() {
            long seconds = sessionDurationMs / 1000;
            if (seconds < 60) return seconds + "s";
            long minutes = seconds / 60;
            if (minutes < 60) return minutes + "m " + (seconds % 60) + "s";
            long hours = minutes / 60;
            return hours + "h " + (minutes % 60) + "m";
        }
    }

    public record FooterHint(String key, String description) {
        public String format() {
            return "\033[36m" + key + "\033[0m " + description;
        }
    }

    private volatile String modelName = "unknown";
    private volatile String providerName = null;
    private volatile ThinkingLevel thinkingLevel = null;
    private volatile TokenStats tokenStats = null;
    private volatile SessionStats sessionStats = null;
    private volatile boolean streaming = false;
    private final List<FooterHint> defaultHints = List.of(
        new FooterHint("Ctrl+C", "clear"),
        new FooterHint("Ctrl+D", "exit"),
        new FooterHint("Shift+Tab", "thinking"),
        new FooterHint("Ctrl+P", "model"),
        new FooterHint("Esc", "interrupt")
    );

    /** Update model info. */
    public void setModel(Model model) {
        this.modelName = model.name();
        this.providerName = model.provider().value();
    }

    /** Update thinking level. */
    public void setThinkingLevel(ThinkingLevel level) {
        this.thinkingLevel = level;
    }

    /** Update token stats from usage. */
    public void updateUsage(Usage usage) {
        this.tokenStats = TokenStats.from(usage);
    }

    /** Update session stats. */
    public void updateSession(int turns, int messages, long durationMs) {
        this.sessionStats = new SessionStats(turns, messages, durationMs);
    }

    /** Set streaming status. */
    public void setStreaming(boolean streaming) {
        this.streaming = streaming;
    }

    /** Get current footer data snapshot. */
    public FooterData getFooterData() {
        return new FooterData(modelName, providerName, thinkingLevel,
            tokenStats, sessionStats, defaultHints, streaming);
    }

    /** Format the footer as a single-line status bar. */
    public String formatStatusBar(int width) {
        var sb = new StringBuilder();
        // Model name
        sb.append("\033[1m").append(modelName).append("\033[0m");
        if (providerName != null) {
            sb.append(" \033[2m(").append(providerName).append(")\033[0m");
        }
        // Thinking level
        if (thinkingLevel != null && thinkingLevel != ThinkingLevel.OFF) {
            sb.append(" \033[33m[").append(thinkingLevel.name().toLowerCase()).append("]\033[0m");
        }
        // Token stats
        if (tokenStats != null) {
            sb.append(" │ ").append(tokenStats.formatTokens());
            sb.append(" ").append(tokenStats.formatCost());
        }
        // Streaming indicator
        if (streaming) {
            sb.append(" \033[36m●\033[0m");
        }
        return sb.toString();
    }
}
