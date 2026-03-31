package com.campusclaw.codingagent.mode.tui;

import java.util.List;

import com.campusclaw.tui.Component;

/**
 * Renders a dim visual separator line for background task output in the chat area.
 * Example output:
 * <pre>
 *  ── [Task: check-system] (646bfa3d...) ──────────────────
 * </pre>
 */
public class TaskHeaderComponent implements Component {

    private static final String ANSI_DIM = "\033[2m";
    private static final String ANSI_RESET = "\033[0m";

    private final String taskName;
    private final String taskIdShort;

    public TaskHeaderComponent(String taskId, String taskName) {
        this.taskName = taskName != null ? taskName : "unnamed";
        this.taskIdShort = taskId != null && taskId.length() > 8
                ? taskId.substring(0, 8) + "..."
                : taskId != null ? taskId : "?";
    }

    @Override
    public List<String> render(int width) {
        // Build: " ── [Task: name] (id...) ──────"
        String label = " [Task: " + taskName + "] (" + taskIdShort + ") ";
        int labelVisLen = label.length();
        int dashCount = Math.max(0, width - labelVisLen - 1);
        if (dashCount < 2) dashCount = 2;
        String dashes = "─".repeat(dashCount);
        String line = ANSI_DIM + dashes + label + dashes + ANSI_RESET;
        return List.of("", line, "");
    }

    @Override
    public void invalidate() {
        // No cache to invalidate
    }
}
