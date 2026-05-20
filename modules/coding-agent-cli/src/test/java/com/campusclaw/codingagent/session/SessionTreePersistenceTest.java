/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.campusclaw.codingagent.session;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import com.campusclaw.ai.types.TextContent;
import com.campusclaw.ai.types.UserMessage;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SessionTreePersistenceTest {

    private static SessionEntry msg(String id, String parent, String text) {
        return SessionEntry.message(id, parent, new UserMessage(List.of(new TextContent(text, null)), 0L));
    }

    @Nested
    class SaveAndLoad {

        @Test
        void roundtripPreservesEntries(@TempDir Path tmp) {
            SessionTree tree = new SessionTree();
            tree.addEntry(msg("a", null, "first"));
            tree.addEntry(msg("b", "a", "second"));
            SessionTreePersistence p = new SessionTreePersistence(tmp);
            p.save("test", tree);
            SessionTree loaded = p.load("test");
            assertThat(loaded.size()).isEqualTo(2);
            assertThat(loaded.getAllEntries()).extracting(SessionEntry::id).containsExactly("a", "b");
        }

        @Test
        void loadMissingSessionReturnsEmptyTree(@TempDir Path tmp) {
            SessionTreePersistence p = new SessionTreePersistence(tmp);
            assertThat(p.load("ghost").isEmpty()).isTrue();
        }

        @Test
        void loadSkipsMalformedAndBlankLines(@TempDir Path tmp) throws IOException {
            Path file = tmp.resolve("bad.jsonl");
            Files.writeString(
                    file,
                    "\nnot json\n" + "{\"id\":\"a\",\"type\":\"compaction\",\"summary\":\"hi\",\"timestamp\":0}\n");
            SessionTreePersistence p = new SessionTreePersistence(tmp);
            assertThat(p.load("bad").size()).isEqualTo(1);
        }
    }

    @Nested
    class Append {

        @Test
        void appendAddsEntry(@TempDir Path tmp) throws IOException {
            SessionTreePersistence p = new SessionTreePersistence(tmp);
            p.appendEntry("sess", msg("a", null, "first"));
            p.appendEntry("sess", msg("b", "a", "second"));
            List<String> lines = Files.readAllLines(tmp.resolve("sess.jsonl"));
            assertThat(lines).hasSize(2);
        }
    }

    @Nested
    class ListAndDelete {

        @Test
        void listEmptyWhenDirMissing(@TempDir Path tmp) {
            SessionTreePersistence p = new SessionTreePersistence(tmp.resolve("nope"));
            assertThat(p.listSessions()).isEmpty();
        }

        @Test
        void listFiltersNonJsonlAndStripsExt(@TempDir Path tmp) throws IOException {
            Files.writeString(tmp.resolve("s1.jsonl"), "");
            Files.writeString(tmp.resolve("s2.jsonl"), "");
            Files.writeString(tmp.resolve("note.txt"), "ignore");
            SessionTreePersistence p = new SessionTreePersistence(tmp);
            assertThat(p.listSessions()).containsExactly("s1", "s2");
        }

        @Test
        void deleteRemovesFile(@TempDir Path tmp) throws IOException {
            Path file = tmp.resolve("s1.jsonl");
            Files.writeString(file, "");
            SessionTreePersistence p = new SessionTreePersistence(tmp);
            assertThat(p.delete("s1")).isTrue();
            assertThat(Files.exists(file)).isFalse();
        }

        @Test
        void deleteMissingReturnsFalse(@TempDir Path tmp) {
            SessionTreePersistence p = new SessionTreePersistence(tmp);
            assertThat(p.delete("ghost")).isFalse();
        }
    }

    @Nested
    class DefaultConstructor {

        @Test
        void defaultUsesAppPaths() {
            // Constructor must not throw even when default sessions dir doesn't exist.
            assertThat(new SessionTreePersistence()).isNotNull();
        }
    }
}
