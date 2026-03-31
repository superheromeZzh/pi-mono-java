package com.campusclaw.codingagent.tool.edit;

import java.util.ArrayList;
import java.util.List;

/**
 * Generates unified diff output from before/after text.
 */
final class DiffUtils {

    private DiffUtils() {
    }

    static final int CONTEXT_LINES = 3;

    /**
     * Computes a unified diff between two texts.
     *
     * @param oldText  the original text
     * @param newText  the modified text
     * @param fileName the file name for the diff header
     * @return unified diff string
     */
    static String computeUnifiedDiff(String oldText, String newText, String fileName) {
        String[] oldLines = oldText.split("\n", -1);
        String[] newLines = newText.split("\n", -1);

        List<int[]> changes = computeChanges(oldLines, newLines);
        if (changes.isEmpty()) {
            return "";
        }

        var sb = new StringBuilder();
        sb.append("--- a/").append(fileName).append('\n');
        sb.append("+++ b/").append(fileName).append('\n');

        // Group changes into hunks with context
        List<int[]> hunks = groupIntoHunks(changes, oldLines.length, newLines.length);

        for (int[] hunk : hunks) {
            int oldStart = hunk[0];
            int oldEnd = hunk[1];
            int newStart = hunk[2];
            int newEnd = hunk[3];

            sb.append(String.format("@@ -%d,%d +%d,%d @@\n",
                    oldStart + 1, oldEnd - oldStart,
                    newStart + 1, newEnd - newStart));

            appendHunkContent(sb, oldLines, newLines, oldStart, oldEnd, newStart, newEnd, changes);
        }

        return sb.toString();
    }

    /**
     * Finds the 1-indexed line number of the first change.
     */
    static Integer findFirstChangedLine(String oldText, String newText) {
        String[] oldLines = oldText.split("\n", -1);
        String[] newLines = newText.split("\n", -1);
        int minLen = Math.min(oldLines.length, newLines.length);
        for (int i = 0; i < minLen; i++) {
            if (!oldLines[i].equals(newLines[i])) {
                return i + 1;
            }
        }
        if (oldLines.length != newLines.length) {
            return minLen + 1;
        }
        return null;
    }

    /**
     * Computes a list of change regions as [oldIdx, newIdx] pairs for each differing line.
     * Uses a simple LCS-based approach for small diffs.
     */
    private static List<int[]> computeChanges(String[] oldLines, String[] newLines) {
        // Find common prefix
        int prefixLen = 0;
        int minLen = Math.min(oldLines.length, newLines.length);
        while (prefixLen < minLen && oldLines[prefixLen].equals(newLines[prefixLen])) {
            prefixLen++;
        }

        // Find common suffix
        int suffixLen = 0;
        while (suffixLen < minLen - prefixLen
                && oldLines[oldLines.length - 1 - suffixLen].equals(newLines[newLines.length - 1 - suffixLen])) {
            suffixLen++;
        }

        int oldDiffStart = prefixLen;
        int oldDiffEnd = oldLines.length - suffixLen;
        int newDiffStart = prefixLen;
        int newDiffEnd = newLines.length - suffixLen;

        if (oldDiffStart == oldDiffEnd && newDiffStart == newDiffEnd) {
            return List.of();
        }

        List<int[]> changes = new ArrayList<>();
        // Each change: [oldLineStart, oldLineEnd, newLineStart, newLineEnd]
        changes.add(new int[]{oldDiffStart, oldDiffEnd, newDiffStart, newDiffEnd});
        return changes;
    }

    private static List<int[]> groupIntoHunks(List<int[]> changes, int oldLen, int newLen) {
        List<int[]> hunks = new ArrayList<>();
        for (int[] change : changes) {
            int ctxBefore = Math.min(CONTEXT_LINES, change[0]);
            int ctxAfterOld = Math.min(CONTEXT_LINES, oldLen - change[1]);
            int ctxAfterNew = Math.min(CONTEXT_LINES, newLen - change[3]);
            int ctxAfter = Math.min(ctxAfterOld, ctxAfterNew);

            hunks.add(new int[]{
                    change[0] - ctxBefore,
                    change[1] + ctxAfter,
                    change[2] - ctxBefore,
                    change[3] + ctxAfter
            });
        }
        return hunks;
    }

    private static void appendHunkContent(
            StringBuilder sb,
            String[] oldLines, String[] newLines,
            int oldStart, int oldEnd, int newStart, int newEnd,
            List<int[]> changes
    ) {
        int[] change = changes.get(0);
        // Context before
        for (int i = oldStart; i < change[0]; i++) {
            sb.append(' ').append(oldLines[i]).append('\n');
        }
        // Removed lines
        for (int i = change[0]; i < change[1]; i++) {
            sb.append('-').append(oldLines[i]).append('\n');
        }
        // Added lines
        for (int i = change[2]; i < change[3]; i++) {
            sb.append('+').append(newLines[i]).append('\n');
        }
        // Context after
        for (int i = change[1]; i < oldEnd; i++) {
            sb.append(' ').append(oldLines[i]).append('\n');
        }
    }
}
