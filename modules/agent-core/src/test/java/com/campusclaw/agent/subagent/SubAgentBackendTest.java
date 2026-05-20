/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.campusclaw.agent.subagent;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class SubAgentBackendTest {

    @Test
    void permissionOutcomeValues() {
        assertThat(SubAgentBackend.PermissionOutcome.values())
                .containsExactlyInAnyOrder(
                        SubAgentBackend.PermissionOutcome.ALLOW_ONCE,
                        SubAgentBackend.PermissionOutcome.ALLOW_ALWAYS,
                        SubAgentBackend.PermissionOutcome.DENY,
                        SubAgentBackend.PermissionOutcome.CANCEL);
    }

    @Test
    void valueOfRoundtrip() {
        for (SubAgentBackend.PermissionOutcome o : SubAgentBackend.PermissionOutcome.values()) {
            assertThat(SubAgentBackend.PermissionOutcome.valueOf(o.name())).isEqualTo(o);
        }
    }
}
