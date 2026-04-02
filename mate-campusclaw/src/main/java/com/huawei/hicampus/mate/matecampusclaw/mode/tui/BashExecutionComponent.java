package com.huawei.hicampus.mate.matecampusclaw.codingagent.mode.tui;

import java.util.ArrayList;
import java.util.List;

import com.huawei.hicampus.mate.matecampusclaw.tui.Component;
import com.huawei.hicampus.mate.matecampusclaw.tui.ansi.AnsiUtils;

/**
 * Displays a user-initiated bash command ({@code !} prefix) and its output.
 * Matches campusclaw TS bash execution display.
 */
public class BashExecutionComponent implements Component {

    // Colors matching campusclaw dark theme
    private static final String ANSI_BASH_COLOR = "\033[38;2;181;189;104m"; // bashMode green #b5bd68
    private static final String ANSI_BOLD = "\033[1m";
    private static final String ANSI_GRAY = "\033[38;2;128;128;128m";      // gray #808080
    private static final String ANSI_ERROR = "\033[38;2;204;102;102m";     // error #cc6666
    private static final String ANSI_RESET = "\033[0m";

    private final String command;
    private final boolean excluded;
    private String output;
    private Integer exitCode;
    private boolean complete;

    /**
     * @param command  the bash command text
     * @param excluded true if this was a {@code !!} command (excluded from context)
     */
    public BashExecutionComponent(String command, boolean excluded) {
        this.command = command;
        this.excluded = excluded;
    }

    public void setResult(String output, Integer exitCode) {
        this.output = output;
        this.exitCode = exitCode;
        this.complete = true;
    }

    @Override
    public List<String> render(int width) {
        var lines = new ArrayList<String>();

        // Command line: $ command (matching campusclaw: bold green)
        String prefix = excluded ? "$$" : "$";
        String excludedHint = excluded ? ANSI_GRAY + " (no context)" + ANSI_RESET : "";
        lines.add(" " + ANSI_BOLD + ANSI_BASH_COLOR + prefix + " " + command + ANSI_RESET + excludedHint);

        if (!complete) {
            lines.add(" " + ANSI_GRAY + "running..." + ANSI_RESET);
            return lines;
        }

        // Output lines
        if (output != null && !output.isEmpty()) {
            lines.add(""); // spacer
            int contentWidth = Math.max(1, width - 1);
            String[] outputLines = output.split("\n", -1);
            int maxLines = 50;
            int linesToShow = Math.min(outputLines.length, maxLines);
            for (int i = 0; i < linesToShow; i++) {
                String line = outputLines[i];
                if (AnsiUtils.visibleWidth(line) > contentWidth) {
                    line = AnsiUtils.sliceByColumn(line, 0, Math.max(1, contentWidth - 3)) + "...";
                }
                lines.add(" " + ANSI_GRAY + line + ANSI_RESET);
            }
            if (outputLines.length > maxLines) {
                lines.add(" " + ANSI_GRAY + "... " + (outputLines.length - maxLines) + " more lines" + ANSI_RESET);
            }
        }

        // Exit code (matching campusclaw format)
        if (exitCode != null && exitCode != 0) {
            lines.add("");
            lines.add(" " + ANSI_ERROR + "(exit " + exitCode + ")" + ANSI_RESET);
        } else if (exitCode == null) {
            lines.add("");
            lines.add(" " + ANSI_ERROR + "(timed out)" + ANSI_RESET);
        }

        return lines;
    }

    @Override
    public void invalidate() { }
}
