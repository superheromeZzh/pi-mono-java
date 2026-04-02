package com.huawei.hicampus.mate.matecampusclaw.codingagent.diff;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class DiffViewerTest {

    @Nested
    class WordDiff {

        @Test
        void identicalLinesNoHighlight() {
            String result = DiffViewer.highlightWordDiff("hello world", "hello world", "\033[31m");
            assertEquals("hello world", result);
        }

        @Test
        void singleWordChange() {
            String result = DiffViewer.highlightWordDiff("hello world", "hello earth", "\033[31m");
            // "world" should be highlighted with INVERSE since it differs from "earth"
            assertTrue(result.contains("\033[7m"), "Should contain INVERSE code");
            assertTrue(result.contains("world"), "Should contain the word 'world'");
        }

        @Test
        void emptyLines() {
            assertEquals("", DiffViewer.highlightWordDiff("", "anything", "\033[31m"));
            assertEquals("", DiffViewer.highlightWordDiff(null, "anything", "\033[31m"));
            assertEquals("hello", DiffViewer.highlightWordDiff("hello", "", "\033[31m"));
        }

        @Test
        void allDifferent() {
            String result = DiffViewer.highlightWordDiff("foo bar", "baz qux", "\033[32m");
            assertTrue(result.contains("\033[7m"), "Should contain INVERSE highlighting");
        }
    }

    @Nested
    class LineDiff {

        @Test
        void identicalTexts() {
            var lines = DiffViewer.diff("hello\nworld", "hello\nworld");
            assertEquals(2, lines.size());
            assertEquals(DiffViewer.LineType.SAME, lines.get(0).type());
            assertEquals(DiffViewer.LineType.SAME, lines.get(1).type());
        }

        @Test
        void addedLine() {
            var lines = DiffViewer.diff("hello", "hello\nworld");
            assertTrue(lines.stream().anyMatch(l -> l.type() == DiffViewer.LineType.ADDED));
        }

        @Test
        void removedLine() {
            var lines = DiffViewer.diff("hello\nworld", "hello");
            assertTrue(lines.stream().anyMatch(l -> l.type() == DiffViewer.LineType.REMOVED));
        }
    }

    @Nested
    class FormatUnified {

        @Test
        void includesFileHeader() {
            var lines = DiffViewer.diff("old", "new");
            String output = DiffViewer.formatUnified(lines, "test.java");
            assertTrue(output.contains("test.java"));
        }

        @Test
        void modifiedLineHasInverseHighlighting() {
            // Manually create a MODIFIED diff line to test intra-line highlighting
            var lines = java.util.List.of(
                    new DiffViewer.DiffLine(DiffViewer.LineType.MODIFIED, 1, 1, "return foo;", "return bar;")
            );
            String output = DiffViewer.formatUnified(lines, "test.java");
            // The output should contain inverse highlighting for the changed word
            assertTrue(output.contains("\033[7m"), "Modified line should have intra-line highlighting");
        }
    }

    @Nested
    class Summary {

        @Test
        void countChanges() {
            var lines = DiffViewer.diff("a\nb\nc", "a\nx\nc\nd");
            var summary = DiffViewer.summarize(lines);
            assertTrue(summary.added() > 0 || summary.removed() > 0);
            assertTrue(summary.unchanged() > 0);
        }
    }
}
