/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.campusclaw.tui.ansi;

import java.text.BreakIterator;
import java.util.ArrayList;
import java.util.List;
import java.util.function.UnaryOperator;

/**
 * ANSI-aware text utility functions for terminal rendering.
 * Handles ANSI escape sequences, East Asian wide characters, and grapheme clusters.
 *
 * @version [br_eCampusCore 25.1.0_Next, 2026/05/06]
 * @since [br_eCampusCore 25.1.0_Next]
 */
public final class AnsiUtils {

    private AnsiUtils() {}

    // -------------------------------------------------------------------
    // ANSI escape code extraction
    // -------------------------------------------------------------------

    /**
     * Extracts an ANSI escape sequence starting at the given position.
     * Supports CSI (ESC[), OSC (ESC]), and APC (ESC_) sequences.
     *
     * @param str string to scan
     * @param pos zero-based start offset
     * @return the escape code and its length, or null if no escape sequence starts at pos
     */
    static AnsiCode extractAnsiCode(String str, int pos) {
        if (pos + 1 >= str.length() || str.charAt(pos) != '\033') {
            return null;
        }
        char next = str.charAt(pos + 1);
        return switch (next) {
            case '[' -> extractCsi(str, pos);
            case ']', '_' -> extractStringTerminated(str, pos);
            default -> null;
        };
    }

    // CSI sequence: ESC [ ... <terminator>.
    //   Final byte: 0x40-0x7E ('@'..'~').
    //   Intermediate bytes: 0x20-0x2F.
    //   Parameter bytes: 0x30-0x3F (digits, semicolons, '?', etc.).
    private static AnsiCode extractCsi(String str, int pos) {
        int j = pos + 2;
        while (j < str.length()) {
            char c = str.charAt(j);
            if (c >= 0x40 && c <= 0x7E) {
                return new AnsiCode(str.substring(pos, j + 1), j + 1 - pos);
            }
            if (c < 0x20 || c > 0x3F) {
                return null; // Malformed sequence — stop scanning.
            }
            j++;
        }
        return null;
    }

    // OSC / APC sequence: ESC ] ... BEL | ESC ] ... ST (ESC \).
    // (Same terminator pair applies to APC, which starts with ESC _.)
    private static AnsiCode extractStringTerminated(String str, int pos) {
        int j = pos + 2;
        while (j < str.length()) {
            if (str.charAt(j) == '\007') {
                return new AnsiCode(str.substring(pos, j + 1), j + 1 - pos);
            }
            if (str.charAt(j) == '\033' && j + 1 < str.length() && str.charAt(j + 1) == '\\') {
                return new AnsiCode(str.substring(pos, j + 2), j + 2 - pos);
            }
            j++;
        }
        return null;
    }

    record AnsiCode(String code, int length) {}

    // -------------------------------------------------------------------
    // Character width calculation
    // -------------------------------------------------------------------

    /**
     * Returns the display width of a single code point in a terminal.
     * East Asian fullwidth/wide characters occupy 2 columns; most others occupy 1.
     * Control characters and zero-width characters return 0.
     *
     * @param cp Unicode code point
     * @return display width in terminal columns (0, 1, or 2)
     */
    static int codePointWidth(int cp) {
        // Control characters
        if (cp < 0x20 || (cp >= 0x7F && cp < 0xA0)) {
            return 0;
        }

        // Zero-width combining marks and format characters
        if (Character.getType(cp) == Character.NON_SPACING_MARK
                || Character.getType(cp) == Character.ENCLOSING_MARK
                || Character.getType(cp) == Character.FORMAT) {
            return 0;
        }

        // East Asian Fullwidth and Wide characters
        if (isEastAsianWide(cp)) {
            return 2;
        }
        return 1;
    }

    /**
     * Inclusive Unicode code point ranges that render as 2 columns in a terminal.
     * Layout: flat {@code int[]} of pairs ({@code lo, hi, lo, hi, ...}) — a flat
     * array reads faster than a record array because there's no indirection.
     * Order is by lowest {@code lo} ascending so a single sentinel break stops
     * the loop early once {@code cp < lo}.
     */
    private static final int[] EAST_ASIAN_WIDE_RANGES = {
        // Sorted by low bound ascending.
        0x1F000, 0x1FBFF, // Emoji typically rendered as wide (overlaps Regional Indicators 0x1F1E6-0x1F1FF)
        0x20000, 0x2A6DF, // CJK Unified Ideographs Extension B
        0x2A700, 0x323AF, // CJK Unified Ideographs Extension C-H
        0x2E80, 0x2EFF, // CJK Radicals Supplement
        0x2F00, 0x2FDF, // Kangxi Radicals
        0x2F800, 0x2FA1F, // CJK Compatibility Ideographs Supplement
        0x3000, 0x303F, // CJK Symbols and Punctuation
        0x3040, 0x309F, // Hiragana
        0x30A0, 0x30FF, // Katakana
        0x3100, 0x312F, // Bopomofo
        0x3130, 0x318F, // Hangul Compatibility Jamo
        0x3190, 0x319F, // Kanbun
        0x31A0, 0x31BF, // Bopomofo Extended
        0x31C0, 0x31EF, // CJK Strokes
        0x31F0, 0x31FF, // Katakana Phonetic Extensions
        0x3200, 0x32FF, // Enclosed CJK Letters and Months
        0x3300, 0x33FF, // CJK Compatibility
        0x3400, 0x4DBF, // CJK Unified Ideographs Extension A
        0x4E00, 0x9FFF, // CJK Unified Ideographs
        0xAC00, 0xD7AF, // Hangul Syllables
        0xD7B0, 0xD7FF, // Hangul Jamo Extended-B
        0xF900, 0xFAFF, // CJK Compatibility Ideographs
        0xFF01, 0xFF60, // Fullwidth Forms (Fullwidth ASCII, Fullwidth punctuation)
        0xFFE0, 0xFFE6, // Fullwidth signs
    };

    /**
     * Checks if a code point is East Asian Fullwidth or Wide.
     *
     * @param cp Unicode code point
     * @return {@code true} for East Asian fullwidth/wide code points
     */
    private static boolean isEastAsianWide(int cp) {
        for (int i = 0; i < EAST_ASIAN_WIDE_RANGES.length; i += 2) {
            int lo = EAST_ASIAN_WIDE_RANGES[i];
            int hi = EAST_ASIAN_WIDE_RANGES[i + 1];
            if (cp < lo) {
                continue;
            }
            if (cp <= hi) {
                return true;
            }
        }
        return false;
    }

    /**
     * Returns the display width of a grapheme cluster (a string segment from BreakIterator).
     *
     * @param grapheme grapheme cluster text
     * @return display width in terminal columns
     */
    private static int graphemeWidth(String grapheme) {
        if (grapheme.isEmpty()) {
            return 0;
        }
        int width = 0;
        int i = 0;
        while (i < grapheme.length()) {
            int cp = grapheme.codePointAt(i);
            int w = codePointWidth(cp);
            width = Math.max(width, w); // For a grapheme cluster, use the widest component
            i += Character.charCount(cp);
        }
        return width;
    }

    // -------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------

    /**
     * Calculates the visible terminal width of a string, ignoring ANSI escape codes.
     * Correctly handles East Asian fullwidth characters (width 2) and tabs (width 3).
     *
     * @param text the text to measure
     * @return the visible column width
     */
    public static int visibleWidth(String text) {
        if (text == null || text.isEmpty()) {
            return 0;
        }

        // Fast path: pure ASCII printable (no ANSI, no wide chars)
        if (isPureAscii(text)) {
            return text.length();
        }

        // Strip ANSI codes and count visible width
        int width = 0;
        int i = 0;
        while (i < text.length()) {
            // Skip ANSI escape sequences
            AnsiCode ansi = extractAnsiCode(text, i);
            if (ansi != null) {
                i += ansi.length();
                continue;
            }

            char c = text.charAt(i);

            // Tab = 3 spaces
            if (c == '\t') {
                width += 3;
                i++;
                continue;
            }

            // Segment into grapheme clusters for the non-ANSI portion
            int end = i;
            while (end < text.length() && text.charAt(end) != '\t' && extractAnsiCode(text, end) == null) {
                end++;
            }

            // Use BreakIterator for grapheme segmentation
            String portion = text.substring(i, end);
            BreakIterator bi = BreakIterator.getCharacterInstance();
            bi.setText(portion);
            int start = bi.first();
            int boundary = bi.next();
            while (boundary != BreakIterator.DONE) {
                String grapheme = portion.substring(start, boundary);
                width += graphemeWidth(grapheme);
                start = boundary;
                boundary = bi.next();
            }
            i = end;
        }
        return width;
    }

    private static boolean isPureAscii(String str) {
        for (int i = 0; i < str.length(); i++) {
            char c = str.charAt(i);
            if (c < 0x20 || c > 0x7E) {
                return false;
            }
        }
        return true;
    }

    /**
     * Extracts a range of visible columns from a line, preserving ANSI codes.
     * Wide characters that start within range but extend past endCol are included.
     *
     * @param text     the source text (may contain ANSI escape codes)
     * @param startCol the starting visible column (0-based, inclusive)
     * @param endCol   the ending visible column (exclusive)
     * @return the sliced text with ANSI codes preserved
     */
    public static String sliceByColumn(String text, int startCol, int endCol) {
        if (text == null || text.isEmpty() || endCol <= startCol) {
            return "";
        }
        StringBuilder result = new StringBuilder();
        StringBuilder pendingAnsi = new StringBuilder();
        int currentCol = 0;
        int i = 0;
        while (i < text.length()) {
            AnsiCode ansi = extractAnsiCode(text, i);
            if (ansi != null) {
                if (currentCol >= startCol && currentCol < endCol) {
                    result.append(ansi.code());
                } else if (currentCol < startCol) {
                    pendingAnsi.append(ansi.code());
                }
                i += ansi.length();
                continue;
            }
            int textEnd = findNextAnsi(text, i);
            currentCol = appendGraphemesInRange(
                    text.substring(i, textEnd), currentCol, startCol, endCol, pendingAnsi, result);
            i = textEnd;
            if (currentCol >= endCol) {
                break;
            }
        }
        return result.toString();
    }

    private static int findNextAnsi(String text, int from) {
        int end = from;
        while (end < text.length() && extractAnsiCode(text, end) == null) {
            end++;
        }
        return end;
    }

    private static int appendGraphemesInRange(
            String portion, int currentCol, int startCol, int endCol, StringBuilder pendingAnsi, StringBuilder out) {
        BreakIterator bi = BreakIterator.getCharacterInstance();
        bi.setText(portion);
        int start = bi.first();
        int boundary = bi.next();
        while (boundary != BreakIterator.DONE) {
            String grapheme = portion.substring(start, boundary);
            if (currentCol >= startCol && currentCol < endCol) {
                if (pendingAnsi.length() > 0) {
                    out.append(pendingAnsi);
                    pendingAnsi.setLength(0);
                }
                out.append(grapheme);
            }
            currentCol += graphemeWidth(grapheme);
            if (currentCol > endCol) {
                break;
            }
            start = boundary;
            boundary = bi.next();
        }
        return currentCol;
    }

    /**
     * Separates a string into ANSI escape sequences and visible text segments.
     *
     * @param text the text to extract segments from
     * @return a list of segments, each marked as ANSI or visible text
     */
    public static List<AnsiSegment> extractSegments(String text) {
        if (text == null || text.isEmpty()) {
            return List.of();
        }

        List<AnsiSegment> segments = new ArrayList<>();
        StringBuilder currentText = new StringBuilder();
        int i = 0;

        while (i < text.length()) {
            AnsiCode ansi = extractAnsiCode(text, i);
            if (ansi != null) {
                // Flush any pending visible text
                if (currentText.length() > 0) {
                    segments.add(new AnsiSegment(currentText.toString(), false));
                    currentText.setLength(0);
                }
                segments.add(new AnsiSegment(ansi.code(), true));
                i += ansi.length();
            } else {
                currentText.append(text.charAt(i));
                i++;
            }
        }

        // Flush remaining visible text
        if (currentText.length() > 0) {
            segments.add(new AnsiSegment(currentText.toString(), false));
        }

        return segments;
    }

    /**
     * Wraps text to a maximum visible width, preserving ANSI codes across line breaks.
     * Handles newlines in the input by treating each line separately.
     * Active ANSI styles carry over to wrapped continuation lines.
     *
     * @param text     the text to wrap (may contain ANSI codes and newlines)
     * @param maxWidth the maximum visible width per line
     * @return a list of wrapped lines (NOT padded to width)
     */
    public static List<String> wrapTextWithAnsi(String text, int maxWidth) {
        if (text == null || text.isEmpty()) {
            return List.of("");
        }

        String[] inputLines = text.split("\n", -1);
        List<String> result = new ArrayList<>();
        AnsiCodeTracker tracker = new AnsiCodeTracker();

        for (String inputLine : inputLines) {
            String prefix = !result.isEmpty() ? tracker.getActiveCodes() : "";
            result.addAll(wrapSingleLine(prefix + inputLine, maxWidth));
            updateTracker(inputLine, tracker);
        }

        return result.isEmpty() ? List.of("") : result;
    }

    /**
     * Applies a background color function to a line, padding it to the specified width.
     *
     * @param line  the line of text (may contain ANSI codes)
     * @param width the total width to pad to
     * @param bgFn  a function that wraps text with background color ANSI codes
     * @return the line with background applied and padded to width
     */
    public static String applyBackground(String line, int width, UnaryOperator<String> bgFn) {
        int visibleLen = visibleWidth(line);
        int paddingNeeded = Math.max(0, width - visibleLen);
        String withPadding = line + " ".repeat(paddingNeeded);
        return bgFn.apply(withPadding);
    }

    // -------------------------------------------------------------------
    // Internal helpers for wrapTextWithAnsi
    // -------------------------------------------------------------------

    /**
     * Mutable cursor over the in-progress wrapped line — used to keep wrapSingleLine flat.
     */
    private static final class WrapCursor {
        StringBuilder line = new StringBuilder();
        int visibleLen;

        void reset(String prefix, int len) {
            line = new StringBuilder(prefix);
            visibleLen = len;
        }

        void append(String token, int tokenWidth) {
            line.append(token);
            visibleLen += tokenWidth;
        }
    }

    private static List<String> wrapSingleLine(String line, int maxWidth) {
        if (line.isEmpty()) {
            return List.of("");
        }
        if (visibleWidth(line) <= maxWidth) {
            return List.of(line);
        }
        List<String> wrapped = new ArrayList<>();
        AnsiCodeTracker tracker = new AnsiCodeTracker();
        WrapCursor cursor = new WrapCursor();
        for (String token : splitIntoTokensWithAnsi(line)) {
            int tokenVisibleLen = visibleWidth(token);
            boolean isWhitespace = token.trim().isEmpty();
            if (tokenVisibleLen > maxWidth && !isWhitespace) {
                flushOverflowingToken(token, maxWidth, tracker, cursor, wrapped);
                continue;
            }
            if (cursor.visibleLen + tokenVisibleLen > maxWidth && cursor.visibleLen > 0) {
                wrapAndStartNewLine(token, tokenVisibleLen, isWhitespace, tracker, cursor, wrapped);
            } else {
                cursor.append(token, tokenVisibleLen);
            }
            updateTracker(token, tracker);
        }
        if (cursor.line.length() > 0) {
            wrapped.add(cursor.line.toString());
        }
        return wrapped.isEmpty()
                ? List.of("")
                : wrapped.stream().map(AnsiUtils::trimEnd).toList();
    }

    private static void flushOverflowingToken(
            String token, int maxWidth, AnsiCodeTracker tracker, WrapCursor cursor, List<String> wrapped) {
        if (cursor.line.length() > 0) {
            String reset = tracker.getLineEndReset();
            if (!reset.isEmpty()) {
                cursor.line.append(reset);
            }
            wrapped.add(cursor.line.toString());
            cursor.reset("", 0);
        }
        List<String> broken = breakLongWord(token, maxWidth, tracker);
        for (int k = 0; k < broken.size() - 1; k++) {
            wrapped.add(broken.get(k));
        }
        String last = broken.get(broken.size() - 1);
        cursor.line.append(last);
        cursor.visibleLen = visibleWidth(last);
    }

    private static void wrapAndStartNewLine(
            String token,
            int tokenVisibleLen,
            boolean isWhitespace,
            AnsiCodeTracker tracker,
            WrapCursor cursor,
            List<String> wrapped) {
        String lineToWrap = trimEnd(cursor.line.toString());
        String reset = tracker.getLineEndReset();
        if (!reset.isEmpty()) {
            lineToWrap += reset;
        }
        wrapped.add(lineToWrap);
        if (isWhitespace) {
            cursor.reset(tracker.getActiveCodes(), 0);
        } else {
            cursor.reset(tracker.getActiveCodes() + token, tokenVisibleLen);
        }
    }

    /**
     * Tagged segment yielded by {@link #segmentAnsiAware}: either an ANSI code or a visible grapheme.
     */
    private record WordSegment(boolean ansi, String value) {}

    private static List<WordSegment> segmentAnsiAware(String word) {
        List<WordSegment> segments = new ArrayList<>();
        int i = 0;
        while (i < word.length()) {
            AnsiCode ansi = extractAnsiCode(word, i);
            if (ansi != null) {
                segments.add(new WordSegment(true, ansi.code()));
                i += ansi.length();
                continue;
            }
            int end = findNextAnsi(word, i);
            String portion = word.substring(i, end);
            BreakIterator bi = BreakIterator.getCharacterInstance();
            bi.setText(portion);
            int start = bi.first();
            int boundary = bi.next();
            while (boundary != BreakIterator.DONE) {
                segments.add(new WordSegment(false, portion.substring(start, boundary)));
                start = boundary;
                boundary = bi.next();
            }
            i = end;
        }
        return segments;
    }

    private static List<String> breakLongWord(String word, int maxWidth, AnsiCodeTracker tracker) {
        List<String> lines = new ArrayList<>();
        StringBuilder currentLine = new StringBuilder(tracker.getActiveCodes());
        int currentWidth = 0;
        for (WordSegment seg : segmentAnsiAware(word)) {
            if (seg.ansi()) {
                currentLine.append(seg.value());
                tracker.process(seg.value());
                continue;
            }
            int gw = graphemeWidth(seg.value());
            if (gw == 0) {
                currentLine.append(seg.value());
                continue;
            }
            if (currentWidth + gw > maxWidth) {
                String lineEndReset = tracker.getLineEndReset();
                if (!lineEndReset.isEmpty()) {
                    currentLine.append(lineEndReset);
                }
                lines.add(currentLine.toString());
                currentLine = new StringBuilder(tracker.getActiveCodes());
                currentWidth = 0;
            }
            currentLine.append(seg.value());
            currentWidth += gw;
        }
        if (currentLine.length() > 0) {
            lines.add(currentLine.toString());
        }
        return lines.isEmpty() ? List.of("") : lines;
    }

    private static List<String> splitIntoTokensWithAnsi(String text) {
        List<String> tokens = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        StringBuilder pendingAnsi = new StringBuilder();
        boolean inWhitespace = false;
        int i = 0;

        while (i < text.length()) {
            AnsiCode ansi = extractAnsiCode(text, i);
            if (ansi != null) {
                pendingAnsi.append(ansi.code());
                i += ansi.length();
                continue;
            }

            int cp = text.codePointAt(i);
            int charCount = Character.charCount(cp);
            boolean charIsSpace = cp == ' ';
            boolean charIsWide = isEastAsianWide(cp);

            // Break token at: whitespace boundary OR before/after CJK wide characters.
            // CJK text has no spaces between characters, so without this, an entire
            // Chinese paragraph becomes one huge token that can't be word-wrapped.
            boolean shouldBreak = (charIsSpace != inWhitespace) || charIsWide;
            if (shouldBreak && current.length() > 0) {
                tokens.add(current.toString());
                current.setLength(0);
            }

            if (pendingAnsi.length() > 0) {
                current.append(pendingAnsi);
                pendingAnsi.setLength(0);
            }

            inWhitespace = charIsSpace;
            current.appendCodePoint(cp);

            // Also break AFTER a CJK character so each wide char is its own token
            if (charIsWide) {
                tokens.add(current.toString());
                current.setLength(0);
            }

            i += charCount;
        }

        if (pendingAnsi.length() > 0) {
            current.append(pendingAnsi);
        }
        if (current.length() > 0) {
            tokens.add(current.toString());
        }

        return tokens;
    }

    private static void updateTracker(String text, AnsiCodeTracker tracker) {
        int i = 0;
        while (i < text.length()) {
            AnsiCode ansi = extractAnsiCode(text, i);
            if (ansi != null) {
                tracker.process(ansi.code());
                i += ansi.length();
            } else {
                i++;
            }
        }
    }

    private static String trimEnd(String s) {
        int end = s.length();
        while (end > 0 && s.charAt(end - 1) == ' ') {
            end--;
        }
        return s.substring(0, end);
    }
}
