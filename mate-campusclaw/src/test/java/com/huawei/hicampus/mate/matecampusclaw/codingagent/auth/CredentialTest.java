/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.hicampus.mate.matecampusclaw.codingagent.auth;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;

import org.junit.jupiter.api.Test;

class CredentialTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    void apiKeyRoundtrip() throws Exception {
        Credential.ApiKey k = new Credential.ApiKey("sk-test");
        String json = MAPPER.writeValueAsString(k);
        assertThat(json).contains("api_key").contains("sk-test");
        Credential decoded = MAPPER.readValue(json, Credential.class);
        assertThat(decoded).isInstanceOf(Credential.ApiKey.class);
        assertThat(((Credential.ApiKey) decoded).key()).isEqualTo("sk-test");
    }

    @Test
    void oauthRoundtrip() throws Exception {
        Credential.OAuth o = new Credential.OAuth("at", "rt", 1700000000L);
        String json = MAPPER.writeValueAsString(o);
        assertThat(json).contains("oauth");
        Credential decoded = MAPPER.readValue(json, Credential.class);
        assertThat(decoded).isInstanceOf(Credential.OAuth.class);
        Credential.OAuth out = (Credential.OAuth) decoded;
        assertThat(out.accessToken()).isEqualTo("at");
        assertThat(out.refreshToken()).isEqualTo("rt");
        assertThat(out.expiresAt()).isEqualTo(1700000000L);
    }

    @Test
    void oauthOptionalsNull() {
        Credential.OAuth o = new Credential.OAuth("at", null, null);
        assertThat(o.refreshToken()).isNull();
        assertThat(o.expiresAt()).isNull();
    }
}
