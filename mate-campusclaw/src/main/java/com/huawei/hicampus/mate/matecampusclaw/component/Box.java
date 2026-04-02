package com.huawei.hicampus.mate.matecampusclaw.tui.component;

import java.util.ArrayList;
import java.util.List;
import java.util.function.UnaryOperator;

import com.huawei.hicampus.mate.matecampusclaw.tui.Component;
import com.huawei.hicampus.mate.matecampusclaw.tui.ansi.AnsiUtils;

/**
 * Box component — wraps a child component with optional border, padding, and background color.
 * <p>
 * Layout (when border is present):
 * <pre>
 * ┌──────────────────┐  ← border top
 * │                  │  ← paddingY rows
 * │  [child content] │  ← content with paddingX
 * │                  │  ← paddingY rows
 * └──────────────────┘  ← border bottom
 * </pre>
 * When no border, only padding and background are applied.
 */
public class Box implements Component {

    private final Component child;
    private BorderStyle borderStyle;
    private int padding;
    private int paddingX;
    private int paddingY;
    private UnaryOperator<String> bgFn;
    private UnaryOperator<String> borderColorFn;

    public Box(Component child) {
        this(child, null, 0, 0, 0, null, null);
    }

    public Box(Component child, BorderStyle borderStyle) {
        this(child, borderStyle, 0, 0, 0, null, null);
    }

    public Box(Component child, BorderStyle borderStyle, int padding) {
        this(child, borderStyle, padding, -1, -1, null, null);
    }

    /**
     * Creates a Box with full configuration.
     *
     * @param child         the child component to render inside the box
     * @param borderStyle   border style (null for no border)
     * @param padding       uniform padding (used if paddingX/paddingY are -1)
     * @param paddingX      horizontal padding (-1 to use padding)
     * @param paddingY      vertical padding (-1 to use padding)
     * @param bgFn          background color function (null for none)
     * @param borderColorFn function to apply color to border characters (null for none)
     */
    public Box(Component child, BorderStyle borderStyle, int padding,
               int paddingX, int paddingY,
               UnaryOperator<String> bgFn, UnaryOperator<String> borderColorFn) {
        this.child = child;
        this.borderStyle = borderStyle;
        this.padding = padding;
        this.paddingX = paddingX >= 0 ? paddingX : padding;
        this.paddingY = paddingY >= 0 ? paddingY : padding;
        this.bgFn = bgFn;
        this.borderColorFn = borderColorFn;
    }

    public void setBorderStyle(BorderStyle borderStyle) {
        this.borderStyle = borderStyle;
    }

    public void setBgFn(UnaryOperator<String> bgFn) {
        this.bgFn = bgFn;
    }

    public void setBorderColorFn(UnaryOperator<String> borderColorFn) {
        this.borderColorFn = borderColorFn;
    }

    @Override
    public void invalidate() {
        child.invalidate();
    }

    @Override
    public List<String> render(int width) {
        boolean hasBorder = borderStyle != null;
        // Border takes 1 column on each side
        int borderWidth = hasBorder ? 1 : 0;
        int contentWidth = Math.max(1, width - (borderWidth * 2) - (paddingX * 2));

        // Render child at content width
        List<String> childLines = child.render(contentWidth);

        // Inner width is everything between the border characters
        int innerWidth = width - (borderWidth * 2);

        List<String> result = new ArrayList<>();

        // --- Top border ---
        if (hasBorder) {
            result.add(buildBorderLine(
                    borderStyle.topLeft, borderStyle.horizontal, borderStyle.topRight, width));
        }

        // --- Top padding ---
        for (int i = 0; i < paddingY; i++) {
            result.add(buildPaddingLine(innerWidth, hasBorder));
        }

        // --- Content lines ---
        String leftPad = " ".repeat(paddingX);
        for (String line : childLines) {
            String padded = leftPad + line;
            // Pad to innerWidth
            int visLen = AnsiUtils.visibleWidth(padded);
            int rightPad = Math.max(0, innerWidth - visLen);
            String inner = padded + " ".repeat(rightPad);

            if (bgFn != null) {
                inner = bgFn.apply(inner);
            }

            if (hasBorder) {
                String v = colorBorder(String.valueOf(borderStyle.vertical));
                result.add(v + inner + v);
            } else {
                result.add(inner);
            }
        }

        // --- Bottom padding ---
        for (int i = 0; i < paddingY; i++) {
            result.add(buildPaddingLine(innerWidth, hasBorder));
        }

        // --- Bottom border ---
        if (hasBorder) {
            result.add(buildBorderLine(
                    borderStyle.bottomLeft, borderStyle.horizontal, borderStyle.bottomRight, width));
        }

        return result;
    }

    /**
     * Builds a horizontal border line: corner + repeated horizontal + corner.
     */
    private String buildBorderLine(char left, char horizontal, char right, int totalWidth) {
        int innerCount = Math.max(0, totalWidth - 2);
        String line = String.valueOf(left)
                + String.valueOf(horizontal).repeat(innerCount)
                + String.valueOf(right);
        return colorBorder(line);
    }

    /**
     * Builds a padding line (empty space between border and content).
     */
    private String buildPaddingLine(int innerWidth, boolean hasBorder) {
        String inner = " ".repeat(innerWidth);
        if (bgFn != null) {
            inner = bgFn.apply(inner);
        }
        if (hasBorder) {
            String v = colorBorder(String.valueOf(borderStyle.vertical));
            return v + inner + v;
        }
        return inner;
    }

    private String colorBorder(String text) {
        if (borderColorFn != null) {
            return borderColorFn.apply(text);
        }
        return text;
    }
}
