/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.campusclaw.codingagent.skill;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

class SkillConflictExceptionTest {

    @Test
    void messageDescribesConflict() {
        SkillConflictException.Conflict c = new SkillConflictException.Conflict("skill-a", "pkg-x");
        SkillConflictException ex = new SkillConflictException(List.of(c));
        assertThat(ex.getMessage()).contains("skill-a").contains("pkg-x");
        assertThat(ex.conflicts()).containsExactly(c);
    }

    @Test
    void multipleConflicts() {
        List<SkillConflictException.Conflict> list =
                List.of(new SkillConflictException.Conflict("a", "p1"), new SkillConflictException.Conflict("b", "p2"));
        SkillConflictException ex = new SkillConflictException(list);
        assertThat(ex.getMessage()).contains("a").contains("b").contains("p1").contains("p2");
        assertThat(ex.conflicts()).hasSize(2);
    }

    @Test
    void conflictsListImmutable() {
        SkillConflictException ex = new SkillConflictException(List.of(new SkillConflictException.Conflict("a", "p1")));
        org.junit.jupiter.api.Assertions.assertThrows(
                UnsupportedOperationException.class, () -> ex.conflicts().add(null));
    }
}
