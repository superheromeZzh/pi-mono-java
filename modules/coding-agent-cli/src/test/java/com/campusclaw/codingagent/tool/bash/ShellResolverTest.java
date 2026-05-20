/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.campusclaw.codingagent.tool.bash;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class ShellResolverTest {

    @BeforeEach
    void resetCache() {
        ShellResolver.resetCacheForTesting();
    }

    @Nested
    class Resolution {

        @Test
        void resolvesAShellOnAnyPosixSystem() {
            ShellResolver.ShellConfig cfg = ShellResolver.resolve();
            assertThat(cfg).isNotNull();
            assertThat(cfg.shell()).isNotBlank();
            assertThat(cfg.args()).contains("-c");
        }

        @Test
        void cacheReturnsSameInstance() {
            ShellResolver.ShellConfig first = ShellResolver.resolve();
            ShellResolver.ShellConfig second = ShellResolver.resolve();
            assertThat(second).isSameAs(first);
        }
    }

    @Nested
    class ShellConfigRecord {

        @Test
        void argsCopiedImmutable() {
            ShellResolver.ShellConfig cfg = new ShellResolver.ShellConfig("/bin/sh", java.util.List.of("-c"));
            assertThat(cfg.shell()).isEqualTo("/bin/sh");
            org.junit.jupiter.api.Assertions.assertThrows(
                    UnsupportedOperationException.class, () -> cfg.args().add("-x"));
        }
    }
}
