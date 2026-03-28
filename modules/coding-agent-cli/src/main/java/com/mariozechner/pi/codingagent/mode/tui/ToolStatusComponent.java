package com.mariozechner.pi.codingagent.mode.tui;

import com.mariozechner.pi.agent.tool.AgentToolResult;
import com.mariozechner.pi.ai.types.ContentBlock;
import com.mariozechner.pi.ai.types.TextContent;
import com.mariozechner.pi.codingagent.tool.edit.EditToolDetails;
import com.mariozechner.pi.tui.Component;
import com.mariozechner.pi.tui.ansi.AnsiUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Displays a tool execution with background color matching pi-mono TS:
 * - Pending: dark purplish-gray background (#282832)
 * - Success: dark greenish-gray background (#283228)
 * - Error: dark reddish-gray background (#3c2828)
 * <p>
 * Title shows bold tool name + accent-colored file path.
 * Content shows file content or result summary with tool-specific formatting.
 */
public class ToolStatusComponent implements Component {

    private static final String ANSI_RESET = "\033[0m";
    private static final String ANSI_BOLD = "\033[1m";
    // Tool output text color — gray #808080
    private static final String ANSI_TOOL_OUTPUT = "\033[38;2;128;128;128m";
    // Accent color for file paths — #8abeb7
    private static final String ANSI_ACCENT = "\033[38;2;138;190;183m";
    // Background colors matching pi-mono dark theme
    private static final String BG_PENDING = "\033[48;2;40;40;50m";  // #282832
    private static final String BG_SUCCESS = "\033[48;2;40;50;40m";  // #283228
    private static final String BG_ERROR = "\033[48;2;60;40;40m";    // #3c2828
    // Diff colors
    private static final String ANSI_RED = "\033[31m";
    private static final String ANSI_GREEN = "\033[32m";
    private static final String ANSI_DIM = "\033[38;2;128;128;128m";

    private static final int MAX_CONTENT_LINES = 10;

    private final String toolName;
    private Object args;
    private boolean complete;
    private boolean error;
    private String resultSummary;
    private String partialResultSummary;

    public ToolStatusComponent(String toolName) {
        this.toolName = toolName;
    }

    public void setArgs(Object args) {
        this.args = args;
    }

    /** Update with partial result (live progress during tool execution). */
    public void updatePartialResult(Object partialResult) {
        this.partialResultSummary = summarizeResult(partialResult);
    }

    public void setComplete(boolean error, Object result) {
        this.complete = true;
        this.error = error;
        this.resultSummary = summarizeResult(result);
        this.partialResultSummary = null;
        invalidate();
    }

    public void setComplete(boolean error) {
        setComplete(error, null);
    }

    @Override
    public List<String> render(int width) {
        var lines = new ArrayList<String>();

        // Select background color based on state
        String bg;
        if (!complete) {
            bg = BG_PENDING;
        } else if (error) {
            bg = BG_ERROR;
        } else {
            bg = BG_SUCCESS;
        }

        int contentWidth = Math.max(1, width - 2); // 1-space padding each side

        // Top padding line
        lines.add(bgLine("", width, bg));

        // Title line: bold tool name + accent file path (or args summary)
        String titleContent = buildTitle();
        lines.add(bgLine(" " + titleContent, width, bg));

        // Content — show file content or result
        String content = getDisplayContent();
        if (content != null && !content.isEmpty()) {
            lines.add(bgLine("", width, bg)); // spacer
            String[] contentLines = content.split("\n");
            int linesToShow = Math.min(contentLines.length, MAX_CONTENT_LINES);
            for (int i = 0; i < linesToShow; i++) {
                String truncated = truncateText(contentLines[i], contentWidth);
                lines.add(bgLine(" " + truncated, width, bg));
            }
            if (contentLines.length > MAX_CONTENT_LINES) {
                String moreText = ANSI_TOOL_OUTPUT + "... (" + (contentLines.length - MAX_CONTENT_LINES)
                        + " more lines)" + ANSI_RESET;
                lines.add(bgLine(" " + moreText, width, bg));
            }
        }

        // Bottom padding line
        lines.add(bgLine("", width, bg));

        return lines;
    }

    @Override
    public void invalidate() {
        // No cache
    }

    /** Build the title line: bold tool name + accent-colored path or relevant info. */
    private String buildTitle() {
        String path = extractPath();
        var sb = new StringBuilder();
        sb.append(ANSI_BOLD).append(toolName).append(ANSI_RESET);

        if (path != null) {
            sb.append(" ").append(ANSI_ACCENT).append(shortenPath(path)).append(ANSI_RESET);
        } else if (!complete) {
            sb.append(" ").append(ANSI_TOOL_OUTPUT).append("...").append(ANSI_RESET);
        }

        return sb.toString();
    }

    /** Get the content to display — file content for write, result for others. */
    private String getDisplayContent() {
        // For write tool, show file content from args
        if ("write".equals(toolName) && args instanceof Map<?, ?> map) {
            Object content = map.get("content");
            if (content != null) {
                return colorizeContent(content.toString());
            }
        }

        // For bash tool, show command
        if ("bash".equals(toolName) && args instanceof Map<?, ?> map) {
            Object cmd = map.get("command");
            String display = cmd != null ? "$ " + cmd : null;
            // Show result too if available
            if (complete && resultSummary != null) {
                display = (display != null ? display + "\n" : "") + resultSummary;
            } else if (!complete && partialResultSummary != null) {
                display = (display != null ? display + "\n" : "") + partialResultSummary;
            }
            return display;
        }

        // For edit tool, show diff from result
        if (complete && resultSummary != null) {
            return resultSummary;
        }
        if (!complete && partialResultSummary != null) {
            return partialResultSummary;
        }

        return null;
    }

    /** Colorize content for display in tool output (gray text). */
    private String colorizeContent(String content) {
        var sb = new StringBuilder();
        for (String line : content.split("\n", -1)) {
            if (!sb.isEmpty()) sb.append("\n");
            sb.append(ANSI_TOOL_OUTPUT).append(line).append(ANSI_RESET);
        }
        return sb.toString();
    }

    /** Extract file path from args. */
    private String extractPath() {
        if (args instanceof Map<?, ?> map) {
            Object path = map.get("file_path");
            if (path == null) path = map.get("path");
            return path != null ? path.toString() : null;
        }
        return null;
    }

    /** Shorten a path by replacing home directory with ~. */
    private static String shortenPath(String path) {
        String home = System.getProperty("user.home", "");
        if (!home.isEmpty() && path.startsWith(home)) {
            return "~" + path.substring(home.length());
        }
        return path;
    }

    /** Wrap content line with background color, padding to full width. */
    private static String bgLine(String content, int width, String bg) {
        int visLen = AnsiUtils.visibleWidth(content);
        int pad = Math.max(0, width - visLen);
        return bg + content + " ".repeat(pad) + ANSI_RESET;
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
            // Append diff from EditToolDetails if available
            if (atr.details() instanceof EditToolDetails details && details.diff() != null) {
                if (!sb.isEmpty()) sb.append('\n');
                sb.append(formatDiff(details.diff()));
            }
            s = sb.toString();
        } else {
            s = result.toString();
        }
        if (s.isBlank()) return null;
        if (s.length() > 2000) {
            return s.substring(0, 2000) + "...";
        }
        return s;
    }

    /** Format a unified diff with red (-) and green (+) coloring. */
    private static String formatDiff(String diff) {
        var sb = new StringBuilder();
        for (String line : diff.split("\n")) {
            if (line.startsWith("---") || line.startsWith("+++") || line.startsWith("@@")) {
                continue;
            }
            if (!sb.isEmpty()) sb.append('\n');
            if (line.startsWith("-")) {
                sb.append(ANSI_RED).append(line).append(ANSI_RESET);
            } else if (line.startsWith("+")) {
                sb.append(ANSI_GREEN).append(line).append(ANSI_RESET);
            } else {
                sb.append(ANSI_DIM).append(line).append(ANSI_RESET);
            }
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
