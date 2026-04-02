package com.huawei.hicampus.mate.matecampusclaw.codingagent.mode.tui;

import java.util.ArrayList;
import java.util.List;

import com.huawei.hicampus.mate.matecampusclaw.tui.Component;
import com.huawei.hicampus.mate.matecampusclaw.tui.ansi.AnsiUtils;

/**
 * Renders slash command output with dim styling and vertical spacing,
 * visually distinct from assistant messages.
 */
public class CommandOutputComponent implements Component {

    // Use dim gray matching campusclaw footer styling
    private static final String ANSI_DIM = "\033[38;2;102;102;102m";
    private static final String ANSI_RESET = "\033[0m";

    private final String text;

    // Cache
    private int cachedWidth = -1;
    private List<String> cachedLines;

    public CommandOutputComponent(String text) {
        this.text = text != null ? text : "";
    }

    @Override
    public List<String> render(int width) {
        if (cachedLines != null && cachedWidth == width) return cachedLines;

        var lines = new ArrayList<String>();
        lines.add(""); // spacer before

        int contentWidth = Math.max(1, width - 2);
        List<String> wrapped = AnsiUtils.wrapTextWithAnsi(text, contentWidth);
        for (String line : wrapped) {
            lines.add(ANSI_DIM + " " + line + ANSI_RESET);
        }

        lines.add(""); // spacer after

        cachedWidth = width;
        cachedLines = lines;
        return lines;
    }

    @Override
    public void invalidate() {
        cachedWidth = -1;
        cachedLines = null;
    }
}
