package com.huawei.hicampus.mate.matecampusclaw.tui.component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.UnaryOperator;

import com.huawei.hicampus.mate.matecampusclaw.tui.Component;
import com.huawei.hicampus.mate.matecampusclaw.tui.ansi.AnsiUtils;

/**
 * Text component — displays multi-line text with word wrapping, padding, and optional background.
 * <p>
 * Rendering flow:
 * <ol>
 *   <li>Normalize tabs to 3 spaces</li>
 *   <li>Wrap text with {@link AnsiUtils#wrapTextWithAnsi} at content width</li>
 *   <li>Add left/right padding to each line</li>
 *   <li>Apply background or pad with spaces to full width</li>
 *   <li>Add top/bottom padding lines</li>
 * </ol>
 */
public class Text implements Component {

    private String text;
    private int paddingX;
    private int paddingY;
    private UnaryOperator<String> bgFn;

    // Cache
    private String cachedText;
    private int cachedWidth = -1;
    private List<String> cachedLines;

    public Text() {
        this("");
    }

    public Text(String text) {
        this(text, 0, 0, null);
    }

    public Text(String text, int paddingX, int paddingY) {
        this(text, paddingX, paddingY, null);
    }

    public Text(String text, int paddingX, int paddingY, UnaryOperator<String> bgFn) {
        this.text = text != null ? text : "";
        this.paddingX = paddingX;
        this.paddingY = paddingY;
        this.bgFn = bgFn;
    }

    public void setText(String text) {
        this.text = text != null ? text : "";
        invalidate();
    }

    public String getText() {
        return text;
    }

    public void setBgFn(UnaryOperator<String> bgFn) {
        this.bgFn = bgFn;
        invalidate();
    }

    @Override
    public void invalidate() {
        cachedText = null;
        cachedWidth = -1;
        cachedLines = null;
    }

    @Override
    public List<String> render(int width) {
        // Cache hit
        if (cachedLines != null && text.equals(cachedText) && width == cachedWidth) {
            return cachedLines;
        }

        // Empty text → no output
        if (text.isEmpty() || text.isBlank()) {
            cachedText = text;
            cachedWidth = width;
            cachedLines = Collections.emptyList();
            return cachedLines;
        }

        // Normalize tabs
        String normalized = text.replace("\t", "   ");

        // Content width = total width minus left+right padding
        int contentWidth = Math.max(1, width - paddingX * 2);

        // Wrap text (preserves ANSI, does NOT pad)
        List<String> wrappedLines = AnsiUtils.wrapTextWithAnsi(normalized, contentWidth);

        // Add margins and apply background
        String leftMargin = " ".repeat(paddingX);
        List<String> contentLines = new ArrayList<>();
        for (String line : wrappedLines) {
            String withMargins = leftMargin + line;
            if (bgFn != null) {
                contentLines.add(AnsiUtils.applyBackground(withMargins, width, bgFn));
            } else {
                int visLen = AnsiUtils.visibleWidth(withMargins);
                int pad = Math.max(0, width - visLen);
                contentLines.add(withMargins + " ".repeat(pad));
            }
        }

        // Top/bottom padding
        String emptyLine = " ".repeat(width);
        List<String> paddingLines = new ArrayList<>();
        for (int i = 0; i < paddingY; i++) {
            paddingLines.add(bgFn != null ? AnsiUtils.applyBackground(emptyLine, width, bgFn) : emptyLine);
        }

        List<String> result = new ArrayList<>();
        result.addAll(paddingLines);
        result.addAll(contentLines);
        result.addAll(paddingLines);

        cachedText = text;
        cachedWidth = width;
        cachedLines = result;
        return result;
    }
}
