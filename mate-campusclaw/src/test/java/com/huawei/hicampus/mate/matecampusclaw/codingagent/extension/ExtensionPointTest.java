/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.hicampus.mate.matecampusclaw.codingagent.extension;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ExtensionPointTest {

    @Test
    void allValuesPresent() {
        assertThat(ExtensionPoint.values())
                .containsExactlyInAnyOrder(
                        ExtensionPoint.TOOL,
                        ExtensionPoint.COMMAND,
                        ExtensionPoint.BEFORE_TOOL_CALL,
                        ExtensionPoint.AFTER_TOOL_CALL,
                        ExtensionPoint.CONTEXT_TRANSFORMER,
                        ExtensionPoint.EVENT_LISTENER);
    }

    @Test
    void valueOfRoundtrip() {
        for (ExtensionPoint p : ExtensionPoint.values()) {
            assertThat(ExtensionPoint.valueOf(p.name())).isEqualTo(p);
        }
    }
}
