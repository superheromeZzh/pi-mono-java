package com.huawei.hicampus.mate.matecampusclaw.codingagent.tool.write;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Map;

import com.huawei.hicampus.mate.matecampusclaw.agent.tool.AgentToolResult;
import com.huawei.hicampus.mate.matecampusclaw.ai.types.TextContent;
import com.huawei.hicampus.mate.matecampusclaw.codingagent.tool.ops.WriteOperations;
import com.huawei.hicampus.mate.matecampusclaw.codingagent.util.FileMutationQueue;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class WriteToolTest {

    @Mock
    WriteOperations writeOperations;

    @TempDir
    Path tempDir;

    WriteTool writeTool;

    @BeforeEach
    void setUp() {
        writeTool = new WriteTool(writeOperations, new FileMutationQueue(), tempDir);
    }

    private String extractText(AgentToolResult result) {
        return ((TextContent) result.content().get(0)).text();
    }

    // -------------------------------------------------------------------
    // Metadata
    // -------------------------------------------------------------------

    @Nested
    class Metadata {

        @Test
        void name() {
            assertEquals("write", writeTool.name());
        }

        @Test
        void label() {
            assertEquals("Write", writeTool.label());
        }

        @Test
        void parametersSchema() {
            var params = writeTool.parameters();
            assertEquals("object", params.get("type").asText());
            assertTrue(params.get("properties").has("path"));
            assertTrue(params.get("properties").has("content"));
            var required = params.get("required");
            assertEquals(2, required.size());
        }
    }

    // -------------------------------------------------------------------
    // Normal write
    // -------------------------------------------------------------------

    @Nested
    class NormalWrite {

        @Test
        void writesFileContent() throws Exception {
            var result = writeTool.execute("c1",
                    Map.of("path", "test.txt", "content", "hello world"),
                    null, null);

            assertTrue(extractText(result).contains("Wrote test.txt"));
            verify(writeOperations).writeFile(eq(tempDir.resolve("test.txt")), eq("hello world"));
        }

        @Test
        void createsParentDirectories() throws Exception {
            writeTool.execute("c2",
                    Map.of("path", "a/b/c/deep.txt", "content", "deep"),
                    null, null);

            verify(writeOperations).mkdir(tempDir.resolve("a/b/c"));
            verify(writeOperations).writeFile(eq(tempDir.resolve("a/b/c/deep.txt")), eq("deep"));
        }

        @Test
        void mkdirBeforeWrite() throws Exception {
            writeTool.execute("c3",
                    Map.of("path", "sub/file.txt", "content", "data"),
                    null, null);

            InOrder inOrder = inOrder(writeOperations);
            inOrder.verify(writeOperations).mkdir(tempDir.resolve("sub"));
            inOrder.verify(writeOperations).writeFile(any(), any());
        }

        @Test
        void writesEmptyContent() throws Exception {
            var result = writeTool.execute("c4",
                    Map.of("path", "empty.txt", "content", ""),
                    null, null);

            assertTrue(extractText(result).contains("Wrote"));
            verify(writeOperations).writeFile(eq(tempDir.resolve("empty.txt")), eq(""));
        }

        @Test
        void writesUtf8Content() throws Exception {
            writeTool.execute("c5",
                    Map.of("path", "utf8.txt", "content", "你好世界"),
                    null, null);

            verify(writeOperations).writeFile(any(), eq("你好世界"));
        }
    }

    // -------------------------------------------------------------------
    // Error handling
    // -------------------------------------------------------------------

    @Nested
    class ErrorHandling {

        @Test
        void missingPathReturnsError() throws Exception {
            var result = writeTool.execute("c6",
                    Map.of("content", "data"), null, null);
            assertTrue(extractText(result).contains("Error"));
        }

        @Test
        void blankPathReturnsError() throws Exception {
            var result = writeTool.execute("c7",
                    Map.of("path", "  ", "content", "data"), null, null);
            assertTrue(extractText(result).contains("Error"));
        }

        @Test
        void missingContentReturnsError() throws Exception {
            var result = writeTool.execute("c8",
                    Map.of("path", "test.txt"), null, null);
            assertTrue(extractText(result).contains("Error"));
        }

        @Test
        void pathTraversalReturnsError() throws Exception {
            var result = writeTool.execute("c9",
                    Map.of("path", "../../etc/passwd", "content", "hack"),
                    null, null);
            assertTrue(extractText(result).contains("Error"));
            verify(writeOperations, never()).writeFile(any(), any());
        }

        @Test
        void ioExceptionPropagates() throws IOException {
            doThrow(new IOException("disk full")).when(writeOperations).writeFile(any(), any());

            assertThrows(Exception.class, () ->
                    writeTool.execute("c10",
                            Map.of("path", "fail.txt", "content", "data"),
                            null, null));
        }
    }

    // -------------------------------------------------------------------
    // FileMutationQueue integration
    // -------------------------------------------------------------------

    @Nested
    class MutationQueue {

        @Test
        void writeGoesThoughMutationQueue() throws Exception {
            writeTool.execute("c11",
                    Map.of("path", "queued.txt", "content", "queued"),
                    null, null);

            // Write happened means the queue executed the callable
            verify(writeOperations).writeFile(eq(tempDir.resolve("queued.txt")), eq("queued"));
        }
    }
}
