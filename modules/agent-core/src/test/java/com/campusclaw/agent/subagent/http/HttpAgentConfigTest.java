/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.campusclaw.agent.subagent.http;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.net.URI;
import java.time.Duration;

import org.junit.jupiter.api.Test;

class HttpAgentConfigTest {

    @Test
    void authTypeParsesBearerHeaderNone() {
        assertThat(HttpAgentConfig.AuthType.fromWire("bearer")).isEqualTo(HttpAgentConfig.AuthType.BEARER);
        assertThat(HttpAgentConfig.AuthType.fromWire("HEADER")).isEqualTo(HttpAgentConfig.AuthType.HEADER);
        assertThat(HttpAgentConfig.AuthType.fromWire("none")).isEqualTo(HttpAgentConfig.AuthType.NONE);
        assertThat(HttpAgentConfig.AuthType.fromWire(null)).isEqualTo(HttpAgentConfig.AuthType.NONE);
        assertThatThrownBy(() -> HttpAgentConfig.AuthType.fromWire("oauth2"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsBearerWithoutToken() {
        assertThatThrownBy(() -> new HttpAgentConfig(
                        "id",
                        URI.create("https://example.com"),
                        HttpAgentConfig.AuthType.BEARER,
                        null,
                        null,
                        null,
                        null,
                        null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("authToken");
    }

    @Test
    void rejectsHeaderWithoutHeaderName() {
        assertThatThrownBy(() -> new HttpAgentConfig(
                        "id",
                        URI.create("https://example.com"),
                        HttpAgentConfig.AuthType.HEADER,
                        "secret",
                        null,
                        null,
                        null,
                        null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("authHeaderName");
    }

    @Test
    void appliesDefaults() {
        var config = new HttpAgentConfig(
                "id", URI.create("https://example.com"), HttpAgentConfig.AuthType.NONE, null, null, null, null, null);
        assertThat(config.connectTimeout()).isEqualTo(Duration.ofSeconds(10L));
        assertThat(config.requestTimeout()).isEqualTo(Duration.ofSeconds(30L));
        assertThat(config.promptTimeout()).isEqualTo(Duration.ofMinutes(10L));
    }
}
