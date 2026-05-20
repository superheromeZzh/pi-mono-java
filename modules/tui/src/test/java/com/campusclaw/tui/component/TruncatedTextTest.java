/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.campusclaw.tui.component;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class TruncatedTextTest {

    @Nested
    class Construction {

        @Test
        void emptyConstructorYieldsEmpty() {
            assertEquals("", new TruncatedText().getText());
        }

        @Test
        void nullTextBecomesEmpty() {
            assertEquals("", new TruncatedText(null).getText());
        }

        @Test
        void styledConstructor() {
            TruncatedText t = new TruncatedText("hi", s -> "[" + s + "]");
            List<String> lines = t.render(10);
            assertEquals(1, lines.size());
            assertTrue(lines.get(0).startsWith("["));
        }
    }

    @Nested
    class Rendering {

        @Test
        void emptyTextProducesNoLines() {
            assertEquals(List.of(), new TruncatedText("").render(10));
        }

        @Test
        void fitsExactlyNoEllipsis() {
            List<String> lines = new TruncatedText("hello").render(5);
            assertEquals(1, lines.size());
            assertEquals("hello", lines.get(0));
        }

        @Test
        void fitsWithPadding() {
            List<String> lines = new TruncatedText("hi").render(5);

            // 2 chars + 3 spaces padding
            assertEquals("hi   ", lines.get(0));
        }

        @Test
        void truncatesWithEllipsis() {
            List<String> lines = new TruncatedText("abcdefgh").render(5);

            // First 4 chars + ellipsis
            assertEquals("abcd…", lines.get(0));
        }

        @Test
        void widthOneShowsNoEllipsis() {
            List<String> lines = new TruncatedText("abcdef").render(1);

            // Width too narrow for ellipsis — just first char
            assertEquals("a", lines.get(0));
        }

        @Test
        void widthZeroProducesEmptyDisplay() {
            List<String> lines = new TruncatedText("abc").render(0);
            assertEquals("", lines.get(0));
        }

        @Test
        void styledApplied() {
            TruncatedText t = new TruncatedText("hi");
            t.setStyleFn(s -> "<" + s + ">");
            assertEquals("<hi   >", t.render(5).get(0));
        }
    }

    @Nested
    class Caching {

        @Test
        void sameWidthHitsCache() {
            TruncatedText t = new TruncatedText("hi");
            List<String> first = t.render(10);
            List<String> second = t.render(10);
            assertSame(first, second);
        }

        @Test
        void differentWidthRecomputes() {
            TruncatedText t = new TruncatedText("hi");
            List<String> first = t.render(10);
            List<String> second = t.render(20);
            assertNotSame(first, second);
        }

        @Test
        void setTextInvalidatesCache() {
            TruncatedText t = new TruncatedText("hi");
            List<String> first = t.render(10);
            t.setText("ok");
            List<String> second = t.render(10);
            assertNotSame(first, second);
        }

        @Test
        void invalidateClearsCache() {
            TruncatedText t = new TruncatedText("hi");
            List<String> first = t.render(10);
            t.invalidate();
            List<String> second = t.render(10);
            assertNotSame(first, second);
        }

        @Test
        void setNullTextBecomesEmpty() {
            TruncatedText t = new TruncatedText("hi");
            t.setText(null);
            assertEquals("", t.getText());
        }
    }
}
