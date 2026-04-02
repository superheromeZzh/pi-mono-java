package com.huawei.hicampus.mate.matecampusclaw.codingagent.auth;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

import jakarta.annotation.Nullable;

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
@JsonSubTypes({
    @JsonSubTypes.Type(value = Credential.ApiKey.class, name = "api_key"),
    @JsonSubTypes.Type(value = Credential.OAuth.class, name = "oauth")
})
@JsonIgnoreProperties(ignoreUnknown = true)
public sealed interface Credential {
    record ApiKey(@JsonProperty("key") String key) implements Credential {}
    record OAuth(
        @JsonProperty("accessToken") String accessToken,
        @JsonProperty("refreshToken") @Nullable String refreshToken,
        @JsonProperty("expiresAt") @Nullable Long expiresAt
    ) implements Credential {}
}
