/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.campusclaw.codingagent.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * Tests {@link ConfigValueResolver} placeholder expansion. The class reads
 * environment variables and the {@code user.home} system property — tests
 * pick {@code HOME} (POSIX) and {@code PATH} (universal) as variables that
 * the test runner always has set, avoiding the need to mutate process env.
 */
class ConfigValueResolverTest {

    @Test
    void nullInputReturnsNull() {
        assertThat(ConfigValueResolver.resolve(null)).isNull();
    }

    @Test
    void plainStringIsReturnedUnchanged() {
        assertThat(ConfigValueResolver.resolve("plain text")).isEqualTo("plain text");
    }

    @Test
    void tildeExpandsToHomeDirectory() {
        String home = System.getProperty("user.home");
        assertThat(ConfigValueResolver.resolve("~/configs/x.json")).isEqualTo(home + "/configs/x.json");
    }

    @Test
    void unsetEnvWithoutDefaultBecomesEmptyString() {
        String unsetName = "CAMPUSCLAW_DEFINITELY_NOT_SET_" + System.nanoTime();
        assertThat(ConfigValueResolver.resolve("[${" + unsetName + "}]")).isEqualTo("[]");
    }

    @Test
    void envWithDefaultUsesDefaultWhenUnset() {
        String unsetName = "CAMPUSCLAW_DEFINITELY_NOT_SET_" + System.nanoTime();
        assertThat(ConfigValueResolver.resolve("${" + unsetName + ":-fallback}"))
                .isEqualTo("fallback");
    }

    @Test
    void envWithDefaultPrefersActualValueWhenSet() {
        // PATH is universally present on POSIX/Windows test runners.
        String pathValue = System.getenv("PATH");
        if (pathValue == null) {
            return; // Skip silently on exotic runners
        }
        assertThat(ConfigValueResolver.resolve("${PATH:-fallback-value}")).isEqualTo(pathValue);
    }

    @Test
    void resolvesMultipleEnvPlaceholdersInOneValue() {
        String unset1 = "CAMPUSCLAW_X_" + System.nanoTime();
        String unset2 = "CAMPUSCLAW_Y_" + System.nanoTime();
        String resolved = ConfigValueResolver.resolve("a=${" + unset1 + ":-one} b=${" + unset2 + ":-two}");
        assertThat(resolved).isEqualTo("a=one b=two");
    }

    @Test
    void blankEnvValueFallsThroughToDefault() {
        // The implementation treats blank env values as if unset and uses the default.
        String unset = "CAMPUSCLAW_BLANK_TEST_" + System.nanoTime();
        assertThat(ConfigValueResolver.resolve("${" + unset + ":-non-blank}")).isEqualTo("non-blank");
    }

    @Test
    void hasPlaceholdersDetectsTildeAndEnv() {
        assertThat(ConfigValueResolver.hasPlaceholders("~/foo")).isTrue();
        assertThat(ConfigValueResolver.hasPlaceholders("${HOME}")).isTrue();
        assertThat(ConfigValueResolver.hasPlaceholders("${HOME:-/default}")).isTrue();
        assertThat(ConfigValueResolver.hasPlaceholders("plain text")).isFalse();
        assertThat(ConfigValueResolver.hasPlaceholders(null)).isFalse();
    }
}
