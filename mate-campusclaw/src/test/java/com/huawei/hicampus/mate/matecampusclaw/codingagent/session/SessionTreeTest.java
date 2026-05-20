/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.hicampus.mate.matecampusclaw.codingagent.session;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import com.huawei.hicampus.mate.matecampusclaw.ai.types.TextContent;
import com.huawei.hicampus.mate.matecampusclaw.ai.types.UserMessage;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class SessionTreeTest {

    private static SessionEntry msg(String id, String parentId, String text) {
        return SessionEntry.message(id, parentId, new UserMessage(List.of(new TextContent(text, null)), 0L));
    }

    @Nested
    class Lifecycle {

        @Test
        void newTreeIsEmpty() {
            SessionTree t = new SessionTree();
            assertThat(t.isEmpty()).isTrue();
            assertThat(t.size()).isZero();
            assertThat(t.getCurrentBranch()).isEmpty();
            assertThat(t.getCurrentMessages()).isEmpty();
            assertThat(t.getLeaves()).isEmpty();
            assertThat(t.getCurrentEntryId()).isNull();
        }

        @Test
        void addEntryAdvancesCurrent() {
            SessionTree t = new SessionTree();
            t.addEntry(msg("a", null, "hello"));
            assertThat(t.size()).isEqualTo(1);
            assertThat(t.getCurrentEntryId()).isEqualTo("a");
            assertThat(t.getAllEntries()).hasSize(1);
        }

        @Test
        void linearChainBuildsBranch() {
            SessionTree t = new SessionTree();
            t.addEntry(msg("a", null, "1"));
            t.addEntry(msg("b", "a", "2"));
            t.addEntry(msg("c", "b", "3"));
            List<SessionEntry> branch = t.getCurrentBranch();
            assertThat(branch).extracting(SessionEntry::id).containsExactly("a", "b", "c");
            assertThat(t.getCurrentMessages()).hasSize(3);
        }
    }

    @Nested
    class ForkAndSwitch {

        @Test
        void forkChangesCurrentPointer() {
            SessionTree t = new SessionTree();
            t.addEntry(msg("a", null, "1"));
            t.addEntry(msg("b", "a", "2"));
            String parent = t.fork("a");
            assertThat(parent).isEqualTo("a");
            assertThat(t.getCurrentEntryId()).isEqualTo("a");
        }

        @Test
        void forkUnknownIdThrows() {
            SessionTree t = new SessionTree();
            t.addEntry(msg("a", null, "1"));
            assertThatThrownBy(() -> t.fork("missing"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Entry not found");
        }

        @Test
        void switchToValidId() {
            SessionTree t = new SessionTree();
            t.addEntry(msg("a", null, "1"));
            t.addEntry(msg("b", "a", "2"));
            t.switchTo("a");
            assertThat(t.getCurrentEntryId()).isEqualTo("a");
        }

        @Test
        void switchToInvalidIdThrows() {
            SessionTree t = new SessionTree();
            assertThatThrownBy(() -> t.switchTo("zzz")).isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Nested
    class TreeQueries {

        @Test
        void getChildrenForLeafEmpty() {
            SessionTree t = new SessionTree();
            t.addEntry(msg("a", null, "1"));
            assertThat(t.getChildren("a")).isEmpty();
        }

        @Test
        void getChildrenLinksByParent() {
            SessionTree t = new SessionTree();
            t.addEntry(msg("root", null, "r"));
            t.addEntry(msg("c1", "root", "c1"));
            t.addEntry(msg("c2", "root", "c2"));
            assertThat(t.getChildren("root")).extracting(SessionEntry::id).containsExactly("c1", "c2");
        }

        @Test
        void getLeavesIdentifiesNonParentEntries() {
            SessionTree t = new SessionTree();
            t.addEntry(msg("root", null, "r"));
            t.addEntry(msg("c1", "root", "c1"));
            t.addEntry(msg("c2", "root", "c2"));
            assertThat(t.getLeaves()).extracting(SessionEntry::id).containsExactlyInAnyOrder("c1", "c2");
        }

        @Test
        void getEntryFromIdReturnsOptional() {
            SessionTree t = new SessionTree();
            t.addEntry(msg("a", null, "x"));
            assertThat(t.getEntry("a")).isPresent();
            assertThat(t.getEntry("missing")).isEmpty();
        }

        @Test
        void getAllEntriesIsUnmodifiable() {
            SessionTree t = new SessionTree();
            t.addEntry(msg("a", null, "x"));
            org.junit.jupiter.api.Assertions.assertThrows(
                    UnsupportedOperationException.class, () -> t.getAllEntries().add(null));
        }
    }

    @Nested
    class StaticHelpers {

        @Test
        void generatedIdsAreShortAndUnique() {
            String a = SessionTree.generateId();
            String b = SessionTree.generateId();
            assertThat(a).hasSize(8);
            assertThat(b).hasSize(8);
            assertThat(a).isNotEqualTo(b);
        }
    }

    @Nested
    class NonMessageEntries {

        @Test
        void compactionEntryHasNoMessage() {
            SessionEntry e = SessionEntry.compaction("c1", null, "summary text");
            SessionTree t = new SessionTree();
            t.addEntry(e);
            assertThat(t.getCurrentMessages()).isEmpty();
            assertThat(t.getCurrentBranch()).hasSize(1);
        }
    }
}
