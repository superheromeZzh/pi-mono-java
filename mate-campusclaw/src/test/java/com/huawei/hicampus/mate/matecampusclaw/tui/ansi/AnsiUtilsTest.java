package com.huawei.hicampus.mate.matecampusclaw.tui.ansi;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.function.UnaryOperator;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class AnsiUtilsTest {

    // Common ANSI codes for tests
    private static final String RED = "\033[31m";
    private static final String GREEN = "\033[32m";
    private static final String BLUE = "\033[34m";
    private static final String BOLD = "\033[1m";
    private static final String RESET = "\033[0m";
    private static final String BG_RED = "\033[41m";
    private static final String UNDERLINE = "\033[4m";

    // -------------------------------------------------------------------
    // visibleWidth
    // -------------------------------------------------------------------

    @Nested
    class VisibleWidth {

        @Test
        void emptyString() {
            assertEquals(0, AnsiUtils.visibleWidth(""));
        }

        @Test
        void nullString() {
            assertEquals(0, AnsiUtils.visibleWidth(null));
        }

        @Test
        void pureAscii() {
            assertEquals(5, AnsiUtils.visibleWidth("hello"));
        }

        @Test
        void asciiWithSpaces() {
            assertEquals(11, AnsiUtils.visibleWidth("hello world"));
        }

        @Test
        void chineseCharacters() {
            // Each Chinese character is 2 columns wide
            assertEquals(6, AnsiUtils.visibleWidth("你好世"));
        }

        @Test
        void mixedAsciiAndChinese() {
            // "hi" = 2, "你好" = 4, total = 6
            assertEquals(6, AnsiUtils.visibleWidth("hi你好"));
        }

        @Test
        void withAnsiCodes() {
            // ANSI codes should not count toward width
            assertEquals(5, AnsiUtils.visibleWidth(RED + "hello" + RESET));
        }

        @Test
        void onlyAnsiCodes() {
            assertEquals(0, AnsiUtils.visibleWidth(RED + RESET));
        }

        @Test
        void chineseWithAnsiCodes() {
            assertEquals(4, AnsiUtils.visibleWidth(RED + "你好" + RESET));
        }

        @Test
        void multipleAnsiCodesInterspersed() {
            // "he" + "ll" + "o" with colors
            String text = RED + "he" + GREEN + "ll" + BLUE + "o" + RESET;
            assertEquals(5, AnsiUtils.visibleWidth(text));
        }

        @Test
        void tabsCountAsThreeSpaces() {
            assertEquals(3, AnsiUtils.visibleWidth("\t"));
            assertEquals(8, AnsiUtils.visibleWidth("hello\t"));
        }

        @Test
        void color256() {
            String color256 = "\033[38;5;240m";
            assertEquals(5, AnsiUtils.visibleWidth(color256 + "hello" + RESET));
        }

        @Test
        void colorRgb() {
            String colorRgb = "\033[38;2;128;0;255m";
            assertEquals(5, AnsiUtils.visibleWidth(colorRgb + "hello" + RESET));
        }

        @Test
        void japaneseKatakana() {
            // Katakana characters are fullwidth
            assertEquals(6, AnsiUtils.visibleWidth("カタカ"));
        }

        @Test
        void fullwidthAscii() {
            // Fullwidth latin A = 0xFF21, width 2
            assertEquals(2, AnsiUtils.visibleWidth("\uFF21"));
        }

        @Test
        void oscSequenceIgnored() {
            // OSC hyperlink sequence
            String hyperlink = "\033]8;;http://example.com\007link\033]8;;\007";
            assertEquals(4, AnsiUtils.visibleWidth(hyperlink));
        }

        @Test
        void apcSequenceIgnored() {
            String apc = "\033_pi:c\007";
            assertEquals(0, AnsiUtils.visibleWidth(apc));
        }
    }

    // -------------------------------------------------------------------
    // sliceByColumn
    // -------------------------------------------------------------------

    @Nested
    class SliceByColumn {

        @Test
        void emptyString() {
            assertEquals("", AnsiUtils.sliceByColumn("", 0, 5));
        }

        @Test
        void nullString() {
            assertEquals("", AnsiUtils.sliceByColumn(null, 0, 5));
        }

        @Test
        void fullSlice() {
            assertEquals("hello", AnsiUtils.sliceByColumn("hello", 0, 5));
        }

        @Test
        void partialSlice() {
            assertEquals("ell", AnsiUtils.sliceByColumn("hello", 1, 4));
        }

        @Test
        void sliceWithAnsiCodes() {
            String text = RED + "hello" + RESET;
            // Slice columns 1-4: should get "ell" with RED code preserved
            String result = AnsiUtils.sliceByColumn(text, 1, 4);
            // Should contain RED and "ell"
            assertTrue(result.contains("ell"));
            assertTrue(result.contains(RED));
        }

        @Test
        void sliceChineseCharacters() {
            // "你好世" — each char is 2 cols wide
            // col 0-1 = 你, col 2-3 = 好, col 4-5 = 世
            String result = AnsiUtils.sliceByColumn("你好世", 0, 4);
            assertEquals("你好", result);
        }

        @Test
        void sliceMidChineseCharacter() {
            // Start at col 2 (beginning of 好), end at col 6
            String result = AnsiUtils.sliceByColumn("你好世", 2, 6);
            assertEquals("好世", result);
        }

        @Test
        void slicePreservesAnsiBeforeRange() {
            // ANSI code appears before the visible range
            String text = RED + "hello" + RESET;
            // Slice cols 2-5 should preserve the RED code since it was set before
            String result = AnsiUtils.sliceByColumn(text, 2, 5);
            assertTrue(result.contains(RED));
            assertTrue(result.contains("llo"));
        }

        @Test
        void sliceBeyondLength() {
            assertEquals("hello", AnsiUtils.sliceByColumn("hello", 0, 100));
        }

        @Test
        void invalidRange() {
            assertEquals("", AnsiUtils.sliceByColumn("hello", 5, 3));
        }

        @Test
        void zeroWidthSlice() {
            assertEquals("", AnsiUtils.sliceByColumn("hello", 2, 2));
        }

        @Test
        void mixedContentSlice() {
            String text = "AB" + RED + "你好" + RESET + "CD";
            // A=0, B=1, 你=2-3, 好=4-5, C=6, D=7
            String result = AnsiUtils.sliceByColumn(text, 1, 6);
            assertTrue(result.contains("B"));
            assertTrue(result.contains("你"));
            assertTrue(result.contains("好"));
        }
    }

    // -------------------------------------------------------------------
    // extractSegments
    // -------------------------------------------------------------------

    @Nested
    class ExtractSegments {

        @Test
        void emptyString() {
            assertTrue(AnsiUtils.extractSegments("").isEmpty());
        }

        @Test
        void nullString() {
            assertTrue(AnsiUtils.extractSegments(null).isEmpty());
        }

        @Test
        void pureText() {
            List<AnsiSegment> segments = AnsiUtils.extractSegments("hello");
            assertEquals(1, segments.size());
            assertEquals("hello", segments.get(0).text());
            assertFalse(segments.get(0).isAnsi());
        }

        @Test
        void pureAnsi() {
            List<AnsiSegment> segments = AnsiUtils.extractSegments(RED);
            assertEquals(1, segments.size());
            assertEquals(RED, segments.get(0).text());
            assertTrue(segments.get(0).isAnsi());
        }

        @Test
        void ansiThenText() {
            List<AnsiSegment> segments = AnsiUtils.extractSegments(RED + "hello");
            assertEquals(2, segments.size());
            assertEquals(RED, segments.get(0).text());
            assertTrue(segments.get(0).isAnsi());
            assertEquals("hello", segments.get(1).text());
            assertFalse(segments.get(1).isAnsi());
        }

        @Test
        void textAnsiTextAnsi() {
            String text = "hello" + RED + "world" + RESET;
            List<AnsiSegment> segments = AnsiUtils.extractSegments(text);
            assertEquals(4, segments.size());
            assertEquals("hello", segments.get(0).text());
            assertFalse(segments.get(0).isAnsi());
            assertEquals(RED, segments.get(1).text());
            assertTrue(segments.get(1).isAnsi());
            assertEquals("world", segments.get(2).text());
            assertFalse(segments.get(2).isAnsi());
            assertEquals(RESET, segments.get(3).text());
            assertTrue(segments.get(3).isAnsi());
        }

        @Test
        void consecutiveAnsiCodes() {
            String text = RED + BOLD + "hi";
            List<AnsiSegment> segments = AnsiUtils.extractSegments(text);
            assertEquals(3, segments.size());
            assertTrue(segments.get(0).isAnsi());
            assertTrue(segments.get(1).isAnsi());
            assertFalse(segments.get(2).isAnsi());
        }

        @Test
        void chineseWithAnsi() {
            String text = RED + "你好" + RESET;
            List<AnsiSegment> segments = AnsiUtils.extractSegments(text);
            assertEquals(3, segments.size());
            assertEquals(RED, segments.get(0).text());
            assertEquals("你好", segments.get(1).text());
            assertEquals(RESET, segments.get(2).text());
        }

        @Test
        void oscSequence() {
            String osc = "\033]8;;http://example.com\007";
            String text = osc + "link" + "\033]8;;\007";
            List<AnsiSegment> segments = AnsiUtils.extractSegments(text);
            assertEquals(3, segments.size());
            assertTrue(segments.get(0).isAnsi());
            assertEquals("link", segments.get(1).text());
            assertTrue(segments.get(2).isAnsi());
        }
    }

    // -------------------------------------------------------------------
    // wrapTextWithAnsi
    // -------------------------------------------------------------------

    @Nested
    class WrapTextWithAnsi {

        @Test
        void emptyString() {
            assertEquals(List.of(""), AnsiUtils.wrapTextWithAnsi("", 10));
        }

        @Test
        void nullString() {
            assertEquals(List.of(""), AnsiUtils.wrapTextWithAnsi(null, 10));
        }

        @Test
        void fitsWithinWidth() {
            assertEquals(List.of("hello"), AnsiUtils.wrapTextWithAnsi("hello", 10));
        }

        @Test
        void exactFit() {
            assertEquals(List.of("hello"), AnsiUtils.wrapTextWithAnsi("hello", 5));
        }

        @Test
        void simpleWrap() {
            List<String> result = AnsiUtils.wrapTextWithAnsi("hello world", 5);
            assertEquals(2, result.size());
            assertEquals("hello", result.get(0));
            assertEquals("world", result.get(1));
        }

        @Test
        void wrapPreservesAnsiAcrossLines() {
            String text = RED + "hello world" + RESET;
            List<String> result = AnsiUtils.wrapTextWithAnsi(text, 5);
            assertEquals(2, result.size());
            // First line should start with RED
            assertTrue(result.get(0).startsWith(RED));
            // Second line should also have RED prefix (restored from tracker)
            assertTrue(result.get(1).contains(RED));
        }

        @Test
        void wrapLongWord() {
            List<String> result = AnsiUtils.wrapTextWithAnsi("abcdefghij", 5);
            assertEquals(2, result.size());
            assertEquals("abcde", result.get(0));
            assertEquals("fghij", result.get(1));
        }

        @Test
        void wrapChineseText() {
            // Each Chinese char is width 2
            // "你好世界" = 8 columns
            List<String> result = AnsiUtils.wrapTextWithAnsi("你好世界", 4);
            assertEquals(2, result.size());
            assertEquals("你好", result.get(0));
            assertEquals("世界", result.get(1));
        }

        @Test
        void preservesNewlines() {
            List<String> result = AnsiUtils.wrapTextWithAnsi("abc\ndef", 10);
            assertEquals(2, result.size());
            assertEquals("abc", result.get(0));
            assertEquals("def", result.get(1));
        }

        @Test
        void newlinesAndWrapping() {
            List<String> result = AnsiUtils.wrapTextWithAnsi("hello world\nfoo", 5);
            assertEquals(3, result.size());
            assertEquals("hello", result.get(0));
            assertEquals("world", result.get(1));
            assertEquals("foo", result.get(2));
        }

        @Test
        void ansiStyleCarriesAcrossNewlines() {
            String text = RED + "hi" + "\n" + "there" + RESET;
            List<String> result = AnsiUtils.wrapTextWithAnsi(text, 10);
            assertEquals(2, result.size());
            assertTrue(result.get(0).startsWith(RED));
            // Second line should inherit RED from first line
            assertTrue(result.get(1).contains(RED));
        }

        @Test
        void multipleSpacesAtWrapPoint() {
            // Leading whitespace on new line after wrap should be skipped
            List<String> result = AnsiUtils.wrapTextWithAnsi("abc    def", 5);
            assertEquals(2, result.size());
        }

        @Test
        void singleCharWidth() {
            List<String> result = AnsiUtils.wrapTextWithAnsi("abcde", 1);
            assertEquals(5, result.size());
        }

        @Test
        void widthOfTwo() {
            // Chinese char width 2 at maxWidth 2 should fit
            List<String> result = AnsiUtils.wrapTextWithAnsi("你好", 2);
            assertEquals(2, result.size());
            assertEquals("你", result.get(0));
            assertEquals("好", result.get(1));
        }
    }

    // -------------------------------------------------------------------
    // applyBackground
    // -------------------------------------------------------------------

    @Nested
    class ApplyBackground {

        @Test
        void paddingToWidth() {
            UnaryOperator<String> bgFn = s -> BG_RED + s + RESET;
            String result = AnsiUtils.applyBackground("hi", 5, bgFn);
            // Should contain BG_RED, "hi", 3 spaces padding, and RESET
            assertTrue(result.startsWith(BG_RED));
            assertTrue(result.endsWith(RESET));
            // The visible content should be "hi" + 3 spaces = 5 chars
            String inner = result.substring(BG_RED.length(), result.length() - RESET.length());
            assertEquals("hi   ", inner);
        }

        @Test
        void noPaddingNeeded() {
            UnaryOperator<String> bgFn = s -> BG_RED + s + RESET;
            String result = AnsiUtils.applyBackground("hello", 5, bgFn);
            String inner = result.substring(BG_RED.length(), result.length() - RESET.length());
            assertEquals("hello", inner);
        }

        @Test
        void textExceedsWidth() {
            UnaryOperator<String> bgFn = s -> BG_RED + s + RESET;
            // If text is wider than width, no padding is added
            String result = AnsiUtils.applyBackground("hello world", 5, bgFn);
            String inner = result.substring(BG_RED.length(), result.length() - RESET.length());
            assertEquals("hello world", inner);
        }

        @Test
        void emptyLine() {
            UnaryOperator<String> bgFn = s -> BG_RED + s + RESET;
            String result = AnsiUtils.applyBackground("", 5, bgFn);
            String inner = result.substring(BG_RED.length(), result.length() - RESET.length());
            assertEquals("     ", inner);
        }

        @Test
        void chineseTextPadding() {
            UnaryOperator<String> bgFn = s -> BG_RED + s + RESET;
            // "你好" = 4 columns, width 6, needs 2 spaces padding
            String result = AnsiUtils.applyBackground("你好", 6, bgFn);
            String inner = result.substring(BG_RED.length(), result.length() - RESET.length());
            assertEquals("你好  ", inner);
        }

        @Test
        void ansiTextPadding() {
            UnaryOperator<String> bgFn = s -> BG_RED + s + RESET;
            // RED + "hi" + RESET = visible width 2, pad to 5
            String input = RED + "hi" + RESET;
            String result = AnsiUtils.applyBackground(input, 5, bgFn);
            // Total visible width of inner content should be 5
            String inner = result.substring(BG_RED.length(), result.length() - RESET.length());
            assertEquals(5, AnsiUtils.visibleWidth(inner));
        }
    }

    // -------------------------------------------------------------------
    // Edge cases and integration
    // -------------------------------------------------------------------

    @Nested
    class EdgeCases {

        @Test
        void sliceAndWidthConsistency() {
            String text = RED + "hello" + GREEN + "world" + RESET;
            String sliced = AnsiUtils.sliceByColumn(text, 0, 10);
            // Visible width of sliced result should be 10
            assertEquals(10, AnsiUtils.visibleWidth(sliced));
        }

        @Test
        void extractSegmentsRoundTrip() {
            String text = RED + "hello" + RESET + "world";
            List<AnsiSegment> segments = AnsiUtils.extractSegments(text);
            StringBuilder rebuilt = new StringBuilder();
            for (AnsiSegment seg : segments) {
                rebuilt.append(seg.text());
            }
            assertEquals(text, rebuilt.toString());
        }

        @Test
        void wrapThenMeasureWidth() {
            String text = "这是一段包含中文和English混合的文本";
            List<String> wrapped = AnsiUtils.wrapTextWithAnsi(text, 10);
            for (String line : wrapped) {
                assertTrue(AnsiUtils.visibleWidth(line) <= 10,
                        "Line exceeds max width: '" + line + "' width=" + AnsiUtils.visibleWidth(line));
            }
        }

        @Test
        void deeplyNestedAnsiCodes() {
            String text = BOLD + RED + UNDERLINE + "styled" + RESET;
            assertEquals(6, AnsiUtils.visibleWidth(text));
            List<AnsiSegment> segments = AnsiUtils.extractSegments(text);
            // Should have BOLD, RED, UNDERLINE as separate ANSI segments, "styled" as text, RESET as ANSI
            assertEquals(5, segments.size());
        }

        @Test
        void color256InSegments() {
            String color256fg = "\033[38;5;196m";
            String color256bg = "\033[48;5;21m";
            String text = color256fg + color256bg + "colored" + RESET;
            List<AnsiSegment> segments = AnsiUtils.extractSegments(text);
            assertEquals(4, segments.size());
            assertTrue(segments.get(0).isAnsi());
            assertEquals(color256fg, segments.get(0).text());
        }

        @Test
        void emptyLinesInNewlineText() {
            List<String> result = AnsiUtils.wrapTextWithAnsi("a\n\nb", 10);
            assertEquals(3, result.size());
            assertEquals("a", result.get(0));
            assertEquals("", result.get(1));
            assertEquals("b", result.get(2));
        }

        @Test
        void onlyNewlines() {
            List<String> result = AnsiUtils.wrapTextWithAnsi("\n\n", 10);
            assertEquals(3, result.size());
            assertEquals("", result.get(0));
            assertEquals("", result.get(1));
            assertEquals("", result.get(2));
        }
    }
}
