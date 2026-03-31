package com.campusclaw.ai.utils;

/**
 * Removes unpaired Unicode surrogate characters from strings.
 * Aligned with TypeScript campusclaw utils/sanitize-unicode.ts.
 *
 * <p>Unpaired surrogates (high surrogates 0xD800-0xDBFF without matching
 * low surrogates 0xDC00-0xDFFF, or vice versa) cause JSON serialization
 * errors in many API providers.
 *
 * <p>Valid emoji and other characters outside the Basic Multilingual Plane
 * use properly paired surrogates and are NOT affected.
 */
public final class SurrogateSanitizer {

    private SurrogateSanitizer() {}

    /**
     * Removes unpaired Unicode surrogates from the given text.
     *
     * @param text the text to sanitize (may be null)
     * @return sanitized text with unpaired surrogates removed, or null if input is null
     */
    public static String sanitize(String text) {
        if (text == null) return null;

        int len = text.length();
        StringBuilder sb = null; // Lazily initialized only if surrogates found

        for (int i = 0; i < len; i++) {
            char c = text.charAt(i);

            if (Character.isHighSurrogate(c)) {
                if (i + 1 < len && Character.isLowSurrogate(text.charAt(i + 1))) {
                    // Valid surrogate pair — keep both
                    if (sb != null) {
                        sb.append(c);
                        sb.append(text.charAt(i + 1));
                    }
                    i++; // Skip low surrogate
                } else {
                    // Unpaired high surrogate — skip it
                    if (sb == null) {
                        sb = new StringBuilder(len);
                        sb.append(text, 0, i);
                    }
                }
            } else if (Character.isLowSurrogate(c)) {
                // Unpaired low surrogate — skip it
                if (sb == null) {
                    sb = new StringBuilder(len);
                    sb.append(text, 0, i);
                }
            } else {
                // Normal character
                if (sb != null) {
                    sb.append(c);
                }
            }
        }

        return sb != null ? sb.toString() : text;
    }
}
