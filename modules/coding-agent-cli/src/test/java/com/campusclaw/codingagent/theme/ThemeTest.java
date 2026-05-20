/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.campusclaw.codingagent.theme;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class ThemeTest {

    @Nested
    class ToAnsi {

        @Test
        void hexColor() {
            assertThat(Theme.toAnsi("#FF8000")).isEqualTo("\033[38;2;255;128;0m");
        }

        @Test
        void hexLowercase() {
            assertThat(Theme.toAnsi("#0a1b2c")).isEqualTo("\033[38;2;10;27;44m");
        }

        @Test
        void standardNamedColors() {
            assertThat(Theme.toAnsi("red")).isEqualTo("\033[31m");
            assertThat(Theme.toAnsi("green")).isEqualTo("\033[32m");
            assertThat(Theme.toAnsi("blue")).isEqualTo("\033[34m");
            assertThat(Theme.toAnsi("white")).isEqualTo("\033[37m");
        }

        @Test
        void brightNamedColors() {
            assertThat(Theme.toAnsi("bright_red")).isEqualTo("\033[91m");
            assertThat(Theme.toAnsi("bright_white")).isEqualTo("\033[97m");
        }

        @Test
        void grayAliases() {
            assertThat(Theme.toAnsi("gray")).isEqualTo("\033[90m");
            assertThat(Theme.toAnsi("grey")).isEqualTo("\033[90m");
            assertThat(Theme.toAnsi("bright_black")).isEqualTo("\033[90m");
        }

        @Test
        void caseInsensitive() {
            assertThat(Theme.toAnsi("RED")).isEqualTo("\033[31m");
        }

        @Test
        void unknownReturnsEmpty() {
            assertThat(Theme.toAnsi("not-a-color")).isEmpty();
        }

        @Test
        void shortHexReturnsEmpty() {
            // Hex must be #RRGGBB (7 chars); shorter forms aren't supported.
            assertThat(Theme.toAnsi("#fff")).isEmpty();
        }
    }

    @Nested
    class Ansi {

        @Test
        void resolvesKeyToColor() {
            Theme theme = new Theme("t", Map.of(Theme.PRIMARY, "red"), null);
            assertThat(theme.ansi(Theme.PRIMARY)).isEqualTo("\033[31m");
        }

        @Test
        void missingKeyReturnsEmpty() {
            Theme theme = new Theme("t", Map.of(), null);
            assertThat(theme.ansi(Theme.PRIMARY)).isEmpty();
        }
    }
}
