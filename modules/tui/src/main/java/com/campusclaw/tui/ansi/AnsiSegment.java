package com.campusclaw.tui.ansi;

/**
 * Represents a segment of text that is either an ANSI escape sequence or visible text.
 *
 * @param text   the text content of this segment
 * @param isAnsi true if this segment is an ANSI escape sequence, false if visible text
 */
public record AnsiSegment(String text, boolean isAnsi) {
}
