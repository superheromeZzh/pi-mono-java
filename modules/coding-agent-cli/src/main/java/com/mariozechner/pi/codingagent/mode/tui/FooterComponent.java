package com.mariozechner.pi.codingagent.mode.tui;

import com.mariozechner.pi.codingagent.util.GitUtils;
import com.mariozechner.pi.tui.Component;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Bottom status bar matching pi-mono TS footer layout:
 * <pre>
 *   ~/path (branch)
 *   ↑1.5k ↓200 R832 $0.001 0.7%/200k (auto)           (zai) glm-5 • medium
 * </pre>
 */
public class FooterComponent implements Component {

    private static final String ANSI_DIM = "\033[2m";
    private static final String ANSI_RESET = "\033[0m";
    private static final String ANSI_RED = "\033[31m";
    private static final String ANSI_YELLOW = "\033[33m";

    private String modelName = "";
    private String providerName = "";
    private String thinkingLevel = "";
    private String cwd = "";
    private int inputTokens;
    private int outputTokens;
    private int cacheRead;
    private int cacheWrite;
    private int contextWindow;
    private double totalCost;
    private boolean autoCompactEnabled = true;

    // Git branch cache
    private String cachedBranch;
    private long branchCacheTime;
    private static final long BRANCH_CACHE_TTL_MS = 10_000; // 10s

    public void setModel(String provider, String model, int contextWindow) {
        this.providerName = provider != null ? provider : "";
        this.modelName = model != null ? model : "";
        this.contextWindow = contextWindow;
    }

    public void setThinkingLevel(String level) {
        this.thinkingLevel = level != null ? level : "";
    }

    public void setCwd(String cwd) {
        this.cwd = cwd != null ? cwd : "";
        // Invalidate branch cache on cwd change
        this.cachedBranch = null;
        this.branchCacheTime = 0;
    }

    public void setAutoCompactEnabled(boolean enabled) {
        this.autoCompactEnabled = enabled;
    }

    public void updateUsage(int input, int output, int cacheRead, int cacheWrite, double cost) {
        this.inputTokens += input;
        this.outputTokens += output;
        this.cacheRead += cacheRead;
        this.cacheWrite += cacheWrite;
        this.totalCost += cost;
    }

    public void resetUsage() {
        this.inputTokens = 0;
        this.outputTokens = 0;
        this.cacheRead = 0;
        this.cacheWrite = 0;
        this.totalCost = 0;
    }

    @Override
    public void invalidate() { }

    @Override
    public List<String> render(int width) {
        if (width <= 0) return List.of();

        var lines = new ArrayList<String>();

        // Line 1: pwd with ~ substitution + git branch
        if (!cwd.isEmpty()) {
            String home = System.getProperty("user.home", "");
            String displayPath = cwd;
            if (!home.isEmpty() && cwd.startsWith(home)) {
                displayPath = "~" + cwd.substring(home.length());
            }

            String branch = getGitBranch();
            if (branch != null && !branch.isEmpty()) {
                displayPath = displayPath + " (" + branch + ")";
            }

            lines.add(ANSI_DIM + truncate(displayPath, width) + ANSI_RESET);
        }

        // Line 2: stats (left-aligned) + model info (right-aligned)
        String left = buildLeft();
        String right = buildRight();

        int leftVisible = stripAnsi(left).length();
        int rightVisible = stripAnsi(right).length();
        int gap = Math.max(2, width - leftVisible - rightVisible);

        String statsLine;
        if (leftVisible + rightVisible + 2 <= width) {
            statsLine = ANSI_DIM + left + " ".repeat(gap) + right + ANSI_RESET;
        } else {
            // Truncate — prioritize left side
            statsLine = ANSI_DIM + truncate(left + "  " + right, width) + ANSI_RESET;
        }
        lines.add(statsLine);

        return lines;
    }

    private String buildLeft() {
        var sb = new StringBuilder("  ");
        sb.append("↑").append(formatTokens(inputTokens));
        sb.append(" ↓").append(formatTokens(outputTokens));
        if (cacheRead > 0) {
            sb.append(" R").append(formatTokens(cacheRead));
        }
        if (cacheWrite > 0) {
            sb.append(" W").append(formatTokens(cacheWrite));
        }
        if (totalCost > 0) {
            sb.append(" $").append(String.format("%.3f", totalCost));
        }
        if (contextWindow > 0) {
            int totalIn = inputTokens + cacheRead;
            double pct = totalIn * 100.0 / contextWindow;
            // Color-code context percentage
            String pctStr = String.format("%.1f%%", pct);
            if (pct > 90) {
                sb.append(" ").append(ANSI_RESET).append(ANSI_RED).append(pctStr).append(ANSI_RESET).append(ANSI_DIM);
            } else if (pct > 70) {
                sb.append(" ").append(ANSI_RESET).append(ANSI_YELLOW).append(pctStr).append(ANSI_RESET).append(ANSI_DIM);
            } else {
                sb.append(" ").append(pctStr);
            }
            sb.append("/").append(formatTokens(contextWindow));
        }
        if (autoCompactEnabled) {
            sb.append(" (auto)");
        }
        return sb.toString();
    }

    private String buildRight() {
        if (modelName.isEmpty()) return "";
        var sb = new StringBuilder();
        if (!providerName.isEmpty()) {
            sb.append("(").append(providerName).append(") ");
        }
        sb.append(modelName);
        if (!thinkingLevel.isEmpty()) {
            sb.append(" • ").append(thinkingLevel);
        }
        return sb.toString();
    }

    private String getGitBranch() {
        long now = System.currentTimeMillis();
        if (cachedBranch != null && (now - branchCacheTime) < BRANCH_CACHE_TTL_MS) {
            return cachedBranch;
        }
        if (!cwd.isEmpty()) {
            cachedBranch = GitUtils.getCurrentBranch(Path.of(cwd)).orElse(null);
            branchCacheTime = now;
        }
        return cachedBranch;
    }

    public static String formatTokens(int tokens) {
        if (tokens >= 10_000_000) return String.format("%.0fM", tokens / 1_000_000.0);
        if (tokens >= 1_000_000) return String.format("%.1fM", tokens / 1_000_000.0);
        if (tokens >= 10_000) return String.format("%.0fk", tokens / 1000.0);
        if (tokens >= 1000) return String.format("%.1fk", tokens / 1000.0);
        return String.valueOf(tokens);
    }

    private static String stripAnsi(String s) {
        return s.replaceAll("\033\\[[;\\d]*[a-zA-Z]", "");
    }

    private static String truncate(String s, int maxLen) {
        String stripped = stripAnsi(s);
        if (stripped.length() <= maxLen) return s;
        return s.substring(0, Math.max(0, maxLen - 3)) + "...";
    }
}
