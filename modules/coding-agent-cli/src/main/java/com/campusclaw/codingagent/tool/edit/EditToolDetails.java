package com.campusclaw.codingagent.tool.edit;

/**
 * Structured details returned alongside the Edit tool result.
 *
 * @param diff             unified diff of the changes made
 * @param firstChangedLine 1-indexed line number of the first change, or null
 */
public record EditToolDetails(
        String diff,
        Integer firstChangedLine
) {
}
