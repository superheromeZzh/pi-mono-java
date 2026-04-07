package com.campusclaw.codingagent.mode.tui;

import java.util.ArrayList;
import java.util.List;

import com.campusclaw.tui.Component;
import com.campusclaw.tui.ansi.AnsiUtils;
import com.campusclaw.tui.component.MarkdownComponent;

/**
 * Renders an assistant message with optional thinking block and markdown-formatted text.
 * Updated incrementally during streaming.
 */
public class AssistantMessageComponent implements Component {

    // Thinking style: italic + gray #808080 (matching campusclaw thinkingText color)
    private static final String ANSI_ITALIC = "\033[3m";
    private static final String ANSI_THINKING_COLOR = "\033[38;2;128;128;128m";
    // Spinner colors matching campusclaw: accent for spinner, muted for text
    private static final String ANSI_ACCENT = "\033[38;2;138;190;183m";
    private static final String ANSI_MUTED = "\033[38;2;128;128;128m";
    private static final String ANSI_RESET = "\033[0m";
    private static final String[] SPINNER_FRAMES = {"⠋", "⠙", "⠹", "⠸", "⠼", "⠴", "⠦", "⠧", "⠇", "⠏"};

    private int spinnerFrame = 0;

    private final StringBuilder thinkingContent = new StringBuilder();
    private final StringBuilder textContent = new StringBuilder();
    private final MarkdownComponent markdownComponent = new MarkdownComponent();

    private boolean complete = false;
    private boolean hideThinking = false;

    // Cache — includes complete state in key
    private String cachedThinking;
    private String cachedText;
    private boolean cachedComplete;
    private int cachedWidth = -1;
    private List<String> cachedLines;

    public void appendThinking(String delta) {
        thinkingContent.append(delta);
        invalidate();
    }

    public void appendText(String delta) {
        textContent.append(delta);
        invalidate();
    }

    public void setComplete(boolean complete) {
        this.complete = complete;
        invalidate();
    }

    public boolean hasContent() {
        return !thinkingContent.isEmpty() || !textContent.isEmpty();
    }

    public String getTextContent() {
        return textContent.toString();
    }

    public void setHideThinking(boolean hide) {
        this.hideThinking = hide;
        invalidate();
    }

    public boolean isHideThinking() {
        return hideThinking;
    }

    @Override
    public void invalidate() {
        cachedLines = null;
        cachedWidth = -1;
    }

    @Override
    public List<String> render(int width) {
        String thinking = thinkingContent.toString();
        String text = textContent.toString();

        // Don't cache when showing animated spinner
        boolean showingSpinner = !complete && text.isEmpty() && thinking.isEmpty();
        if (!showingSpinner && cachedLines != null && cachedWidth == width
                && cachedComplete == complete
                && thinking.equals(cachedThinking)
                && text.equals(cachedText)) {
            return cachedLines;
        }

        var lines = new ArrayList<String>();
        lines.add(""); // spacer before message

        // Thinking block — italic + gray color (matching campusclaw thinkingText)
        if (!thinking.isBlank() && !hideThinking) {
            int contentWidth = Math.max(1, width - 2);
            List<String> thinkingLines = AnsiUtils.wrapTextWithAnsi(thinking.strip(), contentWidth);
            for (String line : thinkingLines) {
                lines.add(ANSI_ITALIC + ANSI_THINKING_COLOR + " " + line + ANSI_RESET);
            }
            if (!text.isBlank()) {
                lines.add(""); // spacer between thinking and text
            }
        }

        // Text content — rendered as markdown with 1-space left margin (matching campusclaw paddingX=1)
        if (!text.isBlank()) {
            markdownComponent.setContent(text);
            var mdLines = markdownComponent.render(Math.max(1, width - 1));
            for (String line : mdLines) {
                lines.add(" " + line);
            }
        }

        // Animated spinner — matching campusclaw "⠹ Working..." style
        if (!complete && text.isEmpty() && thinking.isEmpty()) {
            String frame = SPINNER_FRAMES[spinnerFrame % SPINNER_FRAMES.length];
            spinnerFrame++;
            lines.add(" " + ANSI_ACCENT + frame + ANSI_RESET + " " + ANSI_MUTED + "Working... (escape to interrupt)" + ANSI_RESET);
        }

        cachedThinking = thinking;
        cachedText = text;
        cachedComplete = complete;
        cachedWidth = width;
        cachedLines = lines;
        return lines;
    }
}
