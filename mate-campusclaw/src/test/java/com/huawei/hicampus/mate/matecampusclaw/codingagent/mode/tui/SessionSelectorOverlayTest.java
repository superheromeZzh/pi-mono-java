/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.hicampus.mate.matecampusclaw.codingagent.mode.tui;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link SessionSelectorOverlay}. The overlay reads from the
 * static {@code AppPaths.SESSIONS_DIR}, so for a fresh / unknown cwd we expect
 * the empty-state branch. Tests verify the empty render, focus state, escape
 * handling, and that the constructor never throws when the session dir is
 * absent or unreadable.
 */
class SessionSelectorOverlayTest {

    private static SessionSelectorOverlay forCwd(String cwd) {
        return new SessionSelectorOverlay(cwd);
    }

    @Nested
    class EmptyState {

        @Test
        void unknownCwdYieldsEmptyOverlay() {
            // A cwd that won't have sessions stored.
            SessionSelectorOverlay overlay = forCwd("/tmp/__no_such_cwd_" + System.nanoTime());
            assertThat(overlay.isEmpty()).isTrue();
        }

        @Test
        void emptyOverlayRendersNoSessionsMessage() {
            SessionSelectorOverlay overlay = forCwd("/tmp/__no_such_cwd_" + System.nanoTime());
            List<String> lines = overlay.render(80);
            String joined = String.join("\n", lines);
            assertThat(joined).contains("Resume Session").contains("No sessions found");
        }
    }

    @Nested
    class Focus {

        @Test
        void focusFlagToggles() {
            SessionSelectorOverlay overlay = forCwd("/tmp/__no_such_cwd_" + System.nanoTime());
            assertThat(overlay.isFocused()).isFalse();
            overlay.setFocused(true);
            assertThat(overlay.isFocused()).isTrue();
        }
    }

    @Nested
    class Input {

        @Test
        void escapeInvokesCancelCallback() {
            SessionSelectorOverlay overlay = forCwd("/tmp/__no_such_cwd_" + System.nanoTime());
            AtomicInteger cancelCount = new AtomicInteger();
            overlay.setOnCancel(cancelCount::incrementAndGet);
            overlay.handleInput("\033");
            assertThat(cancelCount.get()).isEqualTo(1);
        }

        @Test
        void ctrlCAlsoCancels() {
            SessionSelectorOverlay overlay = forCwd("/tmp/__no_such_cwd_" + System.nanoTime());
            AtomicInteger cancelCount = new AtomicInteger();
            overlay.setOnCancel(cancelCount::incrementAndGet);
            overlay.handleInput("\003");
            assertThat(cancelCount.get()).isEqualTo(1);
        }

        @Test
        void otherKeysDoNotCancel() {
            SessionSelectorOverlay overlay = forCwd("/tmp/__no_such_cwd_" + System.nanoTime());
            AtomicInteger cancelCount = new AtomicInteger();
            overlay.setOnCancel(cancelCount::incrementAndGet);
            overlay.handleInput("\033[A");
            assertThat(cancelCount.get()).isZero();
        }
    }

    @Nested
    class Mutation {

        @Test
        void invalidateClearsCacheWithoutThrowing() {
            SessionSelectorOverlay overlay = forCwd("/tmp/__no_such_cwd_" + System.nanoTime());
            overlay.render(40);
            overlay.invalidate();

            // Subsequent render still works
            assertThat(overlay.render(40)).isNotEmpty();
        }
    }
}
