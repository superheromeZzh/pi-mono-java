/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.hicampus.mate.matecampusclaw.codingagent.tool.editdiff;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import com.huawei.hicampus.mate.matecampusclaw.agent.tool.AgentToolResult;
import com.huawei.hicampus.mate.matecampusclaw.ai.types.TextContent;
import com.huawei.hicampus.mate.matecampusclaw.codingagent.tool.ops.EditOperations;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class EditDiffToolTest {

    @Mock
    EditOperations editOps;

    private static String text(AgentToolResult r) {
        return ((TextContent) r.content().get(0)).text();
    }

    @Nested
    class Metadata {

        @Test
        void identity() {
            EditDiffTool t = new EditDiffTool(editOps);
            assertThat(t.name()).isEqualTo("EditDiff");
            assertThat(t.label()).isEqualTo("EditDiff");
            assertThat(t.description()).isNotBlank();
            assertThat(t.parameters().get("required").get(0).asText()).isEqualTo("file_path");
        }
    }

    @Nested
    class InvalidInput {

        @Test
        void missingFilePathRejected() throws Exception {
            EditDiffTool t = new EditDiffTool(editOps);
            AgentToolResult r = t.execute("id", Map.of("diff", "@@ ... @@"), null, null);
            assertThat(text(r)).contains("file_path");
        }

        @Test
        void blankFilePathRejected() throws Exception {
            EditDiffTool t = new EditDiffTool(editOps);
            AgentToolResult r = t.execute("id", Map.of("file_path", "   ", "diff", "x"), null, null);
            assertThat(text(r)).contains("file_path");
        }

        @Test
        void missingDiffRejected() throws Exception {
            EditDiffTool t = new EditDiffTool(editOps);
            AgentToolResult r = t.execute("id", Map.of("file_path", "/tmp/x"), null, null);
            assertThat(text(r)).contains("diff content");
        }

        @Test
        void emptyHunksReportError() throws Exception {
            when(editOps.readFile(any(Path.class))).thenReturn("hi\nworld\n".getBytes(StandardCharsets.UTF_8));
            EditDiffTool t = new EditDiffTool(editOps);
            AgentToolResult r = t.execute("id", Map.of("file_path", "/tmp/x", "diff", "no hunks at all"), null, null);
            assertThat(text(r)).contains("No valid hunks");
        }
    }

    @Nested
    class HunkParsing {

        @Test
        void singleHunkParsedAndApplied() throws Exception {
            when(editOps.readFile(any(Path.class)))
                    .thenReturn("line1\nline2\nline3\n".getBytes(StandardCharsets.UTF_8));
            String diff = "@@ -2,1 +2,1 @@\n-line2\n+LINE2\n";
            EditDiffTool t = new EditDiffTool(editOps);
            AgentToolResult result = t.execute("id", Map.of("file_path", "/tmp/x", "diff", diff), null, null);
            assertThat(text(result)).contains("Applied 1 hunk(s)");
            ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
            verify(editOps).writeFile(any(Path.class), captor.capture());
            assertThat(captor.getValue()).contains("LINE2").doesNotContain("line2\n");
        }

        @Test
        void multipleHunksAppliedInReverse() throws Exception {
            when(editOps.readFile(any(Path.class))).thenReturn("a\nb\nc\nd\n".getBytes(StandardCharsets.UTF_8));
            String diff = "@@ -1,1 +1,1 @@\n-a\n+A\n" + "@@ -3,1 +3,1 @@\n-c\n+C\n";
            EditDiffTool t = new EditDiffTool(editOps);
            AgentToolResult result = t.execute("id", Map.of("file_path", "/tmp/x", "diff", diff), null, null);
            assertThat(text(result)).contains("Applied 2 hunk(s)");
        }

        @Test
        void hunksWithContextLines() {
            String diff = "@@ -1,3 +1,3 @@\n line1\n-line2\n+LINE2\n line3\n";
            List<EditDiffTool.Hunk> hunks = EditDiffTool.parseHunks(diff);
            assertThat(hunks).hasSize(1);
            EditDiffTool.Hunk h = hunks.get(0);
            assertThat(h.contextBefore()).containsExactly("line1");
            assertThat(h.removals()).containsExactly("line2");
            assertThat(h.additions()).containsExactly("LINE2");
        }

        @Test
        void diffWithoutHunkHeader() {
            assertThat(EditDiffTool.parseHunks("not a real diff")).isEmpty();
        }

        @Test
        void hunkWithDiffMarkersStops() {
            String diff = "@@ -1,1 +1,1 @@\n-a\n+A\n--- another\n";
            List<EditDiffTool.Hunk> hunks = EditDiffTool.parseHunks(diff);
            assertThat(hunks).hasSize(1);
        }
    }

    @Nested
    class NewlinePreservation {

        @Test
        void trailingNewlinePreserved() throws Exception {
            when(editOps.readFile(any(Path.class))).thenReturn("a\nb\n".getBytes(StandardCharsets.UTF_8));
            String diff = "@@ -1,1 +1,1 @@\n-a\n+A\n";
            EditDiffTool t = new EditDiffTool(editOps);
            t.execute("id", Map.of("file_path", "/tmp/x", "diff", diff), null, null);
            ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
            verify(editOps).writeFile(any(Path.class), captor.capture());
            assertThat(captor.getValue()).endsWith("\n");
        }

        @Test
        void noTrailingNewlineNotAdded() throws Exception {
            when(editOps.readFile(any(Path.class))).thenReturn("a\nb".getBytes(StandardCharsets.UTF_8));
            String diff = "@@ -1,1 +1,1 @@\n-a\n+A\n";
            EditDiffTool t = new EditDiffTool(editOps);
            t.execute("id", Map.of("file_path", "/tmp/x", "diff", diff), null, null);
            ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
            verify(editOps).writeFile(any(Path.class), captor.capture());
            assertThat(captor.getValue()).doesNotEndWith("\n");
        }
    }
}
