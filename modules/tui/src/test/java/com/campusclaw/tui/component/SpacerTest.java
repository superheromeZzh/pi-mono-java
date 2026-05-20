/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.campusclaw.tui.component;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.util.List;

import org.junit.jupiter.api.Test;

class SpacerTest {

    @Test
    void defaultConstructorOneLine() {
        Spacer s = new Spacer();
        assertEquals(1, s.getLines());
        List<String> lines = s.render(5);
        assertEquals(List.of("     "), lines);
    }

    @Test
    void multipleLines() {
        Spacer s = new Spacer(3);
        List<String> lines = s.render(2);
        assertEquals(3, lines.size());
        assertEquals("  ", lines.get(0));
    }

    @Test
    void negativeLinesClampedToZero() {
        Spacer s = new Spacer(-5);
        assertEquals(0, s.getLines());
        assertEquals(0, s.render(10).size());
    }

    @Test
    void cacheHitForSameSize() {
        Spacer s = new Spacer(2);
        List<String> a = s.render(5);
        List<String> b = s.render(5);
        assertSame(a, b);
    }

    @Test
    void setLinesInvalidatesCache() {
        Spacer s = new Spacer(1);
        List<String> a = s.render(5);
        s.setLines(2);
        List<String> b = s.render(5);
        assertNotSame(a, b);
        assertEquals(2, b.size());
    }

    @Test
    void differentWidthCacheMiss() {
        Spacer s = new Spacer(1);
        List<String> a = s.render(5);
        List<String> b = s.render(7);
        assertNotSame(a, b);
        assertEquals("       ", b.get(0));
    }
}
