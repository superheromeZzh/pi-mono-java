package com.huawei.hicampus.mate.matecampusclaw.codingagent.tool.edit;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class DiffUtilsTest {

    @Nested
    class ComputeUnifiedDiff {

        @Test
        void identicalTextsProduceEmptyDiff() {
            String diff = DiffUtils.computeUnifiedDiff("hello\n", "hello\n", "test.txt");
            assertEquals("", diff);
        }

        @Test
        void singleLineChange() {
            String diff = DiffUtils.computeUnifiedDiff("aaa\nbbb\nccc\n", "aaa\nxxx\nccc\n", "test.txt");
            assertTrue(diff.contains("--- a/test.txt"));
            assertTrue(diff.contains("+++ b/test.txt"));
            assertTrue(diff.contains("-bbb"));
            assertTrue(diff.contains("+xxx"));
        }

        @Test
        void addedLines() {
            String diff = DiffUtils.computeUnifiedDiff("aaa\nccc\n", "aaa\nbbb\nccc\n", "test.txt");
            assertTrue(diff.contains("+bbb"));
        }

        @Test
        void removedLines() {
            String diff = DiffUtils.computeUnifiedDiff("aaa\nbbb\nccc\n", "aaa\nccc\n", "test.txt");
            assertTrue(diff.contains("-bbb"));
        }

        @Test
        void includesContextLines() {
            String diff = DiffUtils.computeUnifiedDiff(
                    "1\n2\n3\n4\n5\n6\n7\n8\n",
                    "1\n2\n3\nX\n5\n6\n7\n8\n",
                    "test.txt"
            );
            // Context lines should have space prefix
            assertTrue(diff.contains(" 3"));
            assertTrue(diff.contains(" 5"));
        }

        @Test
        void includesHunkHeader() {
            String diff = DiffUtils.computeUnifiedDiff("aaa\n", "bbb\n", "f.txt");
            assertTrue(diff.contains("@@"));
        }
    }

    @Nested
    class FindFirstChangedLine {

        @Test
        void noChange() {
            assertNull(DiffUtils.findFirstChangedLine("aaa\nbbb\n", "aaa\nbbb\n"));
        }

        @Test
        void firstLineChanged() {
            assertEquals(1, DiffUtils.findFirstChangedLine("aaa\n", "bbb\n"));
        }

        @Test
        void secondLineChanged() {
            assertEquals(2, DiffUtils.findFirstChangedLine("aaa\nbbb\n", "aaa\nccc\n"));
        }

        @Test
        void lineAdded() {
            assertEquals(2, DiffUtils.findFirstChangedLine("aaa\n", "aaa\nbbb\n"));
        }
    }
}
