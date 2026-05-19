/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.campusclaw.codingagent.auth;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

import jakarta.annotation.Nullable;

/**
 * Persisted authentication material for an LLM provider, serialized polymorphically by
 * Jackson via a {@code type} discriminator. Two variants are supported: {@link ApiKey} for
 * static keys and {@link OAuth} for tokens with optional refresh metadata.
 *
 * @version [br_eCampusCore 25.1.0_Next, 2026/05/13]
 * @since [br_eCampusCore 25.1.0_Next]
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
@JsonSubTypes({
    @JsonSubTypes.Type(value = Credential.ApiKey.class, name = "api_key"),
    @JsonSubTypes.Type(value = Credential.OAuth.class, name = "oauth")
})
@JsonIgnoreProperties(ignoreUnknown = true)
public sealed interface Credential {
    @SuppressWarnings("checkstyle:top_class_comment")
    record ApiKey(@JsonProperty("key") String key) implements Credential {}

    @SuppressWarnings("checkstyle:top_class_comment")
    record OAuth(
            @JsonProperty("accessToken") String accessToken,
            @JsonProperty("refreshToken") @Nullable String refreshToken,
            @JsonProperty("expiresAt") @Nullable Long expiresAt)
            implements Credential {}
}
