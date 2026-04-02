package com.huawei.hicampus.mate.matecampusclaw.tui.ansi;

import java.text.BreakIterator;
import java.util.ArrayList;
import java.util.List;
import java.util.function.UnaryOperator;

/**
 * ANSI-aware text utility functions for terminal rendering.
 * Handles ANSI escape sequences, East Asian wide characters, and grapheme clusters.
 */
public final class AnsiUtils {

    private AnsiUtils() {
    }

    // -------------------------------------------------------------------
    // ANSI escape code extraction
    // -------------------------------------------------------------------

    /**
     * Extracts an ANSI escape sequence starting at the given position.
     * Supports CSI (ESC[), OSC (ESC]), and APC (ESC_) sequences.
     *
     * @return the escape code and its length, or null if no escape sequence starts at pos
     */
    static AnsiCode extractAnsiCode(String str, int pos) {
        if (pos >= str.length() || str.charAt(pos) != '\033') return null;
        if (pos + 1 >= str.length()) return null;

        char next = str.charAt(pos + 1);

        // CSI sequence: ESC [ ... <terminator>
        // CSI terminators are bytes in the range 0x40–0x7E (i.e. '@' to '~').
        // Intermediate bytes are in the range 0x20–0x2F.
        // Parameter bytes are in the range 0x30–0x3F (digits, semicolons, '?', etc.)
        if (next == '[') {
            int j = pos + 2;
            while (j < str.length()) {
                char c = str.charAt(j);
                // Final byte of CSI sequence: 0x40-0x7E
                if (c >= 0x40 && c <= 0x7E) {
                    return new AnsiCode(str.substring(pos, j + 1), j + 1 - pos);
                }
                // Only parameter (0x30-0x3F) and intermediate (0x20-0x2F) bytes are valid
                if (c < 0x20 || c > 0x3F) {
                    break; // Malformed sequence — stop scanning
                }
                j++;
            }
            return null;
        }

        // OSC sequence: ESC ] ... BEL or ESC ] ... ST (ESC \)
        if (next == ']') {
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

        // APC sequence: ESC _ ... BEL or ESC _ ... ST (ESC \)
        if (next == '_') {
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

        return null;
    }

    record AnsiCode(String code, int length) {
    }

    // -------------------------------------------------------------------
    // Character width calculation
    // -------------------------------------------------------------------

    /**
     * Returns the display width of a single code point in a terminal.
     * East Asian fullwidth/wide characters occupy 2 columns; most others occupy 1.
     * Control characters and zero-width characters return 0.
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
     * Checks if a code point is East Asian Fullwidth or Wide.
     */
    private static boolean isEastAsianWide(int cp) {
        // CJK Unified Ideographs
        if (cp >= 0x4E00 && cp <= 0x9FFF) return true;
        // CJK Unified Ideographs Extension A
        if (cp >= 0x3400 && cp <= 0x4DBF) return true;
        // CJK Unified Ideographs Extension B
        if (cp >= 0x20000 && cp <= 0x2A6DF) return true;
        // CJK Unified Ideographs Extension C-H
        if (cp >= 0x2A700 && cp <= 0x323AF) return true;
        // CJK Compatibility Ideographs
        if (cp >= 0xF900 && cp <= 0xFAFF) return true;
        // CJK Compatibility Ideographs Supplement
        if (cp >= 0x2F800 && cp <= 0x2FA1F) return true;
        // Fullwidth Forms (Fullwidth ASCII, Fullwidth punctuation)
        if (cp >= 0xFF01 && cp <= 0xFF60) return true;
        if (cp >= 0xFFE0 && cp <= 0xFFE6) return true;
        // CJK Radicals Supplement
        if (cp >= 0x2E80 && cp <= 0x2EFF) return true;
        // Kangxi Radicals
        if (cp >= 0x2F00 && cp <= 0x2FDF) return true;
        // CJK Symbols and Punctuation
        if (cp >= 0x3000 && cp <= 0x303F) return true;
        // Hiragana
        if (cp >= 0x3040 && cp <= 0x309F) return true;
        // Katakana
        if (cp >= 0x30A0 && cp <= 0x30FF) return true;
        // Bopomofo
        if (cp >= 0x3100 && cp <= 0x312F) return true;
        // Hangul Compatibility Jamo
        if (cp >= 0x3130 && cp <= 0x318F) return true;
        // Kanbun
        if (cp >= 0x3190 && cp <= 0x319F) return true;
        // Bopomofo Extended
        if (cp >= 0x31A0 && cp <= 0x31BF) return true;
        // CJK Strokes
        if (cp >= 0x31C0 && cp <= 0x31EF) return true;
        // Katakana Phonetic Extensions
        if (cp >= 0x31F0 && cp <= 0x31FF) return true;
        // Enclosed CJK Letters and Months
        if (cp >= 0x3200 && cp <= 0x32FF) return true;
        // CJK Compatibility
        if (cp >= 0x3300 && cp <= 0x33FF) return true;
        // Hangul Syllables
        if (cp >= 0xAC00 && cp <= 0xD7AF) return true;
        // Hangul Jamo Extended-B
        if (cp >= 0xD7B0 && cp <= 0xD7FF) return true;
        // Emoji that are typically rendered as wide
        if (cp >= 0x1F000 && cp <= 0x1FBFF) return true;
        // Regional indicator symbols
        if (cp >= 0x1F1E6 && cp <= 0x1F1FF) return true;
        return false;
    }

    /**
     * Returns the display width of a grapheme cluster (a string segment from BreakIterator).
     */
    private static int graphemeWidth(String grapheme) {
        if (grapheme.isEmpty()) return 0;
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
        if (text == null || text.isEmpty()) return 0;

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
            if (c < 0x20 || c > 0x7E) return false;
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
        if (text == null || text.isEmpty() || endCol <= startCol) return "";

        StringBuilder result = new StringBuilder();
        StringBuilder pendingAnsi = new StringBuilder();
        int currentCol = 0;
        int i = 0;

        while (i < text.length()) {
            // Check for ANSI code
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

            // Find the extent of non-ANSI text
            int textEnd = i;
            while (textEnd < text.length() && extractAnsiCode(text, textEnd) == null) {
                textEnd++;
            }

            // Segment into graphemes
            String portion = text.substring(i, textEnd);
            BreakIterator bi = BreakIterator.getCharacterInstance();
            bi.setText(portion);
            int start = bi.first();
            int boundary = bi.next();
            while (boundary != BreakIterator.DONE) {
                String grapheme = portion.substring(start, boundary);
                int w = graphemeWidth(grapheme);

                boolean inRange = currentCol >= startCol && currentCol < endCol;
                if (inRange) {
                    if (pendingAnsi.length() > 0) {
                        result.append(pendingAnsi);
                        pendingAnsi.setLength(0);
                    }
                    result.append(grapheme);
                }

                currentCol += w;
                if (currentCol > endCol) break;

                start = boundary;
                boundary = bi.next();
            }
            i = textEnd;
            if (currentCol >= endCol) break;
        }

        return result.toString();
    }

    /**
     * Separates a string into ANSI escape sequences and visible text segments.
     *
     * @param text the text to extract segments from
     * @return a list of segments, each marked as ANSI or visible text
     */
    public static List<AnsiSegment> extractSegments(String text) {
        if (text == null || text.isEmpty()) return List.of();

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

    private static List<String> wrapSingleLine(String line, int maxWidth) {
        if (line.isEmpty()) {
            return List.of("");
        }

        int visibleLen = visibleWidth(line);
        if (visibleLen <= maxWidth) {
            return List.of(line);
        }

        List<String> wrapped = new ArrayList<>();
        AnsiCodeTracker tracker = new AnsiCodeTracker();
        List<String> tokens = splitIntoTokensWithAnsi(line);

        StringBuilder currentLine = new StringBuilder();
        int currentVisibleLen = 0;

        for (String token : tokens) {
            int tokenVisibleLen = visibleWidth(token);
            boolean isWhitespace = token.trim().isEmpty();

            // Token itself is too long — break it grapheme by grapheme
            if (tokenVisibleLen > maxWidth && !isWhitespace) {
                if (currentLine.length() > 0) {
                    String lineEndReset = tracker.getLineEndReset();
                    if (!lineEndReset.isEmpty()) {
                        currentLine.append(lineEndReset);
                    }
                    wrapped.add(currentLine.toString());
                    currentLine.setLength(0);
                    currentVisibleLen = 0;
                }

                List<String> broken = breakLongWord(token, maxWidth, tracker);
                for (int k = 0; k < broken.size() - 1; k++) {
                    wrapped.add(broken.get(k));
                }
                String last = broken.get(broken.size() - 1);
                currentLine.append(last);
                currentVisibleLen = visibleWidth(last);
                continue;
            }

            // Check if adding this token would exceed width
            int totalNeeded = currentVisibleLen + tokenVisibleLen;
            if (totalNeeded > maxWidth && currentVisibleLen > 0) {
                // Wrap current line
                String lineToWrap = trimEnd(currentLine.toString());
                String lineEndReset = tracker.getLineEndReset();
                if (!lineEndReset.isEmpty()) {
                    lineToWrap += lineEndReset;
                }
                wrapped.add(lineToWrap);
                if (isWhitespace) {
                    currentLine = new StringBuilder(tracker.getActiveCodes());
                    currentVisibleLen = 0;
                } else {
                    currentLine = new StringBuilder(tracker.getActiveCodes() + token);
                    currentVisibleLen = tokenVisibleLen;
                }
            } else {
                currentLine.append(token);
                currentVisibleLen += tokenVisibleLen;
            }

            updateTracker(token, tracker);
        }

        if (currentLine.length() > 0) {
            wrapped.add(currentLine.toString());
        }

        // Trim trailing whitespace from all lines
        return wrapped.isEmpty() ? List.of("") : wrapped.stream().map(AnsiUtils::trimEnd).toList();
    }

    private static List<String> breakLongWord(String word, int maxWidth, AnsiCodeTracker tracker) {
        List<String> lines = new ArrayList<>();
        StringBuilder currentLine = new StringBuilder(tracker.getActiveCodes());
        int currentWidth = 0;

        // Separate ANSI codes from visible graphemes
        List<Object[]> segments = new ArrayList<>(); // [type, value]: type 0 = ansi, type 1 = grapheme
        int i = 0;
        while (i < word.length()) {
            AnsiCode ansi = extractAnsiCode(word, i);
            if (ansi != null) {
                segments.add(new Object[]{0, ansi.code()});
                i += ansi.length();
            } else {
                // Collect non-ANSI text and segment into graphemes
                int end = i;
                while (end < word.length() && extractAnsiCode(word, end) == null) {
                    end++;
                }
                String portion = word.substring(i, end);
                BreakIterator bi = BreakIterator.getCharacterInstance();
                bi.setText(portion);
                int start = bi.first();
                int boundary = bi.next();
                while (boundary != BreakIterator.DONE) {
                    segments.add(new Object[]{1, portion.substring(start, boundary)});
                    start = boundary;
                    boundary = bi.next();
                }
                i = end;
            }
        }

        for (Object[] seg : segments) {
            int type = (int) seg[0];
            String value = (String) seg[1];

            if (type == 0) { // ANSI code
                currentLine.append(value);
                tracker.process(value);
                continue;
            }

            // Grapheme
            int gw = graphemeWidth(value);
            if (gw == 0) {
                currentLine.append(value);
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

            currentLine.append(value);
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
