/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.hicampus.mate.matecampusclaw.codingagent.compaction;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.huawei.hicampus.mate.matecampusclaw.ai.types.AssistantMessage;
import com.huawei.hicampus.mate.matecampusclaw.ai.types.ContentBlock;
import com.huawei.hicampus.mate.matecampusclaw.ai.types.Message;
import com.huawei.hicampus.mate.matecampusclaw.ai.types.StopReason;
import com.huawei.hicampus.mate.matecampusclaw.ai.types.TextContent;
import com.huawei.hicampus.mate.matecampusclaw.ai.types.ToolCall;
import com.huawei.hicampus.mate.matecampusclaw.ai.types.Usage;
import com.huawei.hicampus.mate.matecampusclaw.ai.types.UserMessage;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class FileOperationTrackerTest {

    private static AssistantMessage assistant(ContentBlock... blocks) {
        return new AssistantMessage(List.of(blocks), "x", "x", "x", null, Usage.empty(), StopReason.STOP, null, 0L);
    }

    private static ToolCall call(String name, Map<String, Object> args) {
        return new ToolCall("c", name, args);
    }

    @Test
    void noMessagesReturnsEmpty() {
        FileOperationTracker.FileOperations ops = FileOperationTracker.extract(List.of());
        assertThat(ops.filesRead()).isEmpty();
        assertThat(ops.filesModified()).isEmpty();
    }

    @Test
    void userMessagesIgnored() {
        Message u = new UserMessage(List.of(new TextContent("hi", null)), 0);
        FileOperationTracker.FileOperations ops = FileOperationTracker.extract(List.of(u));
        assertThat(ops.filesRead()).isEmpty();
        assertThat(ops.filesModified()).isEmpty();
    }

    @Nested
    class ReadOps {

        @Test
        void readByPathArg() {
            AssistantMessage am = assistant(call("Read", Map.of("path", "/tmp/a.txt")));
            assertThat(FileOperationTracker.extract(List.of(am)).filesRead()).containsExactly("/tmp/a.txt");
        }

        @Test
        void readLowercase() {
            AssistantMessage am = assistant(call("read", Map.of("path", "/tmp/b")));
            assertThat(FileOperationTracker.extract(List.of(am)).filesRead()).containsExactly("/tmp/b");
        }

        @Test
        void readByFilePathArg() {
            AssistantMessage am = assistant(call("Read", Map.of("file_path", "/tmp/c")));
            assertThat(FileOperationTracker.extract(List.of(am)).filesRead()).containsExactly("/tmp/c");
        }
    }

    @Nested
    class WriteOps {

        @Test
        void writeAndEditTracked() {
            AssistantMessage am = assistant(
                    call("Write", Map.of("file_path", "/a")),
                    call("write", Map.of("path", "/b")),
                    call("Edit", Map.of("path", "/c")),
                    call("edit", Map.of("file_path", "/d")));
            FileOperationTracker.FileOperations ops = FileOperationTracker.extract(List.of(am));
            assertThat(ops.filesModified()).containsExactly("/a", "/b", "/c", "/d");
        }
    }

    @Nested
    class IgnoredPaths {

        @Test
        void bashCallsAreNoOp() {
            AssistantMessage am = assistant(call("Bash", Map.of("command", "rm -rf /x")));
            FileOperationTracker.FileOperations ops = FileOperationTracker.extract(List.of(am));
            assertThat(ops.filesRead()).isEmpty();
            assertThat(ops.filesModified()).isEmpty();
        }

        @Test
        void unknownToolIgnored() {
            AssistantMessage am = assistant(call("UnknownTool", Map.of("path", "/x")));
            assertThat(FileOperationTracker.extract(List.of(am)).filesRead()).isEmpty();
        }

        @Test
        void toolCallWithoutPathSkipped() {
            AssistantMessage am = assistant(call("Read", Map.of("not_path", "x")));
            assertThat(FileOperationTracker.extract(List.of(am)).filesRead()).isEmpty();
        }

        @Test
        void nullArgsHandled() {
            Map<String, Object> args = new HashMap<>();
            args.put("path", null);
            AssistantMessage am = assistant(call("Read", args));
            assertThat(FileOperationTracker.extract(List.of(am)).filesRead()).isEmpty();
        }

        @Test
        void nonStringArgIgnored() {
            Map<String, Object> args = new HashMap<>();
            args.put("path", 123);
            AssistantMessage am = assistant(call("Read", args));
            assertThat(FileOperationTracker.extract(List.of(am)).filesRead()).isEmpty();
        }
    }
}
