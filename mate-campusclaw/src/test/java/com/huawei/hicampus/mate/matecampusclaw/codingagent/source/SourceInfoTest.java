/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.hicampus.mate.matecampusclaw.codingagent.source;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class SourceInfoTest {

    private static SourceInfo.ResourceSource src(String id, String type, SourceInfo.SourceType st, int priority) {
        return new SourceInfo.ResourceSource(id, type, st, null, null, Instant.EPOCH, priority);
    }

    @Nested
    class RegistrationAndQuery {

        @Test
        void registerAndGetEffective() {
            SourceInfo info = new SourceInfo();
            SourceInfo.ResourceSource s1 = src("skill1", "skill", SourceInfo.SourceType.BUILTIN, 10);
            info.register(s1);
            assertThat(info.getEffective("skill1")).contains(s1);
            assertThat(info.getResourceIds()).contains("skill1");
        }

        @Test
        void getEffectiveAbsent() {
            assertThat(new SourceInfo().getEffective("missing")).isEmpty();
        }

        @Test
        void higherPriorityWinsAsEffective() {
            SourceInfo info = new SourceInfo();
            SourceInfo.ResourceSource low = src("a", "skill", SourceInfo.SourceType.BUILTIN, 10);
            SourceInfo.ResourceSource high = src("a", "skill", SourceInfo.SourceType.PROJECT_CONFIG, 50);
            info.register(low);
            info.register(high);
            assertThat(info.getEffective("a")).contains(high);
        }

        @Test
        void getSourcesReturnsAllSorted() {
            SourceInfo info = new SourceInfo();
            SourceInfo.ResourceSource low = src("a", "skill", SourceInfo.SourceType.BUILTIN, 10);
            SourceInfo.ResourceSource high = src("a", "skill", SourceInfo.SourceType.PROJECT_CONFIG, 50);
            info.register(low);
            info.register(high);
            assertThat(info.getSources("a")).containsExactly(high, low);
        }
    }

    @Nested
    class Filtering {

        @Test
        void getByTypeFiltersAndSorts() {
            SourceInfo info = new SourceInfo();
            info.register(src("s1", "skill", SourceInfo.SourceType.BUILTIN, 1));
            info.register(src("t1", "tool", SourceInfo.SourceType.BUILTIN, 5));
            info.register(src("s2", "skill", SourceInfo.SourceType.PROJECT_CONFIG, 10));
            List<SourceInfo.ResourceSource> skills = info.getByType("skill");
            assertThat(skills).extracting(SourceInfo.ResourceSource::resourceId).containsExactly("s2", "s1");
        }

        @Test
        void getConflictsOnlyMultiple() {
            SourceInfo info = new SourceInfo();
            info.register(src("a", "skill", SourceInfo.SourceType.BUILTIN, 1));
            info.register(src("a", "skill", SourceInfo.SourceType.PROJECT_CONFIG, 10));
            info.register(src("b", "skill", SourceInfo.SourceType.BUILTIN, 1));
            assertThat(info.getConflicts()).containsKey("a").doesNotContainKey("b");
        }

        @Test
        void noConflictsReturnsEmpty() {
            SourceInfo info = new SourceInfo();
            info.register(src("a", "skill", SourceInfo.SourceType.BUILTIN, 1));
            assertThat(info.getConflicts()).isEmpty();
        }
    }

    @Nested
    class Mutation {

        @Test
        void clearRemovesAll() {
            SourceInfo info = new SourceInfo();
            info.register(src("a", "skill", SourceInfo.SourceType.BUILTIN, 1));
            info.clear();
            assertThat(info.getResourceIds()).isEmpty();
        }

        @Test
        void resourceIdsImmutable() {
            SourceInfo info = new SourceInfo();
            info.register(src("a", "skill", SourceInfo.SourceType.BUILTIN, 1));
            org.junit.jupiter.api.Assertions.assertThrows(
                    UnsupportedOperationException.class,
                    () -> info.getResourceIds().add("x"));
        }
    }

    @Nested
    class Formatting {

        @Test
        void basicFormat() {
            SourceInfo.ResourceSource s = src("my-skill", "skill", SourceInfo.SourceType.BUILTIN, 0);
            String formatted = SourceInfo.format(s);
            assertThat(formatted).contains("my-skill").contains("skill").contains("builtin");
        }

        @Test
        void withPackageAndPath() {
            SourceInfo.ResourceSource s = new SourceInfo.ResourceSource(
                    "id", "tool", SourceInfo.SourceType.PACKAGE, "pkg-name", Path.of("/x/y"), Instant.EPOCH, 5);
            String formatted = SourceInfo.format(s);
            assertThat(formatted).contains("[pkg-name]").contains("/x/y");
        }

        @Test
        void underscoreSourceTypeFormatted() {
            SourceInfo.ResourceSource s = src("x", "skill", SourceInfo.SourceType.GLOBAL_CONFIG, 0);
            assertThat(SourceInfo.format(s)).contains("global config");
        }
    }
}
