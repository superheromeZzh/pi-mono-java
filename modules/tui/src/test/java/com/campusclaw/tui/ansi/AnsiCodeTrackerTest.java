/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.campusclaw.tui.ansi;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class AnsiCodeTrackerTest {

    @Nested
    class IndividualAttributes {

        @Test
        void boldThenReset() {
            AnsiCodeTracker t = new AnsiCodeTracker();
            t.process("\033[1m");
            assertTrue(t.hasActiveCodes());
            assertTrue(t.getActiveCodes().contains("1"));
            t.process("\033[22m");
            assertFalse(t.getActiveCodes().contains("1"));
        }

        @Test
        void italicUnderlineBlinkAttributes() {
            AnsiCodeTracker t = new AnsiCodeTracker();
            t.process("\033[3;4;5m");
            String codes = t.getActiveCodes();
            assertTrue(codes.contains("3"));
            assertTrue(codes.contains("4"));
            assertTrue(codes.contains("5"));
            t.process("\033[23m"); // not italic
            t.process("\033[24m"); // not underline
            t.process("\033[25m"); // not blink
            assertFalse(t.hasActiveCodes());
        }

        @Test
        void inverseHiddenStrikethrough() {
            AnsiCodeTracker t = new AnsiCodeTracker();
            t.process("\033[7;8;9m");
            assertTrue(t.getActiveCodes().contains("7"));
            t.process("\033[27;28;29m");
            assertFalse(t.hasActiveCodes());
        }

        @Test
        void dimResetsViaCode22() {
            AnsiCodeTracker t = new AnsiCodeTracker();
            t.process("\033[2m");
            assertTrue(t.getActiveCodes().contains("2"));
            t.process("\033[22m");
            assertFalse(t.hasActiveCodes());
        }

        @Test
        void boldResetsViaCode21() {
            AnsiCodeTracker t = new AnsiCodeTracker();
            t.process("\033[1m");
            t.process("\033[21m");
            assertFalse(t.getActiveCodes().contains("1"));
        }
    }

    @Nested
    class Colors {

        @Test
        void standardForegroundAndBackground() {
            AnsiCodeTracker t = new AnsiCodeTracker();
            t.process("\033[31m"); // fg red
            t.process("\033[42m"); // bg green
            String codes = t.getActiveCodes();
            assertTrue(codes.contains("31"));
            assertTrue(codes.contains("42"));
            t.process("\033[39m"); // default fg
            t.process("\033[49m"); // default bg
            assertEquals("", t.getActiveCodes());
        }

        @Test
        void brightForegroundAndBackground() {
            AnsiCodeTracker t = new AnsiCodeTracker();
            t.process("\033[91m"); // bright red fg
            t.process("\033[107m"); // bright white bg
            String codes = t.getActiveCodes();
            assertTrue(codes.contains("91"));
            assertTrue(codes.contains("107"));
        }

        @Test
        void color256() {
            AnsiCodeTracker t = new AnsiCodeTracker();
            t.process("\033[38;5;240m");
            assertTrue(t.getActiveCodes().contains("38;5;240"));
            t.process("\033[48;5;100m");
            assertTrue(t.getActiveCodes().contains("48;5;100"));
        }

        @Test
        void colorTrueColor() {
            AnsiCodeTracker t = new AnsiCodeTracker();
            t.process("\033[38;2;128;0;255m");
            assertTrue(t.getActiveCodes().contains("38;2;128;0;255"));
            t.process("\033[48;2;10;20;30m");
            assertTrue(t.getActiveCodes().contains("48;2;10;20;30"));
        }

        @Test
        void incompleteExtendedColorSkipped() {
            AnsiCodeTracker t = new AnsiCodeTracker();

            // 38 with no follow-up is ignored
            t.process("\033[38m");
            assertFalse(t.hasActiveCodes());
        }
    }

    @Nested
    class ResetAndEmpty {

        @Test
        void emptyParamsResets() {
            AnsiCodeTracker t = new AnsiCodeTracker();
            t.process("\033[1m");
            t.process("\033[m");
            assertFalse(t.hasActiveCodes());
        }

        @Test
        void zeroParamsResets() {
            AnsiCodeTracker t = new AnsiCodeTracker();
            t.process("\033[31;1m");
            t.process("\033[0m");
            assertFalse(t.hasActiveCodes());
        }

        @Test
        void clearResetsAll() {
            AnsiCodeTracker t = new AnsiCodeTracker();
            t.process("\033[1;31m");
            t.clear();
            assertFalse(t.hasActiveCodes());
        }
    }

    @Nested
    class InvalidInput {

        @Test
        void nonSgrIgnored() {
            AnsiCodeTracker t = new AnsiCodeTracker();
            t.process("not an ansi code");
            assertFalse(t.hasActiveCodes());
        }

        @Test
        void malformedEscapeIgnored() {
            AnsiCodeTracker t = new AnsiCodeTracker();
            t.process("\033xyz");
            assertFalse(t.hasActiveCodes());
        }
    }

    @Nested
    class LineEndReset {

        @Test
        void underlineRequiresLineEndReset() {
            AnsiCodeTracker t = new AnsiCodeTracker();
            t.process("\033[4m");
            assertEquals("\033[24m", t.getLineEndReset());
        }

        @Test
        void noUnderlineReturnsEmpty() {
            AnsiCodeTracker t = new AnsiCodeTracker();
            t.process("\033[1m");
            assertEquals("", t.getLineEndReset());
        }
    }
}
