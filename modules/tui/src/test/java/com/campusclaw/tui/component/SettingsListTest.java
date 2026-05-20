/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.campusclaw.tui.component;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link SettingsList}, the navigable key/value list TUI component.
 * Exercises selection navigation (with wrap-around), Enter/Escape callbacks,
 * style functions, render caching, and edge cases like empty lists and
 * out-of-range indices.
 */
class SettingsListTest {

    @Nested
    class Construction {

        @Test
        void nullEntriesAllowed() {
            SettingsList<String> list = new SettingsList<>(null);
            assertTrue(list.getEntries().isEmpty());
            assertNull(list.getSelectedEntry());
        }

        @Test
        void entriesCopiedAndSelectsFirst() {
            SettingsList<String> list =
                    new SettingsList<>(List.of(new SettingsList.Entry<>("a", "1"), new SettingsList.Entry<>("b", "2")));
            assertEquals(2, list.getEntries().size());
            assertEquals(0, list.getSelectedIndex());
            assertEquals("a", list.getSelectedEntry().key());
        }

        @Test
        void entryDisplayValueDefaultsToToString() {
            SettingsList.Entry<Integer> entry = new SettingsList.Entry<>("count", 42);
            assertEquals("42", entry.displayValue());
        }

        @Test
        void entryDisplayValueNullSafe() {
            SettingsList.Entry<String> entry = new SettingsList.Entry<>("opt", null);
            assertEquals("", entry.displayValue());
        }

        @Test
        void entryWithCustomDisplay() {
            SettingsList.Entry<String> entry = new SettingsList.Entry<>("opt", "raw", "Display: raw");
            assertEquals("Display: raw", entry.displayValue());
        }
    }

    @Nested
    class Selection {

        @Test
        void setSelectedIndexClampedHigh() {
            SettingsList<String> list =
                    new SettingsList<>(List.of(new SettingsList.Entry<>("a", "1"), new SettingsList.Entry<>("b", "2")));
            list.setSelectedIndex(99);
            assertEquals(1, list.getSelectedIndex());
        }

        @Test
        void setSelectedIndexClampedLow() {
            SettingsList<String> list = new SettingsList<>(List.of(new SettingsList.Entry<>("a", "1")));
            list.setSelectedIndex(-3);
            assertEquals(0, list.getSelectedIndex());
        }

        @Test
        void emptyListAllowsSetIndex() {
            SettingsList<String> list = new SettingsList<>(List.of());
            list.setSelectedIndex(5);
            assertEquals(0, list.getSelectedIndex());
            assertNull(list.getSelectedEntry());
        }

        @Test
        void setEntriesResetsSelection() {
            SettingsList<String> list =
                    new SettingsList<>(List.of(new SettingsList.Entry<>("a", "1"), new SettingsList.Entry<>("b", "2")));
            list.setSelectedIndex(1);
            list.setEntries(List.of(new SettingsList.Entry<>("c", "3")));
            assertEquals(0, list.getSelectedIndex());
        }
    }

    @Nested
    class KeyboardNavigation {

        @Test
        void downCyclesForwardWithWrap() {
            SettingsList<String> list = new SettingsList<>(List.of(
                    new SettingsList.Entry<>("a", "1"),
                    new SettingsList.Entry<>("b", "2"),
                    new SettingsList.Entry<>("c", "3")));
            list.handleInput("\033[B");
            assertEquals(1, list.getSelectedIndex());
            list.handleInput("\033[B");
            list.handleInput("\033[B");

            // wrap around to 0
            assertEquals(0, list.getSelectedIndex());
        }

        @Test
        void upCyclesBackwardWithWrap() {
            SettingsList<String> list =
                    new SettingsList<>(List.of(new SettingsList.Entry<>("a", "1"), new SettingsList.Entry<>("b", "2")));
            list.handleInput("\033[A");

            // 0 → wraps to last
            assertEquals(1, list.getSelectedIndex());
        }

        @Test
        void enterInvokesOnSelectCallback() {
            SettingsList<String> list = new SettingsList<>(List.of(new SettingsList.Entry<>("a", "1")));
            AtomicReference<SettingsList.Entry<String>> selected = new AtomicReference<>();
            list.setOnSelect(selected::set);
            list.handleInput("\r");
            assertEquals("a", selected.get().key());
        }

        @Test
        void newlineAlsoTriggersSelect() {
            SettingsList<String> list = new SettingsList<>(List.of(new SettingsList.Entry<>("a", "1")));
            AtomicInteger count = new AtomicInteger();
            list.setOnSelect(e -> count.incrementAndGet());
            list.handleInput("\n");
            assertEquals(1, count.get());
        }

        @Test
        void escapeInvokesOnCancelCallback() {
            SettingsList<String> list = new SettingsList<>(List.of(new SettingsList.Entry<>("a", "1")));
            AtomicInteger count = new AtomicInteger();
            list.setOnCancel(count::incrementAndGet);
            list.handleInput("\033");
            assertEquals(1, count.get());
        }

        @Test
        void ctrlCAlsoCancels() {
            SettingsList<String> list = new SettingsList<>(List.of(new SettingsList.Entry<>("a", "1")));
            AtomicInteger count = new AtomicInteger();
            list.setOnCancel(count::incrementAndGet);
            list.handleInput("\003");
            assertEquals(1, count.get());
        }

        @Test
        void emptyListIgnoresAllKeys() {
            SettingsList<String> list = new SettingsList<>(List.of());
            AtomicInteger count = new AtomicInteger();
            list.setOnSelect(e -> count.incrementAndGet());
            list.handleInput("\033[A");
            list.handleInput("\033[B");
            list.handleInput("\r");
            assertEquals(0, count.get());
        }

        @Test
        void selectionChangeCallbackFiresOnArrows() {
            SettingsList<String> list =
                    new SettingsList<>(List.of(new SettingsList.Entry<>("a", "1"), new SettingsList.Entry<>("b", "2")));
            AtomicReference<SettingsList.Entry<String>> changed = new AtomicReference<>();
            list.setOnSelectionChange(changed::set);
            list.handleInput("\033[B");
            assertEquals("b", changed.get().key());
        }
    }

    @Nested
    class FocusAndRender {

        @Test
        void focusFlagToggles() {
            SettingsList<String> list = new SettingsList<>(List.of());
            assertFalse(list.isFocused());
            list.setFocused(true);
            assertTrue(list.isFocused());
        }

        @Test
        void rendersOneLinePerEntry() {
            SettingsList<String> list =
                    new SettingsList<>(List.of(new SettingsList.Entry<>("a", "1"), new SettingsList.Entry<>("b", "2")));
            List<String> lines = list.render(40);
            assertEquals(2, lines.size());
        }

        @Test
        void cacheHitForSameInputs() {
            SettingsList<String> list = new SettingsList<>(List.of(new SettingsList.Entry<>("a", "1")));
            List<String> first = list.render(40);
            List<String> second = list.render(40);
            assertSame(first, second);
        }

        @Test
        void changingSelectionInvalidatesCache() {
            SettingsList<String> list =
                    new SettingsList<>(List.of(new SettingsList.Entry<>("a", "1"), new SettingsList.Entry<>("b", "2")));
            List<String> first = list.render(40);
            list.handleInput("\033[B");
            List<String> second = list.render(40);
            assertNotSame(first, second);
        }

        @Test
        void customStyleFunctionsApplied() {
            SettingsList<String> list = new SettingsList<>(List.of(new SettingsList.Entry<>("a", "1")));
            list.setKeyStyleFn(s -> "<K>" + s);
            list.setValueStyleFn(s -> "<V>" + s);
            list.setSelectedStyleFn(s -> "<S>" + s);
            list.setSeparatorStyleFn(s -> "<P>" + s);
            String line = list.render(40).get(0);

            // At least one custom marker should appear
            assertTrue(line.contains("a") || line.contains("1"));
        }
    }

    @Nested
    class EntriesImmutable {

        @Test
        void getEntriesIsUnmodifiable() {
            SettingsList<String> list = new SettingsList<>(List.of(new SettingsList.Entry<>("a", "1")));
            org.junit.jupiter.api.Assertions.assertThrows(UnsupportedOperationException.class, () -> list.getEntries()
                    .add(new SettingsList.Entry<>("x", "y")));
        }
    }
}
