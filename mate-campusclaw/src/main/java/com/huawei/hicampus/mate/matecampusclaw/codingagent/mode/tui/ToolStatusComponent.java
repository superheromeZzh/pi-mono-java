package com.huawei.hicampus.mate.matecampusclaw.codingagent.mode.tui;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.huawei.hicampus.mate.matecampusclaw.agent.tool.AgentToolResult;
import com.huawei.hicampus.mate.matecampusclaw.ai.types.ContentBlock;
import com.huawei.hicampus.mate.matecampusclaw.ai.types.TextContent;
import com.huawei.hicampus.mate.matecampusclaw.codingagent.tool.edit.EditToolDetails;
import com.huawei.hicampus.mate.matecampusclaw.tui.Component;
import com.huawei.hicampus.mate.matecampusclaw.tui.ansi.AnsiUtils;

/**
 * Displays a tool execution with background color matching campusclaw TS:
 * - Pending: dark purplish-gray background (#282832)
 * - Success: dark greenish-gray background (#283228)
 * - Error: dark reddish-gray background (#3c2828)
 * <p>
 * Supports expand/collapse via Ctrl+O (matching campusclaw behavior).
 * Collapsed: shows up to PREVIEW_LINES lines + "... (N more lines, ctrl+o to expand)"
 * Expanded: shows all content + "(ctrl+o to collapse)"
 */
public class ToolStatusComponent implements Component {

    private static final String ANSI_RESET = "\033[0m";
    private static final String ANSI_BOLD = "\033[1m";
    private static final String ANSI_TOOL_OUTPUT = "\033[38;2;128;128;128m";
    private static final String ANSI_ACCENT = "\033[38;2;138;190;183m";
    private static final String ANSI_DIM_KEY = "\033[38;2;102;102;102m";
    // Background colors matching campusclaw dark theme
    private static final String BG_PENDING = "\033[48;2;40;40;50m";
    private static final String BG_SUCCESS = "\033[48;2;40;50;40m";
    private static final String BG_ERROR = "\033[48;2;60;40;40m";
    // Diff colors
    private static final String ANSI_RED = "\033[31m";
    private static final String ANSI_GREEN = "\033[32m";

    // Preview line limits per tool type (matching campusclaw)
    private static final int PREVIEW_BASH = 5;
    private static final int PREVIEW_READ = 10;
    private static final int PREVIEW_WRITE = 10;
    private static final int PREVIEW_LS = 20;
    private static final int PREVIEW_GREP = 15;
    private static final int PREVIEW_DEFAULT = 10;

    private final String toolName;
    private Object args;
    private boolean complete;
    private boolean error;
    private String resultSummary;
    private String partialResultSummary;
    private boolean expanded = false;
    private long startTimeMs;
    private long endTimeMs;

    public ToolStatusComponent(String toolName) {
        this.toolName = toolName;
        this.startTimeMs = System.currentTimeMillis();
    }

    public void setArgs(Object args) {
        this.args = args;
    }

    public void updatePartialResult(Object partialResult) {
        this.partialResultSummary = summarizeResult(partialResult);
    }

    public void setComplete(boolean error, Object result) {
        this.complete = true;
        this.error = error;
        this.endTimeMs = System.currentTimeMillis();
        this.resultSummary = summarizeResult(result);
        this.partialResultSummary = null;
        invalidate();
    }

    public void setComplete(boolean error) {
        setComplete(error, null);
    }

    /** Toggle expanded/collapsed state (for ctrl+o). */
    public void setExpanded(boolean expanded) {
        this.expanded = expanded;
    }

    public boolean isExpanded() {
        return expanded;
    }

    @Override
    public List<String> render(int width) {
        var lines = new ArrayList<String>();

        String bg = !complete ? BG_PENDING : error ? BG_ERROR : BG_SUCCESS;
        int contentWidth = Math.max(1, width - 2);

        // Top padding line
        lines.add(bgLine("", width, bg));

        // Title line
        String titleContent = buildTitle();
        lines.add(bgLine(" " + titleContent, width, bg));

        // Content
        String content = getDisplayContent();
        if (content != null && !content.isEmpty()) {
            lines.add(bgLine("", width, bg)); // spacer after title
            String[] contentLines = content.split("\n");
            int previewLimit = getPreviewLimit();
            boolean isTailTruncated = isTailTruncatedTool();

            if (expanded) {
                // Show all lines
                for (String line : contentLines) {
                    lines.add(bgLine(" " + truncateText(line, contentWidth), width, bg));
                }
                // Collapse hint (only if there would be truncation)
                if (contentLines.length > previewLimit) {
                    String hint = ANSI_TOOL_OUTPUT + "("
                            + ANSI_DIM_KEY + "ctrl+o" + ANSI_TOOL_OUTPUT + " to collapse)"
                            + ANSI_RESET;
                    lines.add(bgLine(" " + hint, width, bg));
                }
            } else if (contentLines.length <= previewLimit) {
                // Fits within preview — show all
                for (String line : contentLines) {
                    lines.add(bgLine(" " + truncateText(line, contentWidth), width, bg));
                }
            } else if (isTailTruncated) {
                // Bash: show LAST N lines, hint at top (matching campusclaw)
                int hidden = contentLines.length - previewLimit;
                String hint = ANSI_TOOL_OUTPUT + "... (" + hidden + " earlier lines, "
                        + ANSI_DIM_KEY + "ctrl+o" + ANSI_TOOL_OUTPUT + " to expand)"
                        + ANSI_RESET;
                lines.add(bgLine(" " + hint, width, bg));
                int startIdx = contentLines.length - previewLimit;
                for (int i = startIdx; i < contentLines.length; i++) {
                    lines.add(bgLine(" " + truncateText(contentLines[i], contentWidth), width, bg));
                }
            } else {
                // Other tools: show FIRST N lines, hint at bottom (matching campusclaw)
                for (int i = 0; i < previewLimit; i++) {
                    lines.add(bgLine(" " + truncateText(contentLines[i], contentWidth), width, bg));
                }
                int hidden = contentLines.length - previewLimit;
                String hint = ANSI_TOOL_OUTPUT + "... (" + hidden + " more lines, "
                        + ANSI_DIM_KEY + "ctrl+o" + ANSI_TOOL_OUTPUT + " to expand)"
                        + ANSI_RESET;
                lines.add(bgLine(" " + hint, width, bg));
            }
        }

        // "Took X.Xs" line when complete (matching campusclaw)
        if (complete) {
            double elapsed = (endTimeMs - startTimeMs) / 1000.0;
            String took = ANSI_TOOL_OUTPUT + "Took " + String.format("%.1fs", elapsed) + ANSI_RESET;
            lines.add(bgLine("", width, bg)); // spacer before Took
            lines.add(bgLine(" " + took, width, bg));
        }

        // Bottom padding line
        lines.add(bgLine("", width, bg));

        // Blank spacer after tool box (gap between consecutive tool calls)
        lines.add("");

        return lines;
    }

    @Override
    public void invalidate() { }

    private String buildTitle() {
        // Bash: show "$ command" in bold white (matching campusclaw — command IS the title)
        if ("bash".equals(toolName) && args instanceof Map<?, ?> map) {
            Object cmd = map.get("command");
            if (cmd != null) {
                return ANSI_BOLD + "$ " + cmd + ANSI_RESET;
            }
        }
        var sb = new StringBuilder();
        sb.append(ANSI_BOLD).append(toolName).append(ANSI_RESET);
        String detail = extractTitleDetail();
        if (detail != null) {
            sb.append(" ").append(ANSI_ACCENT).append(detail).append(ANSI_RESET);
        } else if (!complete) {
            sb.append(" ").append(ANSI_TOOL_OUTPUT).append("...").append(ANSI_RESET);
        }
        return sb.toString();
    }

    private String extractTitleDetail() {
        if (!(args instanceof Map<?, ?> map)) return null;
        return switch (toolName) {
            case "read", "write", "edit", "ls" -> {
                Object p = map.get("file_path");
                if (p == null) p = map.get("path");
                yield p != null ? shortenPath(p.toString()) : null;
            }
            case "bash" -> {
                Object cmd = map.get("command");
                yield cmd != null ? "$ " + truncateText(cmd.toString(), 80) : null;
            }
            case "glob" -> {
                Object pattern = map.get("pattern");
                Object path = map.get("path");
                String s = pattern != null ? pattern.toString() : "";
                if (path != null) s += " in " + shortenPath(path.toString());
                yield s.isEmpty() ? null : s;
            }
            case "grep" -> {
                Object pattern = map.get("pattern");
                Object path = map.get("path");
                String s = pattern != null ? "/" + pattern + "/" : "";
                if (path != null) s += " in " + shortenPath(path.toString());
                yield s.isEmpty() ? null : s;
            }
            default -> {
                Object p = map.get("file_path");
                if (p == null) p = map.get("path");
                yield p != null ? shortenPath(p.toString()) : null;
            }
        };
    }

    private String getDisplayContent() {
        if ("write".equals(toolName) && args instanceof Map<?, ?> map) {
            Object content = map.get("content");
            if (content != null) return colorizeContent(content.toString());
        }
        // Bash: command is in the title, content is just the output
        if ("bash".equals(toolName)) {
            if (complete && resultSummary != null) return resultSummary;
            if (!complete && partialResultSummary != null) return partialResultSummary;
            return null;
        }
        if (complete && resultSummary != null) return colorizeContent(resultSummary);
        if (!complete && partialResultSummary != null) return colorizeContent(partialResultSummary);
        return null;
    }

    private String colorizeContent(String content) {
        var sb = new StringBuilder();
        for (String line : content.replace("\t", "    ").split("\n", -1)) {
            if (!sb.isEmpty()) sb.append("\n");
            if (line.startsWith("\033[")) {
                sb.append(line);
            } else {
                sb.append(ANSI_TOOL_OUTPUT).append(line).append(ANSI_RESET);
            }
        }
        return sb.toString();
    }

    /** Whether this tool uses tail truncation (show last lines). */
    private boolean isTailTruncatedTool() {
        return "bash".equals(toolName);
    }

    /** Get preview line limit for this tool type (matching campusclaw per-tool limits). */
    private int getPreviewLimit() {
        return switch (toolName) {
            case "bash" -> PREVIEW_BASH;
            case "read" -> PREVIEW_READ;
            case "write" -> PREVIEW_WRITE;
            case "ls", "find" -> PREVIEW_LS;
            case "grep" -> PREVIEW_GREP;
            default -> PREVIEW_DEFAULT;
        };
    }

    private static String shortenPath(String path) {
        String home = System.getProperty("user.home", "");
        if (!home.isEmpty() && path.startsWith(home)) {
            return "~" + path.substring(home.length());
        }
        return path;
    }

    private static String bgLine(String content, int width, String bg) {
        int visLen = AnsiUtils.visibleWidth(content);
        int pad = Math.max(0, width - visLen);
        // Replace every ANSI_RESET in content with reset+bg so background stays continuous
        String fixed = content.replace(ANSI_RESET, ANSI_RESET + bg);
        return bg + fixed + bg + " ".repeat(pad) + ANSI_RESET;
    }

    private static String summarizeResult(Object result) {
        if (result == null) return null;
        String s;
        if (result instanceof AgentToolResult atr && atr.content() != null) {
            var sb = new StringBuilder();
            for (ContentBlock block : atr.content()) {
                if (block instanceof TextContent tc) {
                    if (!sb.isEmpty()) sb.append('\n');
                    sb.append(tc.text());
                }
            }
            if (atr.details() instanceof EditToolDetails details && details.diff() != null) {
                if (!sb.isEmpty()) sb.append('\n');
                sb.append(formatDiff(details.diff()));
            }
            s = sb.toString();
        } else {
            s = result.toString();
        }
        if (s.isBlank()) return null;
        if (s.length() > 5000) return s.substring(0, 5000) + "...";
        return s;
    }

    private static String formatDiff(String diff) {
        var sb = new StringBuilder();
        for (String line : diff.split("\n")) {
            if (line.startsWith("---") || line.startsWith("+++") || line.startsWith("@@")) continue;
            if (!sb.isEmpty()) sb.append('\n');
            if (line.startsWith("-")) sb.append(ANSI_RED).append(line).append(ANSI_RESET);
            else if (line.startsWith("+")) sb.append(ANSI_GREEN).append(line).append(ANSI_RESET);
            else sb.append(ANSI_TOOL_OUTPUT).append(line).append(ANSI_RESET);
        }
        return sb.toString();
    }

    private static String truncateText(String text, int maxWidth) {
        if (text == null) return "";
        int visWidth = AnsiUtils.visibleWidth(text);
        if (visWidth <= maxWidth) return text;
        return AnsiUtils.sliceByColumn(text, 0, Math.max(1, maxWidth - 3)) + "...";
    }
}
