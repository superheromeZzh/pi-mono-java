package com.mariozechner.pi.codingagent.mode.tui;

import com.mariozechner.pi.tui.Component;
import com.mariozechner.pi.tui.ansi.AnsiUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * Renders a user message with background color (#343541) matching pi-mono TS.
 * Full-width background with padding above and below.
 */
public class UserMessageComponent implements Component {

    // Dark gray background matching TS theme "userMessageBg": "#343541"
    private static final String BG_START = "\033[48;2;52;53;65m";
    private static final String ANSI_RESET = "\033[0m";

    private final String text;

    // Cache
    private int cachedWidth = -1;
    private List<String> cachedLines;

    public UserMessageComponent(String text) {
        this.text = text != null ? text : "";
    }

    @Override
    public List<String> render(int width) {
        if (cachedLines != null && cachedWidth == width) return cachedLines;

        var lines = new ArrayList<String>();
        lines.add(""); // spacer before

        // Wrap text with 1-char padding on each side (matching pi-mono paddingX=1)
        int contentWidth = Math.max(1, width - 2);
        List<String> wrapped = AnsiUtils.wrapTextWithAnsi(text, contentWidth);

        // Top padding line with background
        lines.add(bgLine("", width));

        // Content lines with background (no bold, matching pi-mono)
        for (String line : wrapped) {
            String content = " " + line;
            lines.add(bgLine(content, width));
        }

        // Bottom padding line with background
        lines.add(bgLine("", width));

        lines.add(""); // spacer after

        cachedWidth = width;
        cachedLines = lines;
        return lines;
    }

    /** Wraps a content line with background color, padding to full width. */
    private static String bgLine(String content, int width) {
        int visLen = AnsiUtils.visibleWidth(content);
        int pad = Math.max(0, width - visLen);
        return BG_START + content + " ".repeat(pad) + ANSI_RESET;
    }

    @Override
    public void invalidate() {
        cachedWidth = -1;
        cachedLines = null;
    }
}
