/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.campusclaw.agent.subagent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class SubAgentSessionKeyTest {

    @Test
    void newKeyHasCanonicalFormat() {
        var key = SubAgentSessionKey.newKey("Main", "Claude-Code");
        assertThat(key.parentAgentId()).isEqualTo("main");
        assertThat(key.backendId()).isEqualTo("claude-code");
        assertThat(key.uuid()).hasSize(36);
        assertThat(key.asString()).startsWith("agent:main:claude-code:");
    }

    @Test
    void parseRoundTripsAsString() {
        var key = SubAgentSessionKey.newKey("main", "claude-code");
        var parsed = SubAgentSessionKey.parse(key.asString());
        assertThat(parsed).isEqualTo(key);
    }

    @Test
    void parseRejectsMalformed() {
        assertThatThrownBy(() -> SubAgentSessionKey.parse("not-a-session"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> SubAgentSessionKey.parse(null)).isInstanceOf(IllegalArgumentException.class);
    }
}
