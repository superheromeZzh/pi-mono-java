/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.campusclaw.codingagent.tool.edit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class FuzzyMatchTest {

    @Nested
    class ExactMatch {

        @Test
        void findsExactSubstring() {
            var result = FuzzyMatch.fuzzyFindText("hello world", "world");
            assertNotNull(result);
            assertEquals(6, result.start());
            assertEquals(11, result.end());
        }

        @Test
        void findsAtBeginning() {
            var result = FuzzyMatch.fuzzyFindText("hello world", "hello");
            assertNotNull(result);
            assertEquals(0, result.start());
            assertEquals(5, result.end());
        }

        @Test
        void findsFullString() {
            var result = FuzzyMatch.fuzzyFindText("exact", "exact");
            assertNotNull(result);
            assertEquals(0, result.start());
            assertEquals(5, result.end());
        }
    }

    @Nested
    class WhitespaceNormalized {

        @Test
        void matchesWithExtraSpaces() {
            var result = FuzzyMatch.fuzzyFindText("  hello   world  ", "hello world");
            assertNotNull(result);
            assertEquals("  hello   world  ", "  hello   world  ".substring(result.start(), result.end()));
        }

        @Test
        void matchesWithDifferentIndentation() {
            String haystack = "    indented\n    line";
            var result = FuzzyMatch.fuzzyFindText(haystack, "indented\nline");

            // Whitespace-normalized fuzzy match returns the haystack range that maps to the needle,
            // including the differently-indented prefix that was normalized away.
            assertEquals(haystack, haystack.substring(result.start(), result.end()));
        }

        @Test
        void matchesMultiLineWithWhitespace() {
            String haystack = "  line1  \n  line2  \nline3";
            var result = FuzzyMatch.fuzzyFindText(haystack, "line1\nline2");
            assertNotNull(result);

            // Should match the first two lines
            assertEquals("  line1  \n  line2  ", haystack.substring(result.start(), result.end()));
        }
    }

    @Nested
    class NoMatch {

        @Test
        void returnsNullForNoMatch() {
            assertNull(FuzzyMatch.fuzzyFindText("hello world", "xyz"));
        }

        @Test
        void nullHaystack() {
            assertNull(FuzzyMatch.fuzzyFindText(null, "test"));
        }

        @Test
        void nullNeedle() {
            assertNull(FuzzyMatch.fuzzyFindText("test", null));
        }

        @Test
        void emptyNeedle() {
            assertNull(FuzzyMatch.fuzzyFindText("test", ""));
        }
    }

    @Nested
    class CountOccurrences {

        @Test
        void noOccurrences() {
            assertEquals(0, FuzzyMatch.countOccurrences("hello", "xyz"));
        }

        @Test
        void singleOccurrence() {
            assertEquals(1, FuzzyMatch.countOccurrences("hello world", "world"));
        }

        @Test
        void multipleOccurrences() {
            assertEquals(3, FuzzyMatch.countOccurrences("aXaXaX", "X"));
        }

        @Test
        void nonOverlapping() {
            assertEquals(2, FuzzyMatch.countOccurrences("aaaa", "aa"));
        }

        @Test
        void emptyNeedleReturnsZero() {
            assertEquals(0, FuzzyMatch.countOccurrences("hello", ""));
        }
    }
}
