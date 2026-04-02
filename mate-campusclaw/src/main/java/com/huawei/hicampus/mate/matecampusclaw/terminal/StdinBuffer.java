package com.huawei.hicampus.mate.matecampusclaw.tui.terminal;

import java.util.ArrayList;
import java.util.List;

/**
 * Buffer for stdin input that assembles escape sequences from raw bytes.
 * <p>
 * Terminal input arrives as individual bytes or small chunks. Escape sequences
 * (e.g. {@code \033[A} for arrow up) may arrive split across multiple reads.
 * This buffer accumulates input and yields complete key events.
 * <p>
 * Escape sequence detection:
 * <ul>
 *   <li>If input starts with {@code \033[}, buffer until a final byte (0x40-0x7E) arrives</li>
 *   <li>If input starts with {@code \033O}, read one more byte (SS3 sequences)</li>
 *   <li>If input starts with {@code \033} followed by a printable char, yield Alt+char</li>
 *   <li>Otherwise, yield individual characters</li>
 * </ul>
 */
public class StdinBuffer {

    private final StringBuilder buffer = new StringBuilder();

    // Timeout in milliseconds for incomplete escape sequences
    private static final long ESCAPE_TIMEOUT_MS = 50;
    private long lastInputTime;

    /**
     * Appends raw input data to the buffer.
     *
     * @param data the raw bytes received from stdin
     */
    public void append(String data) {
        if (data != null && !data.isEmpty()) {
            buffer.append(data);
            lastInputTime = System.currentTimeMillis();
        }
    }

    /**
     * Extracts all complete key events from the buffer.
     * Incomplete escape sequences are left in the buffer for the next call.
     *
     * @return a list of complete key event strings
     */
    public List<String> drain() {
        List<String> events = new ArrayList<>();
        long now = System.currentTimeMillis();

        while (buffer.length() > 0) {
            char first = buffer.charAt(0);

            if (first == '\033') {
                // Escape sequence
                String seq = tryParseEscapeSequence(now);
                if (seq == null) {
                    // Incomplete sequence — leave in buffer
                    break;
                }
                events.add(seq);
            } else {
                // Regular character or control character
                events.add(String.valueOf(first));
                buffer.deleteCharAt(0);
            }
        }

        return events;
    }

    /**
     * Returns true if the buffer has no pending data.
     */
    public boolean isEmpty() {
        return buffer.length() == 0;
    }

    /**
     * Clears all buffered data.
     */
    public void clear() {
        buffer.setLength(0);
    }

    /**
     * Returns the number of buffered characters.
     */
    public int size() {
        return buffer.length();
    }

    // -------------------------------------------------------------------
    // Escape sequence parsing
    // -------------------------------------------------------------------

    /**
     * Attempts to parse an escape sequence starting at position 0 in the buffer.
     * Returns the complete sequence string and removes it from the buffer, or
     * returns null if the sequence is incomplete.
     */
    private String tryParseEscapeSequence(long now) {
        int len = buffer.length();

        // Just a bare ESC
        if (len == 1) {
            // Check if we've waited long enough — bare ESC
            if (now - lastInputTime >= ESCAPE_TIMEOUT_MS) {
                buffer.deleteCharAt(0);
                return "\033";
            }
            return null; // wait for more input
        }

        char second = buffer.charAt(1);

        // CSI sequence: \033[ ... final_byte
        if (second == '[') {
            return tryParseCSI();
        }

        // SS3 sequence: \033O + one byte
        if (second == 'O') {
            if (len < 3) return null; // wait for final byte
            String seq = buffer.substring(0, 3);
            buffer.delete(0, 3);
            return seq;
        }

        // Alt + character: \033 + printable char
        if (second >= 0x20 && second < 0x7F) {
            String seq = buffer.substring(0, 2);
            buffer.delete(0, 2);
            return seq;
        }

        // Unknown sequence starting with ESC — yield bare ESC
        buffer.deleteCharAt(0);
        return "\033";
    }

    /**
     * Parses a CSI (Control Sequence Introducer) sequence: {@code \033[ params final}.
     * Parameters are bytes in 0x30-0x3F, intermediate bytes in 0x20-0x2F,
     * and the final byte is in 0x40-0x7E.
     */
    private String tryParseCSI() {
        int len = buffer.length();

        // Need at least \033[ + final byte
        if (len < 3) return null;

        int pos = 2;

        // Skip parameter bytes (0x30-0x3F: digits, semicolons, etc.)
        while (pos < len) {
            char c = buffer.charAt(pos);
            if (c >= 0x30 && c <= 0x3F) {
                pos++;
            } else {
                break;
            }
        }

        // Skip intermediate bytes (0x20-0x2F)
        while (pos < len) {
            char c = buffer.charAt(pos);
            if (c >= 0x20 && c <= 0x2F) {
                pos++;
            } else {
                break;
            }
        }

        // Need final byte
        if (pos >= len) return null;

        char finalByte = buffer.charAt(pos);
        if (finalByte >= 0x40 && finalByte <= 0x7E) {
            String seq = buffer.substring(0, pos + 1);
            buffer.delete(0, pos + 1);
            return seq;
        }

        // Invalid CSI — yield \033[ and let the rest be re-parsed
        String seq = buffer.substring(0, 2);
        buffer.delete(0, 2);
        return seq;
    }
}
