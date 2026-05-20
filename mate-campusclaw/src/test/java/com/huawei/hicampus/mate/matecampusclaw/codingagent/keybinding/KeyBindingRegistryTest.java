/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.hicampus.mate.matecampusclaw.codingagent.keybinding;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class KeyBindingRegistryTest {

    private static KeyBindingRegistry initialized() {
        KeyBindingRegistry r = new KeyBindingRegistry();

        // Manually invoke init via reflection — @PostConstruct isn't run outside Spring
        try {
            var m = KeyBindingRegistry.class.getDeclaredMethod("init");
            m.setAccessible(true);
            m.invoke(r);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
        return r;
    }

    @Nested
    class Defaults {

        @Test
        void registersBuiltinBindings() {
            KeyBindingRegistry r = initialized();
            assertThat(r.get("app.interrupt")).isPresent();
            assertThat(r.get("app.clear")).isPresent();
            assertThat(r.get("app.exit")).isPresent();
            assertThat(r.getAll()).hasSizeGreaterThanOrEqualTo(10);
        }

        @Test
        void getKeyResolvesAction() {
            KeyBindingRegistry r = initialized();
            assertThat(r.getKey("app.exit")).contains("ctrl+d");
        }

        @Test
        void findActionForRegisteredKey() {
            KeyBindingRegistry r = initialized();
            assertThat(r.findAction("ctrl+d")).contains("app.exit");
        }

        @Test
        void findActionCaseInsensitive() {
            KeyBindingRegistry r = initialized();
            assertThat(r.findAction("CTRL+D")).contains("app.exit");
        }

        @Test
        void missingActionReturnsEmpty() {
            KeyBindingRegistry r = initialized();
            assertThat(r.get("app.does.not.exist")).isEmpty();
            assertThat(r.getKey("app.does.not.exist")).isEmpty();
            assertThat(r.findAction("ctrl+q")).isEmpty();
        }
    }

    @Nested
    class Mutation {

        @Test
        void registerOverridesBinding() {
            KeyBindingRegistry r = initialized();
            r.register(new KeyBinding("app.exit", "ctrl+q", "Quit"));
            assertThat(r.getKey("app.exit")).contains("ctrl+q");
        }

        @Test
        void getAllReturnsUnmodifiable() {
            KeyBindingRegistry r = initialized();
            org.junit.jupiter.api.Assertions.assertThrows(
                    UnsupportedOperationException.class, () -> r.getAll().add(null));
        }
    }
}
