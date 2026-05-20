/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.hicampus.mate.matecampusclaw.tui.component;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class KeybindingsComponentTest {

    @Nested
    class Construction {

        @Test
        void defaultConstructorEmpty() {
            KeybindingsComponent c = new KeybindingsComponent();
            assertTrue(c.getKeybindings().isEmpty());
            assertTrue(c.render(80).isEmpty());
        }

        @Test
        void listConstructorCopies() {
            var kbs = List.of(
                    new KeybindingsComponent.Keybinding("Ctrl+C", "Exit"),
                    new KeybindingsComponent.Keybinding("Tab", "Complete"));
            KeybindingsComponent c = new KeybindingsComponent(kbs);
            assertEquals(2, c.getKeybindings().size());
            assertThrows(UnsupportedOperationException.class, () -> c.getKeybindings()
                    .add(null));
        }

        @Test
        void nullListConstructorBecomesEmpty() {
            KeybindingsComponent c = new KeybindingsComponent(null);
            assertTrue(c.getKeybindings().isEmpty());
        }
    }

    @Nested
    class Rendering {

        @Test
        void singleKeybindingFitsOnOneLine() {
            KeybindingsComponent c =
                    new KeybindingsComponent(List.of(new KeybindingsComponent.Keybinding("Ctrl+C", "Exit")));
            List<String> lines = c.render(80);
            assertEquals(1, lines.size());
            assertTrue(lines.get(0).contains("Ctrl+C"));
            assertTrue(lines.get(0).contains("Exit"));
        }

        @Test
        void multipleKeybindingsJoinedBySeparator() {
            KeybindingsComponent c = new KeybindingsComponent(List.of(
                    new KeybindingsComponent.Keybinding("Ctrl+C", "Exit"),
                    new KeybindingsComponent.Keybinding("Tab", "Complete")));
            List<String> lines = c.render(80);
            assertEquals(1, lines.size());
            assertTrue(lines.get(0).contains("|"));
        }

        @Test
        void wrapsToMultipleLinesWhenNarrow() {
            KeybindingsComponent c = new KeybindingsComponent(List.of(
                    new KeybindingsComponent.Keybinding("K1", "A long description"),
                    new KeybindingsComponent.Keybinding("K2", "Another long description"),
                    new KeybindingsComponent.Keybinding("K3", "Yet another description")));
            List<String> lines = c.render(30);
            assertTrue(lines.size() >= 2);
        }
    }

    @Nested
    class Setters {

        @Test
        void setKeybindingsInvalidatesCache() {
            KeybindingsComponent c = new KeybindingsComponent();
            c.setKeybindings(List.of(new KeybindingsComponent.Keybinding("K", "D")));
            List<String> first = c.render(80);

            // Re-render with same — cached
            List<String> second = c.render(80);
            assertSame(first, second);

            // Mutate keybindings list — should invalidate
            c.setKeybindings(List.of(new KeybindingsComponent.Keybinding("X", "Y")));
            List<String> third = c.render(80);
            assertNotSame(first, third);
        }

        @Test
        void setKeybindingsToNullBecomesEmpty() {
            KeybindingsComponent c = new KeybindingsComponent();
            c.setKeybindings(null);
            assertTrue(c.getKeybindings().isEmpty());
        }

        @Test
        void setSeparatorOverridesAndStyleApplied() {
            KeybindingsComponent c = new KeybindingsComponent(List.of(
                    new KeybindingsComponent.Keybinding("A", "x"), new KeybindingsComponent.Keybinding("B", "y")));
            c.setSeparator(" >> ");
            List<String> lines = c.render(80);
            assertTrue(lines.get(0).contains(">>"));
        }

        @Test
        void nullSeparatorRestoresDefault() {
            KeybindingsComponent c = new KeybindingsComponent(List.of(
                    new KeybindingsComponent.Keybinding("A", "x"), new KeybindingsComponent.Keybinding("B", "y")));
            c.setSeparator(null);
            assertTrue(c.render(80).get(0).contains("|"));
        }

        @Test
        void customStyleFunctionsApplied() {
            KeybindingsComponent c = new KeybindingsComponent(List.of(new KeybindingsComponent.Keybinding("K", "D")));
            c.setKeyStyleFn(s -> "<K:" + s + ">");
            c.setDescStyleFn(s -> "<D:" + s + ">");
            c.setSeparatorStyleFn(s -> "<S:" + s + ">");
            List<String> lines = c.render(80);
            String line = lines.get(0);
            assertTrue(line.contains("<K:K>"));
            assertTrue(line.contains("<D:D>"));
        }

        @Test
        void invalidateClearsCache() {
            KeybindingsComponent c = new KeybindingsComponent(List.of(new KeybindingsComponent.Keybinding("K", "D")));
            List<String> first = c.render(80);
            c.invalidate();
            List<String> second = c.render(80);
            assertNotSame(first, second);
        }
    }
}
