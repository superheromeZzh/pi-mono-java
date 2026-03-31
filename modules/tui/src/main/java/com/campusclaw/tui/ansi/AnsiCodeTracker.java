package com.campusclaw.tui.ansi;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Tracks active ANSI SGR (Select Graphic Rendition) codes to preserve styling across line breaks.
 * Supports standard colors, 256-color, and RGB color modes.
 */
class AnsiCodeTracker {

    private static final Pattern SGR_PARAMS = Pattern.compile("\033\\[([\\d;]*)m");

    private boolean bold;
    private boolean dim;
    private boolean italic;
    private boolean underline;
    private boolean blink;
    private boolean inverse;
    private boolean hidden;
    private boolean strikethrough;
    private String fgColor; // e.g. "31" or "38;5;240" or "38;2;128;0;255"
    private String bgColor; // e.g. "41" or "48;5;240" or "48;2;128;0;255"

    void process(String ansiCode) {
        if (!ansiCode.endsWith("m")) {
            return;
        }

        Matcher matcher = SGR_PARAMS.matcher(ansiCode);
        if (!matcher.find()) {
            return;
        }

        String params = matcher.group(1);
        if (params.isEmpty() || params.equals("0")) {
            reset();
            return;
        }

        String[] parts = params.split(";");
        int i = 0;
        while (i < parts.length) {
            int code = Integer.parseInt(parts[i]);

            // Handle 256-color and RGB codes
            if (code == 38 || code == 48) {
                if (i + 2 < parts.length && parts[i + 1].equals("5")) {
                    String colorCode = parts[i] + ";" + parts[i + 1] + ";" + parts[i + 2];
                    if (code == 38) {
                        fgColor = colorCode;
                    } else {
                        bgColor = colorCode;
                    }
                    i += 3;
                    continue;
                } else if (i + 4 < parts.length && parts[i + 1].equals("2")) {
                    String colorCode = parts[i] + ";" + parts[i + 1] + ";" + parts[i + 2]
                            + ";" + parts[i + 3] + ";" + parts[i + 4];
                    if (code == 38) {
                        fgColor = colorCode;
                    } else {
                        bgColor = colorCode;
                    }
                    i += 5;
                    continue;
                }
            }

            switch (code) {
                case 0 -> reset();
                case 1 -> bold = true;
                case 2 -> dim = true;
                case 3 -> italic = true;
                case 4 -> underline = true;
                case 5 -> blink = true;
                case 7 -> inverse = true;
                case 8 -> hidden = true;
                case 9 -> strikethrough = true;
                case 21 -> bold = false;
                case 22 -> { bold = false; dim = false; }
                case 23 -> italic = false;
                case 24 -> underline = false;
                case 25 -> blink = false;
                case 27 -> inverse = false;
                case 28 -> hidden = false;
                case 29 -> strikethrough = false;
                case 39 -> fgColor = null;
                case 49 -> bgColor = null;
                default -> {
                    if ((code >= 30 && code <= 37) || (code >= 90 && code <= 97)) {
                        fgColor = String.valueOf(code);
                    } else if ((code >= 40 && code <= 47) || (code >= 100 && code <= 107)) {
                        bgColor = String.valueOf(code);
                    }
                }
            }
            i++;
        }
    }

    private void reset() {
        bold = false;
        dim = false;
        italic = false;
        underline = false;
        blink = false;
        inverse = false;
        hidden = false;
        strikethrough = false;
        fgColor = null;
        bgColor = null;
    }

    void clear() {
        reset();
    }

    String getActiveCodes() {
        List<String> codes = new ArrayList<>();
        if (bold) codes.add("1");
        if (dim) codes.add("2");
        if (italic) codes.add("3");
        if (underline) codes.add("4");
        if (blink) codes.add("5");
        if (inverse) codes.add("7");
        if (hidden) codes.add("8");
        if (strikethrough) codes.add("9");
        if (fgColor != null) codes.add(fgColor);
        if (bgColor != null) codes.add(bgColor);

        if (codes.isEmpty()) return "";
        return "\033[" + String.join(";", codes) + "m";
    }

    boolean hasActiveCodes() {
        return bold || dim || italic || underline || blink
                || inverse || hidden || strikethrough
                || fgColor != null || bgColor != null;
    }

    String getLineEndReset() {
        if (underline) {
            return "\033[24m";
        }
        return "";
    }
}
