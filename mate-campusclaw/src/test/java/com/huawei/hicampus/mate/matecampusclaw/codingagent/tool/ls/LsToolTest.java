/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.hicampus.mate.matecampusclaw.codingagent.tool.ls;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.huawei.hicampus.mate.matecampusclaw.agent.tool.AgentToolResult;
import com.huawei.hicampus.mate.matecampusclaw.ai.types.TextContent;
import com.huawei.hicampus.mate.matecampusclaw.codingagent.tool.ops.LsOperations;
import com.huawei.hicampus.mate.matecampusclaw.codingagent.tool.ops.LsOperations.LsEntry;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class LsToolTest {

    @Mock
    LsOperations lsOperations;

    private static String text(AgentToolResult r) {
        return ((TextContent) r.content().get(0)).text();
    }

    @Nested
    class Metadata {

        @Test
        void identity(@TempDir Path tmp) {
            LsTool tool = new LsTool(lsOperations, tmp);
            assertThat(tool.name()).isEqualTo("ls");
            assertThat(tool.label()).isEqualTo("Ls");
            assertThat(tool.description()).isNotBlank();
            assertThat(tool.parameters().get("required").get(0).asText()).isEqualTo("path");
        }
    }

    @Nested
    class InvalidInput {

        @Test
        void missingPathRejected(@TempDir Path tmp) throws Exception {
            LsTool tool = new LsTool(lsOperations, tmp);
            AgentToolResult result = tool.execute("id", Map.of(), null, null);
            assertThat(text(result)).contains("path is required");
        }

        @Test
        void blankPathRejected(@TempDir Path tmp) throws Exception {
            LsTool tool = new LsTool(lsOperations, tmp);
            AgentToolResult result = tool.execute("id", Map.of("path", "   "), null, null);
            assertThat(text(result)).contains("path is required");
        }

        @Test
        void nonDirectoryRejected(@TempDir Path tmp) throws Exception {
            Path file = tmp.resolve("a.txt");
            Files.writeString(file, "hi");
            LsTool tool = new LsTool(lsOperations, tmp);
            AgentToolResult result = tool.execute("id", Map.of("path", "a.txt"), null, null);
            assertThat(text(result)).contains("not a directory");
        }
    }

    @Nested
    class Listing {

        @Test
        void emptyDirectoryMessage(@TempDir Path tmp) throws Exception {
            when(lsOperations.list(any(Path.class))).thenReturn(new ArrayList<>());
            LsTool tool = new LsTool(lsOperations, tmp);
            assertThat(text(tool.execute("id", Map.of("path", "."), null, null)))
                    .contains("empty directory");
        }

        @Test
        void formatsEntriesWithTypeFlags(@TempDir Path tmp) throws Exception {
            List<LsEntry> entries = new ArrayList<>(List.of(
                    new LsEntry("zzz.txt", "file", 123, Instant.parse("2025-01-01T00:00:00Z")),
                    new LsEntry("alpha", "directory", 4096, Instant.parse("2025-01-02T00:00:00Z")),
                    new LsEntry("link.so", "symlink", 0, Instant.parse("2025-01-03T00:00:00Z"))));
            when(lsOperations.list(any(Path.class))).thenReturn(entries);
            LsTool tool = new LsTool(lsOperations, tmp);
            String out = text(tool.execute("id", Map.of("path", "."), null, null));

            // Directories first
            int dirIdx = out.indexOf("alpha/");
            int fileIdx = out.indexOf("zzz.txt");
            int linkIdx = out.indexOf("link.so");
            assertThat(dirIdx).isLessThan(fileIdx).isLessThan(linkIdx);

            // Type flags
            assertThat(out).contains("drw-").contains("-rw-").contains("lrw-");
        }

        @Test
        void truncatesWhenOverMax(@TempDir Path tmp) throws Exception {
            List<LsEntry> entries = new ArrayList<>();
            for (int i = 0; i < 1500; i++) {
                entries.add(new LsEntry("f" + i + ".txt", "file", 1, Instant.EPOCH));
            }
            when(lsOperations.list(any(Path.class))).thenReturn(entries);
            LsTool tool = new LsTool(lsOperations, tmp);
            String out = text(tool.execute("id", Map.of("path", "."), null, null));
            assertThat(out).contains("(truncated to 1000 entries)");
        }

        @Test
        void ioErrorReported(@TempDir Path tmp) throws Exception {
            when(lsOperations.list(any(Path.class))).thenThrow(new IOException("disk down"));
            LsTool tool = new LsTool(lsOperations, tmp);
            assertThat(text(tool.execute("id", Map.of("path", "."), null, null)))
                    .contains("Error listing")
                    .contains("disk down");
        }
    }
}
