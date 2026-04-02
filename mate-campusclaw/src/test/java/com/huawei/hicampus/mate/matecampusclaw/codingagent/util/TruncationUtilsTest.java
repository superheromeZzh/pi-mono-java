package com.huawei.hicampus.mate.matecampusclaw.codingagent.util;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class TruncationUtilsTest {

    // -------------------------------------------------------------------
    // truncateHead
    // -------------------------------------------------------------------

    @Nested
    class TruncateHead {

        @Test
        void nullText() {
            var result = TruncationUtils.truncateHead(null, 10, 1000);
            assertFalse(result.truncated());
            assertEquals(0, result.outputLines());
            assertEquals(0, result.totalLines());
        }

        @Test
        void emptyText() {
            var result = TruncationUtils.truncateHead("", 10, 1000);
            assertFalse(result.truncated());
            assertEquals(0, result.outputLines());
        }

        @Test
        void noTruncationNeeded() {
            var result = TruncationUtils.truncateHead("line1\nline2\nline3", 10, 10000);
            assertFalse(result.truncated());
            assertEquals(3, result.outputLines());
            assertEquals(3, result.totalLines());
            assertNull(result.truncatedBy());
        }

        @Test
        void truncateByLines() {
            var result = TruncationUtils.truncateHead("line1\nline2\nline3\nline4\nline5", 3, 10000);
            assertTrue(result.truncated());
            assertEquals(3, result.outputLines());
            assertEquals(5, result.totalLines());
            assertEquals("lines", result.truncatedBy());
        }

        @Test
        void truncateByBytes() {
            // "abcde\nfghij\nklmno" = 5+1+5+1+5 = 17 bytes
            var result = TruncationUtils.truncateHead("abcde\nfghij\nklmno", 10, 11);
            assertTrue(result.truncated());
            assertEquals("bytes", result.truncatedBy());
            // Should keep the last lines that fit in 11 bytes: "fghij\nklmno" = 11 bytes
            assertEquals(2, result.outputLines());
        }

        @Test
        void keepsLastLinesOnLineTruncation() {
            // When truncating by lines, head truncation keeps the LAST lines
            var result = TruncationUtils.truncateHead("A\nB\nC\nD", 2, 10000);
            assertTrue(result.truncated());
            assertEquals(2, result.outputLines());
            assertEquals(4, result.totalLines());
        }

        @Test
        void singleLineFitsExactly() {
            var result = TruncationUtils.truncateHead("hello", 1, 5);
            assertFalse(result.truncated());
            assertEquals(1, result.outputLines());
        }

        @Test
        void firstLineExceedsLimit() {
            // Single line that is larger than maxBytes
            var result = TruncationUtils.truncateHead("this is a very long line", 10, 5);
            assertTrue(result.firstLineExceedsLimit());
        }

        @Test
        void maxLinesAndMaxBytesRecorded() {
            var result = TruncationUtils.truncateHead("abc", 7, 42);
            assertEquals(7, result.maxLines());
            assertEquals(42, result.maxBytes());
        }
    }

    // -------------------------------------------------------------------
    // truncateTail
    // -------------------------------------------------------------------

    @Nested
    class TruncateTail {

        @Test
        void nullText() {
            var result = TruncationUtils.truncateTail(null, 10, 1000);
            assertFalse(result.truncated());
            assertEquals(0, result.outputLines());
        }

        @Test
        void emptyText() {
            var result = TruncationUtils.truncateTail("", 10, 1000);
            assertFalse(result.truncated());
            assertEquals(0, result.outputLines());
        }

        @Test
        void noTruncationNeeded() {
            var result = TruncationUtils.truncateTail("line1\nline2\nline3", 10, 10000);
            assertFalse(result.truncated());
            assertEquals(3, result.outputLines());
            assertEquals(3, result.totalLines());
        }

        @Test
        void truncateByLines() {
            var result = TruncationUtils.truncateTail("line1\nline2\nline3\nline4\nline5", 3, 10000);
            assertTrue(result.truncated());
            assertEquals(3, result.outputLines());
            assertEquals(5, result.totalLines());
            assertEquals("lines", result.truncatedBy());
        }

        @Test
        void truncateByBytes() {
            // "abcde\nfghij\nklmno" = 17 bytes total
            var result = TruncationUtils.truncateTail("abcde\nfghij\nklmno", 10, 11);
            assertTrue(result.truncated());
            assertEquals("bytes", result.truncatedBy());
            // Should keep first lines that fit: "abcde\nfghij" = 11 bytes
            assertEquals(2, result.outputLines());
        }

        @Test
        void keepsFirstLinesOnLineTruncation() {
            var result = TruncationUtils.truncateTail("A\nB\nC\nD", 2, 10000);
            assertTrue(result.truncated());
            assertEquals(2, result.outputLines());
            assertEquals(4, result.totalLines());
        }

        @Test
        void singleLineFitsExactly() {
            var result = TruncationUtils.truncateTail("hello", 1, 5);
            assertFalse(result.truncated());
            assertEquals(1, result.outputLines());
        }

        @Test
        void firstLineExceedsLimit() {
            var result = TruncationUtils.truncateTail("this is a very long line", 10, 5);
            assertTrue(result.firstLineExceedsLimit());
        }

        @Test
        void bytesTruncationRemovesFromTail() {
            // Tail truncation removes from the end
            // "aa\nbb\ncc" = 2+1+2+1+2 = 8 bytes
            var result = TruncationUtils.truncateTail("aa\nbb\ncc", 10, 5);
            assertTrue(result.truncated());
            assertEquals("bytes", result.truncatedBy());
            // "aa\nbb" = 5 bytes
            assertEquals(2, result.outputLines());
        }
    }

    // -------------------------------------------------------------------
    // truncateLine
    // -------------------------------------------------------------------

    @Nested
    class TruncateLine {

        @Test
        void nullLine() {
            assertEquals("", TruncationUtils.truncateLine(null, 10));
        }

        @Test
        void emptyLine() {
            assertEquals("", TruncationUtils.truncateLine("", 10));
        }

        @Test
        void lineWithinLimit() {
            assertEquals("hello", TruncationUtils.truncateLine("hello", 10));
        }

        @Test
        void lineExactlyAtLimit() {
            assertEquals("hello", TruncationUtils.truncateLine("hello", 5));
        }

        @Test
        void lineTruncated() {
            assertEquals("hel", TruncationUtils.truncateLine("hello", 3));
        }

        @Test
        void zeroMaxBytes() {
            assertEquals("", TruncationUtils.truncateLine("hello", 0));
        }

        @Test
        void multiByteSafeUtf8() {
            // "你好" = 6 bytes in UTF-8 (3 bytes per char)
            // maxBytes=4 should keep only "你" (3 bytes), not split mid-character
            assertEquals("你", TruncationUtils.truncateLine("你好", 4));
        }

        @Test
        void multiByteSafeUtf8ExactBoundary() {
            // "你好" = 6 bytes, maxBytes=6 => no truncation
            assertEquals("你好", TruncationUtils.truncateLine("你好", 6));
        }

        @Test
        void multiByteSafeUtf8TooSmall() {
            // "你" = 3 bytes, maxBytes=2 => cannot fit even one char
            assertEquals("", TruncationUtils.truncateLine("你", 2));
        }

        @Test
        void mixedAsciiAndMultiByte() {
            // "hi你" = 2 + 3 = 5 bytes
            assertEquals("hi", TruncationUtils.truncateLine("hi你", 4));
            assertEquals("hi你", TruncationUtils.truncateLine("hi你", 5));
        }

        @Test
        void emojiHandling() {
            // Emoji "😀" is 4 bytes in UTF-8
            assertEquals("😀", TruncationUtils.truncateLine("😀x", 4));
            assertEquals("", TruncationUtils.truncateLine("😀", 3));
        }
    }

    // -------------------------------------------------------------------
    // formatSize
    // -------------------------------------------------------------------

    @Nested
    class FormatSize {

        @Test
        void zeroBytes() {
            assertEquals("0B", TruncationUtils.formatSize(0));
        }

        @Test
        void negativeBytes() {
            assertEquals("0B", TruncationUtils.formatSize(-1));
        }

        @Test
        void bytesRange() {
            assertEquals("1B", TruncationUtils.formatSize(1));
            assertEquals("512B", TruncationUtils.formatSize(512));
            assertEquals("1023B", TruncationUtils.formatSize(1023));
        }

        @Test
        void kilobytesExact() {
            assertEquals("1KB", TruncationUtils.formatSize(1024));
            assertEquals("32KB", TruncationUtils.formatSize(32 * 1024));
        }

        @Test
        void kilobytesWithDecimal() {
            assertEquals("1.5KB", TruncationUtils.formatSize(1536)); // 1.5 * 1024
        }

        @Test
        void megabytesExact() {
            assertEquals("1MB", TruncationUtils.formatSize(1024L * 1024));
            assertEquals("10MB", TruncationUtils.formatSize(10L * 1024 * 1024));
        }

        @Test
        void megabytesWithDecimal() {
            assertEquals("1.5MB", TruncationUtils.formatSize((long) (1.5 * 1024 * 1024)));
        }

        @Test
        void gigabytesExact() {
            assertEquals("1GB", TruncationUtils.formatSize(1024L * 1024 * 1024));
        }

        @Test
        void gigabytesWithDecimal() {
            assertEquals("2.5GB", TruncationUtils.formatSize((long) (2.5 * 1024 * 1024 * 1024)));
        }
    }

    // -------------------------------------------------------------------
    // Edge cases
    // -------------------------------------------------------------------

    @Nested
    class EdgeCases {

        @Test
        void singleLineNoNewline() {
            var head = TruncationUtils.truncateHead("hello", 5, 100);
            assertFalse(head.truncated());
            assertEquals(1, head.outputLines());
            assertEquals(1, head.totalLines());

            var tail = TruncationUtils.truncateTail("hello", 5, 100);
            assertFalse(tail.truncated());
            assertEquals(1, tail.outputLines());
        }

        @Test
        void trailingNewline() {
            // "a\n" splits into ["a", ""]
            var result = TruncationUtils.truncateTail("a\n", 10, 1000);
            assertEquals(2, result.totalLines());
        }

        @Test
        void multipleEmptyLines() {
            var result = TruncationUtils.truncateTail("\n\n\n", 2, 1000);
            assertTrue(result.truncated());
            assertEquals(2, result.outputLines());
            assertEquals(4, result.totalLines());
        }

        @Test
        void linesTruncationTakesPrecedenceOverBytesWhenBothExceed() {
            // 5 lines, maxLines=2, maxBytes also small
            // Lines truncation happens first, then bytes checked
            var result = TruncationUtils.truncateTail("aa\nbb\ncc\ndd\nee", 2, 3);
            assertTrue(result.truncated());
            // After line truncation: "aa\nbb" = 5 bytes > 3 maxBytes
            // After byte truncation: "aa" = 2 bytes
            assertEquals("bytes", result.truncatedBy());
            assertEquals(1, result.outputLines());
        }
    }
}
