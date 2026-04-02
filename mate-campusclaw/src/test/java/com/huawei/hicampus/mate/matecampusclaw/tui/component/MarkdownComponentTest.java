package com.huawei.hicampus.mate.matecampusclaw.tui.component;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.function.UnaryOperator;

import com.huawei.hicampus.mate.matecampusclaw.tui.ansi.AnsiUtils;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class MarkdownComponentTest {

    // Use an identity theme for most tests so we can assert on raw content
    // without ANSI codes getting in the way.
    private MarkdownTheme plainTheme;
    private MarkdownTheme defaultTheme;

    @BeforeEach
    void setUp() {
        plainTheme = MarkdownTheme.builder()
                .heading1(UnaryOperator.identity())
                .heading2(UnaryOperator.identity())
                .heading3(UnaryOperator.identity())
                .bold(UnaryOperator.identity())
                .italic(UnaryOperator.identity())
                .code(UnaryOperator.identity())
                .codeBlock(UnaryOperator.identity())
                .codeBlockBorder(UnaryOperator.identity())
                .link(UnaryOperator.identity())
                .linkUrl(UnaryOperator.identity())
                .listBullet(UnaryOperator.identity())
                .hr(UnaryOperator.identity())
                .build();
        defaultTheme = MarkdownTheme.defaultTheme();
    }

    // -------------------------------------------------------------------
    // Empty / null content
    // -------------------------------------------------------------------

    @Nested
    class EmptyContent {

        @Test
        void emptyStringReturnsNoLines() {
            var md = new MarkdownComponent("", plainTheme);
            assertTrue(md.render(80).isEmpty());
        }

        @Test
        void nullContentReturnsNoLines() {
            var md = new MarkdownComponent(null, plainTheme);
            assertTrue(md.render(80).isEmpty());
        }

        @Test
        void defaultConstructor() {
            var md = new MarkdownComponent();
            assertTrue(md.render(80).isEmpty());
        }
    }

    // -------------------------------------------------------------------
    // Headings
    // -------------------------------------------------------------------

    @Nested
    class Headings {

        @Test
        void h1() {
            var md = new MarkdownComponent("# Title", plainTheme);
            List<String> lines = md.render(80);
            assertEquals(1, lines.size());
            assertEquals("Title", lines.get(0));
        }

        @Test
        void h2() {
            var md = new MarkdownComponent("## Subtitle", plainTheme);
            List<String> lines = md.render(80);
            assertEquals(1, lines.size());
            assertEquals("Subtitle", lines.get(0));
        }

        @Test
        void h3() {
            var md = new MarkdownComponent("### Section", plainTheme);
            List<String> lines = md.render(80);
            assertEquals(1, lines.size());
            assertEquals("Section", lines.get(0));
        }

        @Test
        void headingWithDefaultThemeHasAnsi() {
            var md = new MarkdownComponent("# Hello", defaultTheme);
            List<String> lines = md.render(80);
            assertEquals(1, lines.size());
            // Should contain ANSI codes (bold+cyan+underline from default theme)
            assertTrue(lines.get(0).contains("\033["));
            // But visible text is "Hello"
            assertEquals(5, AnsiUtils.visibleWidth(lines.get(0)));
        }

        @Test
        void headingWrapsOnNarrowWidth() {
            var md = new MarkdownComponent("# A very long heading text", plainTheme);
            List<String> lines = md.render(10);
            assertTrue(lines.size() > 1);
        }
    }

    // -------------------------------------------------------------------
    // Code blocks
    // -------------------------------------------------------------------

    @Nested
    class CodeBlocks {

        @Test
        void simpleCodeBlock() {
            String input = "```\nfoo\nbar\n```";
            var md = new MarkdownComponent(input, plainTheme);
            List<String> lines = md.render(80);
            // Should have: fence, 2 code lines, fence = 4 lines
            assertEquals(4, lines.size());
            assertEquals("```", lines.get(0));
            assertTrue(lines.get(1).contains("foo"));
            assertTrue(lines.get(2).contains("bar"));
            assertEquals("```", lines.get(3));
        }

        @Test
        void codeBlockWithLanguage() {
            String input = "```java\nint x = 1;\n```";
            var md = new MarkdownComponent(input, plainTheme);
            List<String> lines = md.render(80);
            assertEquals(3, lines.size());
            assertEquals("``` java", lines.get(0));
            assertTrue(lines.get(1).contains("int x = 1;"));
        }

        @Test
        void codeBlockWithDefaultThemeHasBackground() {
            String input = "```\ncode\n```";
            var md = new MarkdownComponent(input, defaultTheme);
            List<String> lines = md.render(80);
            // Code line should contain background ANSI code (48;5;236)
            assertTrue(lines.get(1).contains("\033[48;5;236m"));
        }

        @Test
        void codeBlockPreservesIndentation() {
            String input = "```\n  indented\n    more\n```";
            var md = new MarkdownComponent(input, plainTheme);
            List<String> lines = md.render(80);
            // Code lines have 2-char prefix + original indentation
            assertTrue(lines.get(1).contains("  indented"));
            assertTrue(lines.get(2).contains("    more"));
        }

        @Test
        void unclosedCodeBlockConsumedToEnd() {
            String input = "```\ncode without closing";
            var md = new MarkdownComponent(input, plainTheme);
            List<String> lines = md.render(80);
            // opening fence + code line + closing fence = 3
            assertEquals(3, lines.size());
        }
    }

    // -------------------------------------------------------------------
    // Inline code
    // -------------------------------------------------------------------

    @Nested
    class InlineCode {

        @Test
        void simpleInlineCode() {
            var md = new MarkdownComponent("Use `foo` here", plainTheme);
            List<String> lines = md.render(80);
            assertEquals(1, lines.size());
            assertTrue(lines.get(0).contains("foo"));
        }

        @Test
        void inlineCodeWithDefaultTheme() {
            var md = new MarkdownComponent("Run `npm install`", defaultTheme);
            List<String> lines = md.render(80);
            // Should contain accent color ANSI code (#8abeb7)
            assertTrue(lines.get(0).contains("\033[38;2;138;190;183m"));
        }

        @Test
        void multipleInlineCodes() {
            var md = new MarkdownComponent("`a` and `b`", plainTheme);
            List<String> lines = md.render(80);
            assertTrue(lines.get(0).contains("a"));
            assertTrue(lines.get(0).contains("b"));
        }
    }

    // -------------------------------------------------------------------
    // Bold and italic
    // -------------------------------------------------------------------

    @Nested
    class BoldAndItalic {

        @Test
        void boldText() {
            var md = new MarkdownComponent("This is **bold** text", plainTheme);
            List<String> lines = md.render(80);
            assertTrue(lines.get(0).contains("bold"));
        }

        @Test
        void italicText() {
            var md = new MarkdownComponent("This is *italic* text", plainTheme);
            List<String> lines = md.render(80);
            assertTrue(lines.get(0).contains("italic"));
        }

        @Test
        void boldWithDefaultTheme() {
            var md = new MarkdownComponent("**strong**", defaultTheme);
            List<String> lines = md.render(80);
            // Should contain bold ANSI code
            assertTrue(lines.get(0).contains("\033[1m"));
        }

        @Test
        void italicWithDefaultTheme() {
            var md = new MarkdownComponent("*emphasis*", defaultTheme);
            List<String> lines = md.render(80);
            // Should contain italic ANSI code
            assertTrue(lines.get(0).contains("\033[3m"));
        }

        @Test
        void boldAndItalicInSameLine() {
            var md = new MarkdownComponent("**bold** and *italic*", plainTheme);
            List<String> lines = md.render(80);
            assertTrue(lines.get(0).contains("bold"));
            assertTrue(lines.get(0).contains("italic"));
        }
    }

    // -------------------------------------------------------------------
    // Lists
    // -------------------------------------------------------------------

    @Nested
    class UnorderedLists {

        @Test
        void simpleDashList() {
            String input = "- item one\n- item two";
            var md = new MarkdownComponent(input, plainTheme);
            List<String> lines = md.render(80);
            assertEquals(2, lines.size());
            assertTrue(lines.get(0).contains("-"));
            assertTrue(lines.get(0).contains("item one"));
            assertTrue(lines.get(1).contains("item two"));
        }

        @Test
        void asteriskList() {
            String input = "* alpha\n* beta";
            var md = new MarkdownComponent(input, plainTheme);
            List<String> lines = md.render(80);
            assertEquals(2, lines.size());
            assertTrue(lines.get(0).contains("alpha"));
        }

        @Test
        void nestedUnorderedList() {
            String input = "- outer\n  - inner";
            var md = new MarkdownComponent(input, plainTheme);
            List<String> lines = md.render(80);
            assertEquals(2, lines.size());
            // Inner item should have more leading whitespace
            int outerIndent = countLeadingSpaces(lines.get(0));
            int innerIndent = countLeadingSpaces(lines.get(1));
            assertTrue(innerIndent > outerIndent,
                    "Inner should be more indented: outer=" + outerIndent + " inner=" + innerIndent);
        }

        @Test
        void listItemWraps() {
            String input = "- This is a very long list item that should wrap to the next line";
            var md = new MarkdownComponent(input, plainTheme);
            List<String> lines = md.render(20);
            assertTrue(lines.size() > 1);
            // Continuation lines should be indented to align with first line content
            assertTrue(lines.get(1).startsWith("  "));
        }
    }

    @Nested
    class OrderedLists {

        @Test
        void simpleOrderedList() {
            String input = "1. first\n2. second\n3. third";
            var md = new MarkdownComponent(input, plainTheme);
            List<String> lines = md.render(80);
            assertEquals(3, lines.size());
            assertTrue(lines.get(0).contains("1."));
            assertTrue(lines.get(0).contains("first"));
            assertTrue(lines.get(1).contains("2."));
        }

        @Test
        void orderedListWithInlineFormatting() {
            String input = "1. **bold item**\n2. *italic item*";
            var md = new MarkdownComponent(input, plainTheme);
            List<String> lines = md.render(80);
            assertTrue(lines.get(0).contains("bold item"));
            assertTrue(lines.get(1).contains("italic item"));
        }
    }

    // -------------------------------------------------------------------
    // Links
    // -------------------------------------------------------------------

    @Nested
    class Links {

        @Test
        void simpleLink() {
            var md = new MarkdownComponent("[Google](https://google.com)", plainTheme);
            List<String> lines = md.render(80);
            assertEquals(1, lines.size());
            assertTrue(lines.get(0).contains("Google"));
            assertTrue(lines.get(0).contains("https://google.com"));
        }

        @Test
        void linkWithDefaultTheme() {
            var md = new MarkdownComponent("[click](http://example.com)", defaultTheme);
            List<String> lines = md.render(80);
            // Should contain underline ANSI for link text
            assertTrue(lines.get(0).contains("\033[4m"));
        }

        @Test
        void linkInParagraph() {
            var md = new MarkdownComponent("Visit [our site](http://ex.com) today", plainTheme);
            List<String> lines = md.render(80);
            assertTrue(lines.get(0).contains("our site"));
            assertTrue(lines.get(0).contains("http://ex.com"));
        }
    }

    // -------------------------------------------------------------------
    // Horizontal rules
    // -------------------------------------------------------------------

    @Nested
    class HorizontalRules {

        @Test
        void dashRule() {
            var md = new MarkdownComponent("---", plainTheme);
            List<String> lines = md.render(40);
            assertEquals(1, lines.size());
            assertEquals("─".repeat(40), lines.get(0));
        }

        @Test
        void asteriskRule() {
            var md = new MarkdownComponent("***", plainTheme);
            List<String> lines = md.render(20);
            assertEquals(1, lines.size());
            assertEquals("─".repeat(20), lines.get(0));
        }

        @Test
        void underscoreRule() {
            var md = new MarkdownComponent("___", plainTheme);
            List<String> lines = md.render(30);
            assertEquals(1, lines.size());
        }
    }

    // -------------------------------------------------------------------
    // Paragraphs
    // -------------------------------------------------------------------

    @Nested
    class Paragraphs {

        @Test
        void simpleParagraph() {
            var md = new MarkdownComponent("Hello world", plainTheme);
            List<String> lines = md.render(80);
            assertEquals(1, lines.size());
            assertEquals("Hello world", lines.get(0));
        }

        @Test
        void paragraphWraps() {
            var md = new MarkdownComponent("This is a longer paragraph that should wrap", plainTheme);
            List<String> lines = md.render(15);
            assertTrue(lines.size() > 1);
        }

        @Test
        void consecutiveLinesJoinedAsParagraph() {
            String input = "first line\nsecond line";
            var md = new MarkdownComponent(input, plainTheme);
            List<String> lines = md.render(80);
            // Should be joined as a single paragraph
            assertEquals(1, lines.size());
            assertTrue(lines.get(0).contains("first line"));
            assertTrue(lines.get(0).contains("second line"));
        }

        @Test
        void blankLineSeparatesParagraphs() {
            String input = "para one\n\npara two";
            var md = new MarkdownComponent(input, plainTheme);
            List<String> lines = md.render(80);
            // para one, empty line, para two
            assertEquals(3, lines.size());
            assertEquals("para one", lines.get(0));
            assertEquals("", lines.get(1));
            assertEquals("para two", lines.get(2));
        }
    }

    // -------------------------------------------------------------------
    // Mixed content
    // -------------------------------------------------------------------

    @Nested
    class MixedContent {

        @Test
        void headingThenParagraph() {
            String input = "# Title\n\nSome text.";
            var md = new MarkdownComponent(input, plainTheme);
            List<String> lines = md.render(80);
            assertEquals(3, lines.size());
            assertEquals("Title", lines.get(0));
            assertEquals("", lines.get(1));
            assertEquals("Some text.", lines.get(2));
        }

        @Test
        void headingCodeParagraph() {
            String input = "## Setup\n\n```\nnpm install\n```\n\nDone.";
            var md = new MarkdownComponent(input, plainTheme);
            List<String> lines = md.render(80);
            assertTrue(lines.size() >= 5);
            assertEquals("Setup", lines.get(0));
            assertTrue(lines.stream().anyMatch(l -> l.contains("npm install")));
            assertTrue(lines.stream().anyMatch(l -> l.equals("Done.")));
        }

        @Test
        void listThenParagraph() {
            String input = "- a\n- b\n\nafter list";
            var md = new MarkdownComponent(input, plainTheme);
            List<String> lines = md.render(80);
            assertTrue(lines.get(0).contains("a"));
            assertTrue(lines.get(1).contains("b"));
            assertEquals("after list", lines.get(lines.size() - 1));
        }

        @Test
        void inlineFormattingInHeading() {
            String input = "# **Bold** heading";
            var md = new MarkdownComponent(input, plainTheme);
            List<String> lines = md.render(80);
            assertTrue(lines.get(0).contains("Bold"));
        }

        @Test
        void allElementsCombined() {
            String input = """
                    # Main Title

                    A paragraph with **bold** and *italic* and `code`.

                    ## Section

                    - item one
                    - item two

                    1. first
                    2. second

                    ```python
                    print("hello")
                    ```

                    ---

                    Visit [here](http://example.com).""";
            var md = new MarkdownComponent(input, plainTheme);
            List<String> lines = md.render(60);
            assertFalse(lines.isEmpty());
            // Verify key elements are present
            assertTrue(lines.stream().anyMatch(l -> l.contains("Main Title")));
            assertTrue(lines.stream().anyMatch(l -> l.contains("bold")));
            assertTrue(lines.stream().anyMatch(l -> l.contains("italic")));
            assertTrue(lines.stream().anyMatch(l -> l.contains("code")));
            assertTrue(lines.stream().anyMatch(l -> l.contains("item one")));
            assertTrue(lines.stream().anyMatch(l -> l.contains("1.")));
            assertTrue(lines.stream().anyMatch(l -> l.contains("print")));
            assertTrue(lines.stream().anyMatch(l -> l.contains("─")));
            assertTrue(lines.stream().anyMatch(l -> l.contains("here")));
        }
    }

    // -------------------------------------------------------------------
    // Caching
    // -------------------------------------------------------------------

    @Nested
    class Caching {

        @Test
        void cachedOnSameInput() {
            var md = new MarkdownComponent("# Hello", plainTheme);
            List<String> first = md.render(80);
            List<String> second = md.render(80);
            assertSame(first, second);
        }

        @Test
        void cacheInvalidatedOnWidthChange() {
            var md = new MarkdownComponent("# Hello", plainTheme);
            List<String> first = md.render(80);
            List<String> second = md.render(40);
            assertNotSame(first, second);
        }

        @Test
        void setContentInvalidatesCache() {
            var md = new MarkdownComponent("# Old", plainTheme);
            md.render(80);
            md.setContent("# New");
            List<String> lines = md.render(80);
            assertTrue(lines.get(0).contains("New"));
        }

        @Test
        void invalidateForcesFreshRender() {
            var md = new MarkdownComponent("# Test", plainTheme);
            List<String> first = md.render(80);
            md.invalidate();
            List<String> second = md.render(80);
            assertNotSame(first, second);
        }
    }

    // -------------------------------------------------------------------
    // getContent / setContent
    // -------------------------------------------------------------------

    @Nested
    class ContentAccessors {

        @Test
        void getContentReturnsCurrentContent() {
            var md = new MarkdownComponent("hello");
            assertEquals("hello", md.getContent());
        }

        @Test
        void setContentNullBecomesEmpty() {
            var md = new MarkdownComponent("hello");
            md.setContent(null);
            assertEquals("", md.getContent());
        }
    }

    // -------------------------------------------------------------------
    // Chinese / wide characters
    // -------------------------------------------------------------------

    @Nested
    class WideCharacters {

        @Test
        void chineseHeading() {
            var md = new MarkdownComponent("# 你好世界", plainTheme);
            List<String> lines = md.render(20);
            assertEquals(1, lines.size());
            assertTrue(lines.get(0).contains("你好世界"));
        }

        @Test
        void chineseInList() {
            var md = new MarkdownComponent("- 项目一\n- 项目二", plainTheme);
            List<String> lines = md.render(20);
            assertEquals(2, lines.size());
            assertTrue(lines.get(0).contains("项目一"));
        }
    }

    // -------------------------------------------------------------------
    // Helper
    // -------------------------------------------------------------------

    private int countLeadingSpaces(String s) {
        int count = 0;
        for (int i = 0; i < s.length(); i++) {
            if (s.charAt(i) == ' ') count++;
            else break;
        }
        return count;
    }
}
