package com.huawei.hicampus.mate.matecampusclaw.codingagent.tool.edit;

import static org.junit.jupiter.api.Assertions.*;

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
            var result = FuzzyMatch.fuzzyFindText("    indented\n    line", "indented\nline");
            assertNotNull(result);
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
