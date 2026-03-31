package com.campusclaw.codingagent.util;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Utilities for truncating text output by line count and byte size.
 * Supports head truncation (keep last N lines) and tail truncation (keep first N lines).
 */
public final class TruncationUtils {

    private TruncationUtils() {
    }

    public record TruncationResult(
            boolean truncated,
            int outputLines,
            int totalLines,
            Integer maxLines,
            Integer maxBytes,
            boolean firstLineExceedsLimit,
            String truncatedBy
    ) {
    }

    /**
     * Truncates text from the head (beginning), keeping the last lines.
     * If the text exceeds maxLines, the earliest lines are removed.
     * If the text exceeds maxBytes, lines are removed from the head until it fits.
     *
     * @param text     the text to truncate
     * @param maxLines maximum number of lines to keep
     * @param maxBytes maximum byte size (UTF-8) to keep
     * @return the truncation result with the truncated text accessible via toString on the lines
     */
    public static TruncationResult truncateHead(String text, int maxLines, int maxBytes) {
        if (text == null || text.isEmpty()) {
            return new TruncationResult(false, 0, 0, maxLines, maxBytes, false, null);
        }

        List<String> lines = splitLines(text);
        int totalLines = lines.size();
        boolean truncatedByLines = false;
        boolean truncatedByBytes = false;

        // Truncate by line count: keep the last maxLines lines
        if (lines.size() > maxLines) {
            lines = new ArrayList<>(lines.subList(lines.size() - maxLines, lines.size()));
            truncatedByLines = true;
        }

        // Truncate by byte size: remove lines from the head until within budget
        int totalBytes = computeByteSize(lines);
        if (totalBytes > maxBytes) {
            while (lines.size() > 1 && totalBytes > maxBytes) {
                totalBytes -= lines.get(0).getBytes(StandardCharsets.UTF_8).length + 1; // +1 for newline
                lines.remove(0);
            }
            truncatedByBytes = true;
        }

        boolean firstLineExceedsLimit = !lines.isEmpty()
                && lines.get(0).getBytes(StandardCharsets.UTF_8).length > maxBytes;

        boolean truncated = truncatedByLines || truncatedByBytes;
        String truncatedBy = null;
        if (truncated) {
            truncatedBy = truncatedByBytes ? "bytes" : "lines";
        }

        return new TruncationResult(
                truncated,
                lines.size(),
                totalLines,
                maxLines,
                maxBytes,
                firstLineExceedsLimit,
                truncatedBy
        );
    }

    /**
     * Truncates text from the tail (end), keeping the first lines.
     * If the text exceeds maxLines, trailing lines are removed.
     * If the text exceeds maxBytes, lines are removed from the tail until it fits.
     *
     * @param text     the text to truncate
     * @param maxLines maximum number of lines to keep
     * @param maxBytes maximum byte size (UTF-8) to keep
     * @return the truncation result
     */
    public static TruncationResult truncateTail(String text, int maxLines, int maxBytes) {
        if (text == null || text.isEmpty()) {
            return new TruncationResult(false, 0, 0, maxLines, maxBytes, false, null);
        }

        List<String> lines = splitLines(text);
        int totalLines = lines.size();
        boolean truncatedByLines = false;
        boolean truncatedByBytes = false;

        // Truncate by line count: keep the first maxLines lines
        if (lines.size() > maxLines) {
            lines = new ArrayList<>(lines.subList(0, maxLines));
            truncatedByLines = true;
        }

        // Truncate by byte size: remove lines from the tail until within budget
        int totalBytes = computeByteSize(lines);
        if (totalBytes > maxBytes) {
            while (lines.size() > 1 && totalBytes > maxBytes) {
                totalBytes -= lines.get(lines.size() - 1).getBytes(StandardCharsets.UTF_8).length + 1;
                lines.remove(lines.size() - 1);
            }
            truncatedByBytes = true;
        }

        boolean firstLineExceedsLimit = !lines.isEmpty()
                && lines.get(0).getBytes(StandardCharsets.UTF_8).length > maxBytes;

        boolean truncated = truncatedByLines || truncatedByBytes;
        String truncatedBy = null;
        if (truncated) {
            truncatedBy = truncatedByBytes ? "bytes" : "lines";
        }

        return new TruncationResult(
                truncated,
                lines.size(),
                totalLines,
                maxLines,
                maxBytes,
                firstLineExceedsLimit,
                truncatedBy
        );
    }

    /**
     * Truncates a single line to fit within maxBytes (UTF-8).
     * If the line already fits, it is returned unchanged.
     * Multi-byte characters are not split mid-character.
     *
     * @param line     the line to truncate
     * @param maxBytes maximum byte size
     * @return the truncated line
     */
    public static String truncateLine(String line, int maxBytes) {
        if (line == null || line.isEmpty()) {
            return line == null ? "" : line;
        }
        if (line.getBytes(StandardCharsets.UTF_8).length <= maxBytes) {
            return line;
        }

        // Walk code points to avoid splitting multi-byte characters
        int byteCount = 0;
        int i = 0;
        while (i < line.length()) {
            int cp = line.codePointAt(i);
            int cpBytes = new String(Character.toChars(cp)).getBytes(StandardCharsets.UTF_8).length;
            if (byteCount + cpBytes > maxBytes) {
                break;
            }
            byteCount += cpBytes;
            i += Character.charCount(cp);
        }
        return line.substring(0, i);
    }

    /**
     * Formats a byte count into a human-readable size string.
     * Uses KB for sizes >= 1024, MB for sizes >= 1024*1024, etc.
     *
     * @param bytes the byte count
     * @return formatted string like "32KB", "1.5MB"
     */
    public static String formatSize(long bytes) {
        if (bytes < 0) {
            return "0B";
        }
        if (bytes < 1024) {
            return bytes + "B";
        }
        if (bytes < 1024L * 1024) {
            double kb = bytes / 1024.0;
            return formatNumber(kb) + "KB";
        }
        if (bytes < 1024L * 1024 * 1024) {
            double mb = bytes / (1024.0 * 1024);
            return formatNumber(mb) + "MB";
        }
        double gb = bytes / (1024.0 * 1024 * 1024);
        return formatNumber(gb) + "GB";
    }

    private static String formatNumber(double value) {
        if (value == Math.floor(value) && value < 1_000_000) {
            return String.valueOf((long) value);
        }
        // One decimal place, strip trailing zero
        String formatted = String.format("%.1f", value);
        if (formatted.endsWith(".0")) {
            return formatted.substring(0, formatted.length() - 2);
        }
        return formatted;
    }

    private static List<String> splitLines(String text) {
        List<String> lines = new ArrayList<>();
        int start = 0;
        for (int i = 0; i < text.length(); i++) {
            if (text.charAt(i) == '\n') {
                lines.add(text.substring(start, i));
                start = i + 1;
            }
        }
        lines.add(text.substring(start));
        return lines;
    }

    private static int computeByteSize(List<String> lines) {
        int total = 0;
        for (int i = 0; i < lines.size(); i++) {
            total += lines.get(i).getBytes(StandardCharsets.UTF_8).length;
            if (i < lines.size() - 1) {
                total += 1; // newline separator
            }
        }
        return total;
    }
}
