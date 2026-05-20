/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.hicampus.mate.matecampusclaw.ai.utils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

import org.junit.jupiter.api.Test;

class SurrogateSanitizerTest {

    @Test
    void nullInputReturnsNull() {
        assertNull(SurrogateSanitizer.sanitize(null));
    }

    @Test
    void emptyStringPassedThrough() {
        assertSame("", SurrogateSanitizer.sanitize(""));
    }

    @Test
    void plainAsciiPassedThrough() {
        String input = "hello world";
        assertSame(input, SurrogateSanitizer.sanitize(input));
    }

    @Test
    void unicodeBmpPreserved() {
        String input = "café résumé 中文";
        assertSame(input, SurrogateSanitizer.sanitize(input));
    }

    @Test
    void validSurrogatePairsPreserved() {
        // U+1F600 (😀) = high D83D + low DE00
        String emoji = new String(Character.toChars(0x1F600));
        String input = "hi " + emoji + " bye";
        assertEquals(input, SurrogateSanitizer.sanitize(input));
    }

    @Test
    void unpairedHighSurrogateRemoved() {
        String input = "a" + (char) 0xD83D + "b";
        assertEquals("ab", SurrogateSanitizer.sanitize(input));
    }

    @Test
    void unpairedLowSurrogateRemoved() {
        String input = "a" + (char) 0xDE00 + "b";
        assertEquals("ab", SurrogateSanitizer.sanitize(input));
    }

    @Test
    void mixedValidAndInvalidSurrogates() {
        String emoji = new String(Character.toChars(0x1F600));

        // valid emoji + unpaired high surrogate
        String input = emoji + (char) 0xD83D + "x";
        assertEquals(emoji + "x", SurrogateSanitizer.sanitize(input));
    }

    @Test
    void highSurrogateAtEndOfString() {
        String input = "abc" + (char) 0xD83D;
        assertEquals("abc", SurrogateSanitizer.sanitize(input));
    }
}
