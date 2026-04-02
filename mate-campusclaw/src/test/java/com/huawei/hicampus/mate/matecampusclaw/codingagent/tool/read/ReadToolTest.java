package com.huawei.hicampus.mate.matecampusclaw.codingagent.tool.read;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Base64;
import java.util.Map;

import com.huawei.hicampus.mate.matecampusclaw.agent.tool.AgentToolResult;
import com.huawei.hicampus.mate.matecampusclaw.ai.types.ImageContent;
import com.huawei.hicampus.mate.matecampusclaw.ai.types.TextContent;
import com.huawei.hicampus.mate.matecampusclaw.codingagent.tool.ops.ReadOperations;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ReadToolTest {

    @Mock
    ReadOperations readOperations;

    @TempDir
    Path tempDir;

    ReadTool readTool;

    @BeforeEach
    void setUp() {
        readTool = new ReadTool(readOperations, tempDir);
    }

    private String extractText(AgentToolResult result) {
        return ((TextContent) result.content().get(0)).text();
    }

    private void stubFile(String name, String content) throws IOException {
        Path path = tempDir.resolve(name);
        when(readOperations.exists(path)).thenReturn(true);
        when(readOperations.detectMimeType(path)).thenReturn("text/plain");
        when(readOperations.readFile(path)).thenReturn(content.getBytes(StandardCharsets.UTF_8));
    }

    // -------------------------------------------------------------------
    // Metadata
    // -------------------------------------------------------------------

    @Nested
    class Metadata {

        @Test
        void name() {
            assertEquals("read", readTool.name());
        }

        @Test
        void label() {
            assertEquals("Read", readTool.label());
        }

        @Test
        void parametersSchema() {
            var params = readTool.parameters();
            assertEquals("object", params.get("type").asText());
            assertTrue(params.get("properties").has("path"));
            assertTrue(params.get("properties").has("offset"));
            assertTrue(params.get("properties").has("limit"));
            assertEquals("path", params.get("required").get(0).asText());
        }
    }

    // -------------------------------------------------------------------
    // Text file reading
    // -------------------------------------------------------------------

    @Nested
    class TextFileReading {

        @Test
        void readsFileWithLineNumbers() throws Exception {
            stubFile("test.txt", "line1\nline2\nline3\n");

            var result = readTool.execute("c1", Map.of("path", "test.txt"), null, null);

            String text = extractText(result);
            assertTrue(text.contains("1\tline1"));
            assertTrue(text.contains("2\tline2"));
            assertTrue(text.contains("3\tline3"));
        }

        @Test
        void singleLineFile() throws Exception {
            stubFile("single.txt", "hello");

            var result = readTool.execute("c2", Map.of("path", "single.txt"), null, null);

            String text = extractText(result);
            assertTrue(text.contains("1\thello"));
        }

        @Test
        void emptyFile() throws Exception {
            stubFile("empty.txt", "");

            var result = readTool.execute("c3", Map.of("path", "empty.txt"), null, null);

            String text = extractText(result);
            // Empty file still has one "line"
            assertNotNull(text);
        }

        @Test
        void noTruncationForSmallFile() throws Exception {
            stubFile("small.txt", "a\nb\nc\n");

            var result = readTool.execute("c4", Map.of("path", "small.txt"), null, null);

            var details = (ReadToolDetails) result.details();
            assertNull(details.truncation());
        }
    }

    // -------------------------------------------------------------------
    // Offset and limit
    // -------------------------------------------------------------------

    @Nested
    class OffsetAndLimit {

        @Test
        void offsetSkipsLines() throws Exception {
            stubFile("offset.txt", "line1\nline2\nline3\nline4\nline5\n");

            var result = readTool.execute("c5",
                    Map.of("path", "offset.txt", "offset", 3), null, null);

            String text = extractText(result);
            assertFalse(text.contains("1\tline1"));
            assertFalse(text.contains("2\tline2"));
            assertTrue(text.contains("3\tline3"));
            assertTrue(text.contains("4\tline4"));
            assertTrue(text.contains("5\tline5"));
        }

        @Test
        void limitRestrictsLines() throws Exception {
            stubFile("limit.txt", "line1\nline2\nline3\nline4\nline5\n");

            var result = readTool.execute("c6",
                    Map.of("path", "limit.txt", "limit", 2), null, null);

            String text = extractText(result);
            assertTrue(text.contains("1\tline1"));
            assertTrue(text.contains("2\tline2"));
            assertFalse(text.contains("3\tline3"));
        }

        @Test
        void offsetAndLimitCombined() throws Exception {
            stubFile("combo.txt", "line1\nline2\nline3\nline4\nline5\n");

            var result = readTool.execute("c7",
                    Map.of("path", "combo.txt", "offset", 2, "limit", 2), null, null);

            String text = extractText(result);
            assertFalse(text.contains("1\tline1"));
            assertTrue(text.contains("2\tline2"));
            assertTrue(text.contains("3\tline3"));
            assertFalse(text.contains("4\tline4"));
        }

        @Test
        void offsetBeyondFileReturnsEmpty() throws Exception {
            stubFile("short.txt", "line1\nline2\n");

            var result = readTool.execute("c8",
                    Map.of("path", "short.txt", "offset", 100), null, null);

            assertEquals("", extractText(result));
        }

        @Test
        void offsetAtZeroTreatedAsOne() throws Exception {
            stubFile("zero.txt", "line1\nline2\n");

            var result = readTool.execute("c9",
                    Map.of("path", "zero.txt", "offset", 0), null, null);

            String text = extractText(result);
            assertTrue(text.contains("1\tline1"));
        }
    }

    // -------------------------------------------------------------------
    // Image detection
    // -------------------------------------------------------------------

    @Nested
    class ImageDetection {

        @Test
        void pngFileReturnsImageContent() throws Exception {
            Path path = tempDir.resolve("photo.png");
            byte[] fakeImageData = {(byte) 0x89, 0x50, 0x4E, 0x47};
            when(readOperations.exists(path)).thenReturn(true);
            when(readOperations.detectMimeType(path)).thenReturn("image/png");
            when(readOperations.readFile(path)).thenReturn(fakeImageData);

            var result = readTool.execute("c10", Map.of("path", "photo.png"), null, null);

            assertEquals(1, result.content().size());
            assertInstanceOf(ImageContent.class, result.content().get(0));
            var img = (ImageContent) result.content().get(0);
            assertEquals("image/png", img.mimeType());
            assertEquals(Base64.getEncoder().encodeToString(fakeImageData), img.data());
        }

        @Test
        void jpegFileReturnsImageContent() throws Exception {
            Path path = tempDir.resolve("photo.jpg");
            byte[] fakeData = {(byte) 0xFF, (byte) 0xD8};
            when(readOperations.exists(path)).thenReturn(true);
            when(readOperations.detectMimeType(path)).thenReturn("image/jpeg");
            when(readOperations.readFile(path)).thenReturn(fakeData);

            var result = readTool.execute("c11", Map.of("path", "photo.jpg"), null, null);

            assertInstanceOf(ImageContent.class, result.content().get(0));
            assertEquals("image/jpeg", ((ImageContent) result.content().get(0)).mimeType());
        }

        @Test
        void textMimeTypeReturnsTextContent() throws Exception {
            stubFile("code.js", "console.log('hi');\n");

            var result = readTool.execute("c12", Map.of("path", "code.js"), null, null);

            assertInstanceOf(TextContent.class, result.content().get(0));
        }
    }

    // -------------------------------------------------------------------
    // Truncation
    // -------------------------------------------------------------------

    @Nested
    class Truncation {

        @Test
        void largeFileIsTruncated() throws Exception {
            var sb = new StringBuilder();
            for (int i = 0; i < ReadTool.DEFAULT_MAX_LINES + 100; i++) {
                sb.append("content line ").append(i).append('\n');
            }
            stubFile("large.txt", sb.toString());

            var result = readTool.execute("c13", Map.of("path", "large.txt"), null, null);

            var details = (ReadToolDetails) result.details();
            assertNotNull(details.truncation());
            assertTrue(details.truncation().truncated());
        }

        @Test
        void smallFileNotTruncated() throws Exception {
            stubFile("tiny.txt", "one\ntwo\nthree\n");

            var result = readTool.execute("c14", Map.of("path", "tiny.txt"), null, null);

            var details = (ReadToolDetails) result.details();
            assertNull(details.truncation());
        }
    }

    // -------------------------------------------------------------------
    // Error handling
    // -------------------------------------------------------------------

    @Nested
    class ErrorHandling {

        @Test
        void missingPathReturnsError() throws Exception {
            var result = readTool.execute("c15", Map.of(), null, null);
            assertTrue(extractText(result).contains("Error"));
        }

        @Test
        void blankPathReturnsError() throws Exception {
            var result = readTool.execute("c16", Map.of("path", "  "), null, null);
            assertTrue(extractText(result).contains("Error"));
        }

        @Test
        void fileNotFoundReturnsError() throws Exception {
            when(readOperations.exists(any())).thenReturn(false);

            var result = readTool.execute("c17", Map.of("path", "missing.txt"), null, null);
            assertTrue(extractText(result).contains("not found"));
        }

        @Test
        void pathTraversalReturnsError() throws Exception {
            var result = readTool.execute("c18", Map.of("path", "../../etc/passwd"), null, null);
            assertTrue(extractText(result).contains("Error"));
        }
    }
}
