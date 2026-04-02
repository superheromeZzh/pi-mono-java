package com.huawei.hicampus.mate.matecampusclaw.codingagent.tool.edit;

/**
 * Fuzzy text matching for the Edit tool.
 * When an exact match fails, attempts to find the closest match by normalizing whitespace.
 */
final class FuzzyMatch {

    private FuzzyMatch() {
    }

    /**
     * Result of a fuzzy match attempt.
     *
     * @param start 0-indexed start position in the haystack
     * @param end   0-indexed end position (exclusive) in the haystack
     */
    record Match(int start, int end) {
    }

    /**
     * Attempts to find needle in haystack using progressively relaxed matching:
     * 1. Exact match
     * 2. Whitespace-normalized match (collapse runs of whitespace to single space)
     *
     * @return the match position, or null if no match found
     */
    static Match fuzzyFindText(String haystack, String needle) {
        if (haystack == null || needle == null || needle.isEmpty()) {
            return null;
        }

        // 1. Exact match
        int idx = haystack.indexOf(needle);
        if (idx >= 0) {
            return new Match(idx, idx + needle.length());
        }

        // 2. Whitespace-normalized match
        return whitespaceNormalizedMatch(haystack, needle);
    }

    /**
     * Counts occurrences of needle in haystack (exact match).
     */
    static int countOccurrences(String haystack, String needle) {
        if (needle.isEmpty()) return 0;
        int count = 0;
        int idx = 0;
        while ((idx = haystack.indexOf(needle, idx)) >= 0) {
            count++;
            idx += needle.length();
        }
        return count;
    }

    private static Match whitespaceNormalizedMatch(String haystack, String needle) {
        String normalizedNeedle = normalizeWhitespace(needle);
        if (normalizedNeedle.isEmpty()) {
            return null;
        }

        // Slide a window over the haystack lines trying to match
        String[] haystackLines = haystack.split("\n", -1);
        String[] needleLines = needle.split("\n", -1);
        int needleLineCount = needleLines.length;

        if (needleLineCount > haystackLines.length) {
            return null;
        }

        for (int i = 0; i <= haystackLines.length - needleLineCount; i++) {
            boolean match = true;
            for (int j = 0; j < needleLineCount; j++) {
                if (!normalizeWhitespace(haystackLines[i + j]).equals(normalizeWhitespace(needleLines[j]))) {
                    match = false;
                    break;
                }
            }
            if (match) {
                // Compute start/end positions in the original haystack
                int start = 0;
                for (int k = 0; k < i; k++) {
                    start += haystackLines[k].length() + 1; // +1 for newline
                }
                int end = start;
                for (int k = i; k < i + needleLineCount; k++) {
                    end += haystackLines[k].length();
                    if (k < i + needleLineCount - 1) {
                        end += 1; // newline between lines
                    }
                }
                return new Match(start, end);
            }
        }

        return null;
    }

    private static String normalizeWhitespace(String s) {
        return s.strip().replaceAll("\\s+", " ");
    }
}
