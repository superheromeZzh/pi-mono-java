package com.huawei.hicampus.mate.matecampusclaw.codingagent.tool.edit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Map;

import com.huawei.hicampus.mate.matecampusclaw.agent.tool.AgentToolResult;
import com.huawei.hicampus.mate.matecampusclaw.ai.types.TextContent;
import com.huawei.hicampus.mate.matecampusclaw.codingagent.tool.ops.EditOperations;
import com.huawei.hicampus.mate.matecampusclaw.codingagent.util.FileMutationQueue;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class EditToolTest {

    @Mock
    EditOperations editOperations;

    @TempDir
    Path tempDir;

    EditTool editTool;
    FileMutationQueue mutationQueue;

    @BeforeEach
    void setUp() {
        mutationQueue = new FileMutationQueue();
        editTool = new EditTool(editOperations, mutationQueue, tempDir);
    }

    private String extractText(AgentToolResult result) {
        return ((TextContent) result.content().get(0)).text();
    }

    private void stubFile(String name, String content) throws IOException {
        Path path = tempDir.resolve(name);
        when(editOperations.exists(path)).thenReturn(true);
        when(editOperations.readFile(path)).thenReturn(content.getBytes(StandardCharsets.UTF_8));
    }

    // -------------------------------------------------------------------
    // Metadata
    // -------------------------------------------------------------------

    @Nested
    class Metadata {

        @Test
        void name() {
            assertEquals("edit", editTool.name());
        }

        @Test
        void label() {
            assertEquals("Edit", editTool.label());
        }

        @Test
        void parametersSchema() {
            var params = editTool.parameters();
            assertEquals("object", params.get("type").asText());
            assertTrue(params.get("properties").has("path"));
            assertTrue(params.get("properties").has("oldText"));
            assertTrue(params.get("properties").has("newText"));
            assertTrue(params.get("properties").has("edits"));
            var required = params.get("required");
            assertEquals(1, required.size()); // only path is required (supports edits[] mode)
            assertEquals("path", required.get(0).asText());
        }
    }

    // -------------------------------------------------------------------
    // Exact match
    // -------------------------------------------------------------------

    @Nested
    class ExactMatch {

        @Test
        void replacesExactMatch() throws Exception {
            stubFile("test.txt", "hello world\n");

            var result = editTool.execute("c1",
                    Map.of("path", "test.txt", "oldText", "hello", "newText", "goodbye"),
                    null, null);

            String text = extractText(result);
            assertTrue(text.contains("Applied edit"));
            assertFalse(text.contains("fuzzy"));

            verify(editOperations).writeFile(eq(tempDir.resolve("test.txt")), eq("goodbye world\n"));
        }

        @Test
        void replacesMultiLineExactMatch() throws Exception {
            stubFile("multi.txt", "line1\nline2\nline3\n");

            var result = editTool.execute("c2",
                    Map.of("path", "multi.txt", "oldText", "line1\nline2", "newText", "replaced"),
                    null, null);

            assertTrue(extractText(result).contains("Applied edit"));
            verify(editOperations).writeFile(any(), eq("replaced\nline3\n"));
        }

        @Test
        void includesDiffInDetails() throws Exception {
            stubFile("diff.txt", "aaa\nbbb\nccc\n");

            var result = editTool.execute("c3",
                    Map.of("path", "diff.txt", "oldText", "bbb", "newText", "xxx"),
                    null, null);

            var details = (EditToolDetails) result.details();
            assertNotNull(details.diff());
            assertTrue(details.diff().contains("-bbb"));
            assertTrue(details.diff().contains("+xxx"));
        }

        @Test
        void includesFirstChangedLineInDetails() throws Exception {
            stubFile("line.txt", "aaa\nbbb\nccc\n");

            var result = editTool.execute("c4",
                    Map.of("path", "line.txt", "oldText", "bbb", "newText", "xxx"),
                    null, null);

            var details = (EditToolDetails) result.details();
            assertEquals(2, details.firstChangedLine());
        }
    }

    // -------------------------------------------------------------------
    // Multiple occurrences
    // -------------------------------------------------------------------

    @Nested
    class MultipleOccurrences {

        @Test
        void rejectsMultipleMatches() throws Exception {
            stubFile("dup.txt", "foo bar foo baz foo\n");

            var result = editTool.execute("c5",
                    Map.of("path", "dup.txt", "oldText", "foo", "newText", "qux"),
                    null, null);

            String text = extractText(result);
            assertTrue(text.contains("3 occurrences"));
            verify(editOperations, never()).writeFile(any(), any());
        }

        @Test
        void rejectsTwoOccurrences() throws Exception {
            stubFile("two.txt", "abc\nabc\n");

            var result = editTool.execute("c6",
                    Map.of("path", "two.txt", "oldText", "abc", "newText", "xyz"),
                    null, null);

            assertTrue(extractText(result).contains("2 occurrences"));
        }
    }

    // -------------------------------------------------------------------
    // Fuzzy matching
    // -------------------------------------------------------------------

    @Nested
    class FuzzyMatching {

        @Test
        void fallsBackToFuzzyOnWhitespaceDifference() throws Exception {
            stubFile("fuzzy.txt", "  hello   world  \n");

            var result = editTool.execute("c7",
                    Map.of("path", "fuzzy.txt", "oldText", "hello world", "newText", "goodbye"),
                    null, null);

            String text = extractText(result);
            assertTrue(text.contains("fuzzy match"));
            verify(editOperations).writeFile(any(), eq("goodbye\n"));
        }

        @Test
        void fuzzyMatchesMultiLineWithWhitespace() throws Exception {
            stubFile("fuzzy-multi.txt", "  line1  \n  line2  \nline3\n");

            var result = editTool.execute("c8",
                    Map.of("path", "fuzzy-multi.txt", "oldText", "line1\nline2", "newText", "replaced"),
                    null, null);

            assertTrue(extractText(result).contains("fuzzy match"));
            verify(editOperations).writeFile(any(), eq("replaced\nline3\n"));
        }

        @Test
        void returnsErrorWhenNoFuzzyMatchFound() throws Exception {
            stubFile("nomatch.txt", "hello world\n");

            var result = editTool.execute("c9",
                    Map.of("path", "nomatch.txt", "oldText", "completely different", "newText", "x"),
                    null, null);

            assertTrue(extractText(result).contains("not found"));
            verify(editOperations, never()).writeFile(any(), any());
        }
    }

    // -------------------------------------------------------------------
    // Error handling
    // -------------------------------------------------------------------

    @Nested
    class ErrorHandling {

        @Test
        void missingPathReturnsError() throws Exception {
            var result = editTool.execute("c10",
                    Map.of("oldText", "a", "newText", "b"), null, null);
            assertTrue(extractText(result).contains("Error"));
        }

        @Test
        void fileNotFoundReturnsError() throws Exception {
            when(editOperations.exists(any())).thenReturn(false);

            var result = editTool.execute("c11",
                    Map.of("path", "missing.txt", "oldText", "a", "newText", "b"), null, null);
            assertTrue(extractText(result).contains("not found"));
        }

        @Test
        void pathTraversalReturnsError() throws Exception {
            var result = editTool.execute("c12",
                    Map.of("path", "../../etc/passwd", "oldText", "a", "newText", "b"), null, null);
            assertTrue(extractText(result).contains("Error"));
        }

        @Test
        void missingOldTextReturnsError() throws Exception {
            var result = editTool.execute("c13",
                    Map.of("path", "test.txt", "newText", "b"), null, null);
            assertTrue(extractText(result).contains("Error"));
        }

        @Test
        void missingNewTextReturnsError() throws Exception {
            var result = editTool.execute("c14",
                    Map.of("path", "test.txt", "oldText", "a"), null, null);
            assertTrue(extractText(result).contains("Error"));
        }
    }

    // -------------------------------------------------------------------
    // FileMutationQueue integration
    // -------------------------------------------------------------------

    @Nested
    class MutationQueueIntegration {

        @Test
        void usesFileMutationQueue() throws Exception {
            stubFile("queued.txt", "content\n");

            editTool.execute("c15",
                    Map.of("path", "queued.txt", "oldText", "content", "newText", "updated"),
                    null, null);

            // Verify the write happened (proves the mutation queue executed the action)
            verify(editOperations).writeFile(eq(tempDir.resolve("queued.txt")), eq("updated\n"));
        }
    }
}
