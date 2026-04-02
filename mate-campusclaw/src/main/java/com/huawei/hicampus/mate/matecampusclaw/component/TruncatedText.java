package com.huawei.hicampus.mate.matecampusclaw.tui.component;

import java.util.Collections;
import java.util.List;
import java.util.function.UnaryOperator;

import com.huawei.hicampus.mate.matecampusclaw.tui.Component;
import com.huawei.hicampus.mate.matecampusclaw.tui.ansi.AnsiUtils;

/**
 * TruncatedText component — displays single-line text that is truncated with an ellipsis
 * when it exceeds the available width. Unlike {@link Text}, this component never wraps;
 * it always produces exactly one line (or zero lines if the text is empty).
 */
public class TruncatedText implements Component {

    private static final String ELLIPSIS = "\u2026"; // Unicode ellipsis character (width 1)

    private String text;
    private UnaryOperator<String> styleFn;

    // Cache
    private String cachedText;
    private int cachedWidth = -1;
    private List<String> cachedLines;

    public TruncatedText() {
        this("");
    }

    public TruncatedText(String text) {
        this(text, null);
    }

    /**
     * Creates a TruncatedText with optional styling.
     *
     * @param text    the text to display
     * @param styleFn optional function applied to the final visible string (e.g. ANSI color)
     */
    public TruncatedText(String text, UnaryOperator<String> styleFn) {
        this.text = text != null ? text : "";
        this.styleFn = styleFn;
    }

    public void setText(String text) {
        this.text = text != null ? text : "";
        invalidate();
    }

    public String getText() {
        return text;
    }

    public void setStyleFn(UnaryOperator<String> styleFn) {
        this.styleFn = styleFn;
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

        if (text.isEmpty()) {
            cachedText = text;
            cachedWidth = width;
            cachedLines = Collections.emptyList();
            return cachedLines;
        }

        int visWidth = AnsiUtils.visibleWidth(text);
        String display;

        if (visWidth <= width) {
            // Text fits — pad to full width
            display = text + " ".repeat(Math.max(0, width - visWidth));
        } else if (width <= 1) {
            // Too narrow for ellipsis — just show what fits
            display = AnsiUtils.sliceByColumn(text, 0, width);
        } else {
            // Truncate and append ellipsis
            String truncated = AnsiUtils.sliceByColumn(text, 0, width - 1);
            display = truncated + ELLIPSIS;
        }

        if (styleFn != null) {
            display = styleFn.apply(display);
        }

        cachedText = text;
        cachedWidth = width;
        cachedLines = List.of(display);
        return cachedLines;
    }
}
