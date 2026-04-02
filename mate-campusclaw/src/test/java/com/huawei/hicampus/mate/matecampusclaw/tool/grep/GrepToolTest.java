package com.huawei.hicampus.mate.matecampusclaw.codingagent.tool.grep;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import com.huawei.hicampus.mate.matecampusclaw.agent.tool.AgentToolResult;
import com.huawei.hicampus.mate.matecampusclaw.ai.types.TextContent;
import com.huawei.hicampus.mate.matecampusclaw.codingagent.tool.bash.BashExecutionResult;
import com.huawei.hicampus.mate.matecampusclaw.codingagent.tool.bash.BashExecutor;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class GrepToolTest {

    @Mock
    BashExecutor bashExecutor;

    @TempDir
    Path tempDir;

    GrepTool grepTool;

    @BeforeEach
    void setUp() {
        grepTool = new GrepTool(bashExecutor, tempDir);
        // Force Java fallback for deterministic tests
        grepTool.setRgAvailable(false);
    }

    private String extractText(AgentToolResult result) {
        return ((TextContent) result.content().get(0)).text();
    }

    private void createFile(String relativePath, String content) throws IOException {
        Path file = tempDir.resolve(relativePath);
        Files.createDirectories(file.getParent());
        Files.writeString(file, content);
    }

    // -------------------------------------------------------------------
    // Metadata
    // -------------------------------------------------------------------

    @Nested
    class Metadata {

        @Test
        void name() {
            assertEquals("grep", grepTool.name());
        }

        @Test
        void label() {
            assertEquals("Grep", grepTool.label());
        }

        @Test
        void parametersSchema() {
            var params = grepTool.parameters();
            assertEquals("object", params.get("type").asText());
            assertTrue(params.get("properties").has("pattern"));
            assertTrue(params.get("properties").has("path"));
            assertTrue(params.get("properties").has("glob"));
            assertTrue(params.get("properties").has("type"));
            assertEquals("pattern", params.get("required").get(0).asText());
        }
    }

    // -------------------------------------------------------------------
    // Java fallback: basic search
    // -------------------------------------------------------------------

    @Nested
    class JavaFallbackBasic {

        @Test
        void findsMatchInFile() throws Exception {
            createFile("test.txt", "hello world\nfoo bar\nhello again\n");

            var result = grepTool.execute("c1", Map.of("pattern", "hello"), null, null);

            String text = extractText(result);
            assertTrue(text.contains("test.txt:1:hello world"));
            assertTrue(text.contains("test.txt:3:hello again"));
        }

        @Test
        void regexPattern() throws Exception {
            createFile("code.txt", "int x = 42;\nString s = \"test\";\nint y = 99;\n");

            var result = grepTool.execute("c2", Map.of("pattern", "int \\w+ = \\d+"), null, null);

            String text = extractText(result);
            assertTrue(text.contains("code.txt:1:"));
            assertTrue(text.contains("code.txt:3:"));
            assertFalse(text.contains(":2:"));
        }

        @Test
        void noMatchesReturnsMessage() throws Exception {
            createFile("empty.txt", "nothing relevant here\n");

            var result = grepTool.execute("c3", Map.of("pattern", "zzzzz"), null, null);

            assertEquals("No matches found.", extractText(result));
        }

        @Test
        void searchesRecursively() throws Exception {
            createFile("a/deep.txt", "target line\n");
            createFile("b/c/deeper.txt", "target again\n");

            var result = grepTool.execute("c4", Map.of("pattern", "target"), null, null);

            String text = extractText(result);
            assertTrue(text.contains("deep.txt"));
            assertTrue(text.contains("deeper.txt"));
        }

        @Test
        void skipsDotDirectories() throws Exception {
            createFile(".hidden/secret.txt", "target\n");
            createFile("visible.txt", "target\n");

            var result = grepTool.execute("c5", Map.of("pattern", "target"), null, null);

            String text = extractText(result);
            assertTrue(text.contains("visible.txt"));
            assertFalse(text.contains("secret.txt"));
        }

        @Test
        void searchSingleFile() throws Exception {
            createFile("a.txt", "match here\n");
            createFile("b.txt", "match here too\n");

            var result = grepTool.execute("c6",
                    Map.of("pattern", "match", "path", "a.txt"), null, null);

            String text = extractText(result);
            assertTrue(text.contains("a.txt"));
            assertFalse(text.contains("b.txt"));
        }
    }

    // -------------------------------------------------------------------
    // Java fallback: glob filter
    // -------------------------------------------------------------------

    @Nested
    class JavaFallbackGlob {

        @Test
        void globFiltersFiles() throws Exception {
            createFile("code.java", "public class Foo {}\n");
            createFile("code.txt", "public class Foo {}\n");

            var result = grepTool.execute("c7",
                    Map.of("pattern", "class", "glob", "*.java"), null, null);

            String text = extractText(result);
            assertTrue(text.contains("code.java"));
            assertFalse(text.contains("code.txt"));
        }
    }

    // -------------------------------------------------------------------
    // Java fallback: type filter
    // -------------------------------------------------------------------

    @Nested
    class JavaFallbackType {

        @Test
        void typeFiltersFiles() throws Exception {
            createFile("Main.java", "public static void main\n");
            createFile("script.py", "def main():\n");

            var result = grepTool.execute("c8",
                    Map.of("pattern", "main", "type", "java"), null, null);

            String text = extractText(result);
            assertTrue(text.contains("Main.java"));
            assertFalse(text.contains("script.py"));
        }

        @Test
        void unknownTypeReturnsError() throws Exception {
            var result = grepTool.execute("c9",
                    Map.of("pattern", "test", "type", "unknown_lang"), null, null);

            assertTrue(extractText(result).contains("unknown file type"));
        }
    }

    // -------------------------------------------------------------------
    // Error handling
    // -------------------------------------------------------------------

    @Nested
    class ErrorHandling {

        @Test
        void missingPatternReturnsError() throws Exception {
            var result = grepTool.execute("c10", Map.of(), null, null);
            assertTrue(extractText(result).contains("Error"));
        }

        @Test
        void emptyPatternReturnsError() throws Exception {
            var result = grepTool.execute("c11", Map.of("pattern", ""), null, null);
            assertTrue(extractText(result).contains("Error"));
        }

        @Test
        void invalidRegexReturnsError() throws Exception {
            var result = grepTool.execute("c12", Map.of("pattern", "[invalid"), null, null);
            assertTrue(extractText(result).contains("invalid regex"));
        }

        @Test
        void pathTraversalReturnsError() throws Exception {
            var result = grepTool.execute("c13",
                    Map.of("pattern", "test", "path", "../../etc"), null, null);
            assertTrue(extractText(result).contains("Error"));
        }
    }

    // -------------------------------------------------------------------
    // ripgrep path
    // -------------------------------------------------------------------

    @Nested
    class RipgrepPath {

        @Test
        void usesRgWhenAvailable() throws Exception {
            grepTool.setRgAvailable(true);
            when(bashExecutor.execute(any(), any(), any()))
                    .thenReturn(new BashExecutionResult(0, "file.txt:1:match\n", ""));

            var result = grepTool.execute("c14", Map.of("pattern", "match"), null, null);

            assertTrue(extractText(result).contains("file.txt:1:match"));
        }

        @Test
        void rgNoMatchesReturnsMessage() throws Exception {
            grepTool.setRgAvailable(true);
            when(bashExecutor.execute(any(), any(), any()))
                    .thenReturn(new BashExecutionResult(1, "", ""));

            var result = grepTool.execute("c15", Map.of("pattern", "nope"), null, null);

            assertEquals("No matches found.", extractText(result));
        }

        @Test
        void rgErrorReturnsErrorMessage() throws Exception {
            grepTool.setRgAvailable(true);
            when(bashExecutor.execute(any(), any(), any()))
                    .thenReturn(new BashExecutionResult(2, "", "rg: bad regex\n"));

            var result = grepTool.execute("c16", Map.of("pattern", "[bad"), null, null);

            assertTrue(extractText(result).contains("Grep error"));
        }
    }

    // -------------------------------------------------------------------
    // Result limit
    // -------------------------------------------------------------------

    @Nested
    class ResultLimit {

        @Test
        void limitsResults() throws Exception {
            var sb = new StringBuilder();
            for (int i = 0; i < GrepTool.MAX_RESULTS + 100; i++) {
                sb.append("match_line\n");
            }
            createFile("big.txt", sb.toString());

            var result = grepTool.execute("c17", Map.of("pattern", "match_line"), null, null);

            String text = extractText(result);
            long lineCount = text.lines().count();
            assertTrue(lineCount <= GrepTool.MAX_RESULTS);
        }
    }
}
