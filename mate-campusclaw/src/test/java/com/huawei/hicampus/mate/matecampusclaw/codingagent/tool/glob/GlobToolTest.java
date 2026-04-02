package com.huawei.hicampus.mate.matecampusclaw.codingagent.tool.glob;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Instant;
import java.util.Map;

import com.huawei.hicampus.mate.matecampusclaw.agent.tool.AgentToolResult;
import com.huawei.hicampus.mate.matecampusclaw.ai.types.TextContent;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class GlobToolTest {

    @TempDir
    Path tempDir;

    GlobTool globTool;

    @BeforeEach
    void setUp() {
        globTool = new GlobTool(tempDir);
    }

    private String extractText(AgentToolResult result) {
        return ((TextContent) result.content().get(0)).text();
    }

    private Path createFile(String relativePath) throws IOException {
        Path file = tempDir.resolve(relativePath);
        Files.createDirectories(file.getParent());
        Files.writeString(file, "content");
        return file;
    }

    // -------------------------------------------------------------------
    // Metadata
    // -------------------------------------------------------------------

    @Nested
    class Metadata {

        @Test
        void name() {
            assertEquals("glob", globTool.name());
        }

        @Test
        void label() {
            assertEquals("Glob", globTool.label());
        }

        @Test
        void parametersSchema() {
            var params = globTool.parameters();
            assertEquals("object", params.get("type").asText());
            assertTrue(params.get("properties").has("pattern"));
            assertTrue(params.get("properties").has("path"));
            assertEquals("pattern", params.get("required").get(0).asText());
        }
    }

    // -------------------------------------------------------------------
    // Basic matching
    // -------------------------------------------------------------------

    @Nested
    class BasicMatching {

        @Test
        void matchesByExtension() throws Exception {
            createFile("foo.java");
            createFile("bar.java");
            createFile("baz.txt");

            var result = globTool.execute("c1", Map.of("pattern", "*.java"), null, null);

            String text = extractText(result);
            assertTrue(text.contains("foo.java"));
            assertTrue(text.contains("bar.java"));
            assertFalse(text.contains("baz.txt"));
        }

        @Test
        void recursiveGlob() throws Exception {
            createFile("src/Main.java");
            createFile("src/sub/Helper.java");
            createFile("README.md");

            var result = globTool.execute("c2", Map.of("pattern", "**/*.java"), null, null);

            String text = extractText(result);
            assertTrue(text.contains("Main.java"));
            assertTrue(text.contains("Helper.java"));
            assertFalse(text.contains("README.md"));
        }

        @Test
        void noMatchesReturnsMessage() throws Exception {
            createFile("test.txt");

            var result = globTool.execute("c3", Map.of("pattern", "*.xyz"), null, null);

            assertEquals("No files matched.", extractText(result));
        }

        @Test
        void exactFilename() throws Exception {
            createFile("Makefile");
            createFile("other.txt");

            var result = globTool.execute("c4", Map.of("pattern", "Makefile"), null, null);

            String text = extractText(result);
            assertTrue(text.contains("Makefile"));
            assertFalse(text.contains("other.txt"));
        }
    }

    // -------------------------------------------------------------------
    // Directory exclusion
    // -------------------------------------------------------------------

    @Nested
    class DirectoryExclusion {

        @Test
        void excludesGitDirectory() throws Exception {
            createFile(".git/config");
            createFile("src/code.java");

            var result = globTool.execute("c5", Map.of("pattern", "**/*"), null, null);

            String text = extractText(result);
            assertFalse(text.contains(".git"));
            assertTrue(text.contains("code.java"));
        }

        @Test
        void excludesNodeModules() throws Exception {
            createFile("node_modules/pkg/index.js");
            createFile("src/app.js");

            var result = globTool.execute("c6", Map.of("pattern", "**/*.js"), null, null);

            String text = extractText(result);
            assertFalse(text.contains("node_modules"));
            assertTrue(text.contains("app.js"));
        }

        @Test
        void excludesBuildDirs() throws Exception {
            createFile("build/output.class");
            createFile("target/app.jar");
            createFile("dist/bundle.js");
            createFile("src/App.java");

            var result = globTool.execute("c7", Map.of("pattern", "**/*"), null, null);

            String text = extractText(result);
            assertFalse(text.contains("build"));
            assertFalse(text.contains("target"));
            assertFalse(text.contains("dist"));
            assertTrue(text.contains("App.java"));
        }

        @Test
        void excludesHiddenDirectories() throws Exception {
            createFile(".idea/workspace.xml");
            createFile(".vscode/settings.json");
            createFile("src/main.py");

            var result = globTool.execute("c8", Map.of("pattern", "**/*"), null, null);

            String text = extractText(result);
            assertFalse(text.contains(".idea"));
            assertFalse(text.contains(".vscode"));
            assertTrue(text.contains("main.py"));
        }
    }

    // -------------------------------------------------------------------
    // Sort by modification time
    // -------------------------------------------------------------------

    @Nested
    class SortOrder {

        @Test
        void sortsByModificationTimeDescending() throws Exception {
            Path older = createFile("older.txt");
            Path newer = createFile("newer.txt");

            // Set explicit modification times
            Files.setLastModifiedTime(older, FileTime.from(Instant.parse("2020-01-01T00:00:00Z")));
            Files.setLastModifiedTime(newer, FileTime.from(Instant.parse("2025-01-01T00:00:00Z")));

            var result = globTool.execute("c9", Map.of("pattern", "*.txt"), null, null);

            String text = extractText(result);
            int newerIdx = text.indexOf("newer.txt");
            int olderIdx = text.indexOf("older.txt");
            assertTrue(newerIdx >= 0 && olderIdx >= 0);
            assertTrue(newerIdx < olderIdx, "Newer file should appear before older file");
        }
    }

    // -------------------------------------------------------------------
    // Custom path
    // -------------------------------------------------------------------

    @Nested
    class CustomPath {

        @Test
        void searchesWithinSubdirectory() throws Exception {
            createFile("src/a.java");
            createFile("test/b.java");

            var result = globTool.execute("c10",
                    Map.of("pattern", "*.java", "path", "src"), null, null);

            String text = extractText(result);
            assertTrue(text.contains("a.java"));
            assertFalse(text.contains("b.java"));
        }

        @Test
        void pathTraversalReturnsError() throws Exception {
            var result = globTool.execute("c11",
                    Map.of("pattern", "*.txt", "path", "../../etc"), null, null);

            assertTrue(extractText(result).contains("Error"));
        }

        @Test
        void nonDirectoryReturnsError() throws Exception {
            createFile("file.txt");

            var result = globTool.execute("c12",
                    Map.of("pattern", "*.txt", "path", "file.txt"), null, null);

            assertTrue(extractText(result).contains("not a directory"));
        }
    }

    // -------------------------------------------------------------------
    // Error handling
    // -------------------------------------------------------------------

    @Nested
    class ErrorHandling {

        @Test
        void missingPatternReturnsError() throws Exception {
            var result = globTool.execute("c13", Map.of(), null, null);
            assertTrue(extractText(result).contains("Error"));
        }

        @Test
        void emptyPatternReturnsError() throws Exception {
            var result = globTool.execute("c14", Map.of("pattern", ""), null, null);
            assertTrue(extractText(result).contains("Error"));
        }
    }

    // -------------------------------------------------------------------
    // Result limit
    // -------------------------------------------------------------------

    @Nested
    class ResultLimit {

        @Test
        void capsResults() throws Exception {
            for (int i = 0; i < GlobTool.MAX_RESULTS + 50; i++) {
                createFile("files/file" + i + ".txt");
            }

            var result = globTool.execute("c15", Map.of("pattern", "**/*.txt"), null, null);

            String text = extractText(result);
            long lineCount = text.lines().count();
            assertTrue(lineCount <= GlobTool.MAX_RESULTS);
        }
    }
}
