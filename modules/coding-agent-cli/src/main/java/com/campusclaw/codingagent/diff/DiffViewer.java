/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.campusclaw.codingagent.diff;

import java.util.ArrayList;
import java.util.List;

/**
 * Colorized side-by-side diff visualization for terminal display.
 *
 * @version [br_eCampusCore 25.1.0_Next, 2026/05/06]
 * @since [br_eCampusCore 25.1.0_Next]
 */
public class DiffViewer {

    /**
     * ANSI color codes.
     */
    private static final String RED = "\033[31m";

    private static final String GREEN = "\033[32m";
    private static final String YELLOW = "\033[33m";
    private static final String CYAN = "\033[36m";
    private static final String DIM = "\033[2m";
    private static final String RESET = "\033[0m";
    private static final String BG_RED = "\033[41m";
    private static final String BG_GREEN = "\033[42m";
    private static final String INVERSE = "\033[7m";

    @SuppressWarnings("checkstyle:top_class_comment")
    public enum LineType {
        SAME,
        ADDED,
        REMOVED,
        MODIFIED
    }

    @SuppressWarnings("checkstyle:top_class_comment")
    public record DiffLine(LineType type, int oldLineNum, int newLineNum, String oldText, String newText) {}

    /**
     * Compute a simple line-based diff between two texts.
     * Tabs are replaced with spaces for consistent rendering (matching campusclaw).
     *
     * @param oldText the oldText
     * @param newText the newText
     * @return the result
     */
    public static List<DiffLine> diff(String oldText, String newText) {
        String[] oldLines = replaceTabs(oldText).split("\n", -1);
        String[] newLines = replaceTabs(newText).split("\n", -1);
        return computeLcs(oldLines, newLines);
    }

    /**
     * Replace tabs with spaces for consistent rendering (matching campusclaw).
     *
     * @param text the text
     * @return the result
     */
    private static String replaceTabs(String text) {
        return text != null ? text.replace("\t", "   ") : "";
    }

    /**
     * Format diff as colored unified diff string.
     *
     * @param lines the lines
     * @param fileName the fileName
     * @return the result
     */
    public static String formatUnified(List<DiffLine> lines, String fileName) {
        var sb = new StringBuilder();
        sb.append(CYAN).append("--- a/").append(fileName).append(RESET).append('\n');
        sb.append(CYAN).append("+++ b/").append(fileName).append(RESET).append('\n');

        for (DiffLine line : lines) {
            switch (line.type) {
                case SAME ->
                    sb.append(DIM)
                            .append("  ")
                            .append(line.oldText)
                            .append(RESET)
                            .append('\n');
                case REMOVED ->
                    sb.append(RED)
                            .append("- ")
                            .append(line.oldText)
                            .append(RESET)
                            .append('\n');
                case ADDED ->
                    sb.append(GREEN)
                            .append("+ ")
                            .append(line.newText)
                            .append(RESET)
                            .append('\n');
                case MODIFIED -> {
                    // Intra-line word diff with inverse highlighting (matching campusclaw)
                    sb.append(RED)
                            .append("- ")
                            .append(highlightWordDiff(line.oldText, line.newText, RED))
                            .append(RESET)
                            .append('\n');
                    sb.append(GREEN)
                            .append("+ ")
                            .append(highlightWordDiff(line.newText, line.oldText, GREEN))
                            .append(RESET)
                            .append('\n');
                }
            }
        }
        return sb.toString();
    }

    /**
     * Format diff as side-by-side view.
     *
     * @param lines the lines
     * @param colWidth the colWidth
     * @return the result
     */
    public static String formatSideBySide(List<DiffLine> lines, int colWidth) {
        var sb = new StringBuilder();
        String separator = " │ ";
        String colFmt = "%-" + colWidth + "s";
        sb.append(CYAN)
                .append(String.format(colFmt + separator + colFmt, "Old", "New"))
                .append(RESET)
                .append('\n');
        sb.append("─".repeat(colWidth))
                .append("─┼─")
                .append("─".repeat(colWidth))
                .append('\n');
        for (DiffLine line : lines) {
            String left = truncate(line.oldText != null ? line.oldText : "", colWidth);
            String right = truncate(line.newText != null ? line.newText : "", colWidth);
            appendDiffRow(sb, line.type, left, right, colFmt, separator);
        }
        return sb.toString();
    }

    private static void appendDiffRow(
            StringBuilder sb, LineType type, String left, String right, String colFmt, String separator) {
        switch (type) {
            case SAME ->
                sb.append(DIM)
                        .append(String.format(colFmt, left))
                        .append(separator)
                        .append(String.format(colFmt, right))
                        .append(RESET)
                        .append('\n');
            case REMOVED ->
                sb.append(RED)
                        .append(String.format(colFmt, left))
                        .append(RESET)
                        .append(separator)
                        .append(String.format(colFmt, ""))
                        .append('\n');
            case ADDED ->
                sb.append(String.format(colFmt, ""))
                        .append(separator)
                        .append(GREEN)
                        .append(String.format(colFmt, right))
                        .append(RESET)
                        .append('\n');
            case MODIFIED ->
                sb.append(RED)
                        .append(String.format(colFmt, left))
                        .append(RESET)
                        .append(separator)
                        .append(GREEN)
                        .append(String.format(colFmt, right))
                        .append(RESET)
                        .append('\n');
        }
    }

    /**
     * Generate a summary of changes.
     *
     * @param lines the lines
     * @return the result
     */
    public static DiffSummary summarize(List<DiffLine> lines) {
        int added = 0;
        int removed = 0;
        int modified = 0;
        int unchanged = 0;
        for (DiffLine line : lines) {
            switch (line.type) {
                case ADDED -> added++;
                case REMOVED -> removed++;
                case MODIFIED -> modified++;
                case SAME -> unchanged++;
            }
        }
        return new DiffSummary(added, removed, modified, unchanged);
    }

    @SuppressWarnings("checkstyle:top_class_comment")
    public record DiffSummary(int added, int removed, int modified, int unchanged) {
        public String format() {
            return GREEN + "+" + added + RESET + " "
                    + RED + "-" + removed + RESET + " "
                    + YELLOW + "~" + modified + RESET;
        }
    }

    private static String truncate(String s, int maxLen) {
        if (s.length() <= maxLen) {
            return s;
        }
        return s.substring(0, maxLen - 1) + "…";
    }

    /**
     * Highlights changed words within a line using inverse colors.
     * Words present in 'line' but not in 'other' get INVERSE highlighting.
     * Matching campusclaw's diffWords() intra-line highlighting behavior.
     *
     * @param line the line
     * @param other the other
     * @param baseColor the baseColor
     * @return the result
     */
    static String highlightWordDiff(String line, String other, String baseColor) {
        if (line == null || line.isEmpty()) {
            return "";
        }
        if (other == null || other.isEmpty()) {
            return line;
        }

        // Split into words (preserving whitespace as separate tokens)
        var lineTokens = tokenize(line);
        var otherTokens = tokenize(other);

        // Find common tokens via LCS
        var common = wordLcs(lineTokens, otherTokens);
        var commonSet = new java.util.HashSet<Integer>(); // indices in lineTokens that are common
        int ci = 0;
        for (int li = 0; li < lineTokens.size() && ci < common.size(); li++) {
            if (lineTokens.get(li).equals(common.get(ci))) {
                commonSet.add(li);
                ci++;
            }
        }

        // Build highlighted output
        var sb = new StringBuilder();
        boolean inHighlight = false;
        for (int li = 0; li < lineTokens.size(); li++) {
            boolean isChanged = !commonSet.contains(li);
            if (isChanged && !inHighlight) {
                sb.append(INVERSE);
                inHighlight = true;
            } else if (!isChanged && inHighlight) {
                sb.append(RESET).append(baseColor);
                inHighlight = false;
            }
            sb.append(lineTokens.get(li));
        }
        if (inHighlight) {
            sb.append(RESET).append(baseColor);
        }
        return sb.toString();
    }

    /**
     * Tokenize a string into words and whitespace tokens.
     *
     * @param text the text
     * @return the result
     */
    private static List<String> tokenize(String text) {
        var tokens = new ArrayList<String>();
        var current = new StringBuilder();
        boolean inWord = false;
        for (int i = 0; i < text.length(); i++) {
            char ch = text.charAt(i);
            boolean isWs = Character.isWhitespace(ch);
            if (inWord && isWs) {
                tokens.add(current.toString());
                current.setLength(0);
                inWord = false;
            } else if (!inWord && !isWs) {
                if (!current.isEmpty()) {
                    tokens.add(current.toString());
                }
                current.setLength(0);
                inWord = true;
            }
            current.append(ch);
        }
        if (!current.isEmpty()) {
            tokens.add(current.toString());
        }
        return tokens;
    }

    /**
     * LCS on word tokens.
     *
     * @param a the a
     * @param b the b
     * @return the result
     */
    private static List<String> wordLcs(List<String> a, List<String> b) {
        int m = a.size();
        int n = b.size();
        int[][] dp = new int[m + 1][n + 1];
        for (int i = m - 1; i >= 0; i--) {
            for (int j = n - 1; j >= 0; j--) {
                if (a.get(i).equals(b.get(j))) {
                    dp[i][j] = dp[i + 1][j + 1] + 1;
                } else {
                    dp[i][j] = Math.max(dp[i + 1][j], dp[i][j + 1]);
                }
            }
        }
        var result = new ArrayList<String>();
        int i = 0;
        int j = 0;
        while (i < m && j < n) {
            if (a.get(i).equals(b.get(j))) {
                result.add(a.get(i));
                i++;
                j++;
            } else if (dp[i + 1][j] >= dp[i][j + 1]) {
                i++;
            } else {
                j++;
            }
        }
        return result;
    }

    /**
     * Simple LCS-based diff algorithm.
     *
     * @param oldLines the oldLines
     * @param newLines the newLines
     * @return the result
     */
    private static List<DiffLine> computeLcs(String[] oldLines, String[] newLines) {
        int m = oldLines.length;
        int n = newLines.length;
        int[][] dp = new int[m + 1][n + 1];
        for (int i = m - 1; i >= 0; i--) {
            for (int j = n - 1; j >= 0; j--) {
                if (oldLines[i].equals(newLines[j])) {
                    dp[i][j] = dp[i + 1][j + 1] + 1;
                } else {
                    dp[i][j] = Math.max(dp[i + 1][j], dp[i][j + 1]);
                }
            }
        }

        List<DiffLine> result = new ArrayList<>();
        int i = 0;
        int j = 0;
        while (i < m || j < n) {
            if (i < m && j < n && oldLines[i].equals(newLines[j])) {
                result.add(new DiffLine(LineType.SAME, i + 1, j + 1, oldLines[i], newLines[j]));
                i++;
                j++;
            } else if (j < n && (i >= m || dp[i][j + 1] >= dp[i + 1][j])) {
                result.add(new DiffLine(LineType.ADDED, -1, j + 1, null, newLines[j]));
                j++;
            } else {
                result.add(new DiffLine(LineType.REMOVED, i + 1, -1, oldLines[i], null));
                i++;
            }
        }
        return result;
    }
}
