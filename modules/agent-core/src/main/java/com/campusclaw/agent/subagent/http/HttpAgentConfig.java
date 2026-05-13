/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.campusclaw.agent.subagent.http;

import java.net.URI;
import java.time.Duration;
import java.util.Locale;

/**
 * Static configuration for an {@link HttpAgentBackend}.
 *
 * @param id backend id reported through {@code SubAgentBackend.id()}
 * @param baseUri base URI of the remote agent (must include scheme and host)
 * @param authType authentication scheme: bearer, header, or none
 * @param authToken credential value (token for bearer/header schemes)
 * @param authHeaderName header name used when {@code authType == HEADER}
 * @param connectTimeout TCP/TLS connect timeout
 * @param requestTimeout per-call request timeout for non-streaming endpoints
 * @param promptTimeout overall prompt-turn timeout including streaming
 *
 * @version [br_eCampusCore 25.1.0_Next, 2026/05/12]
 * @since [br_eCampusCore 25.1.0_Next]
 */
public record HttpAgentConfig(
        String id,
        URI baseUri,
        AuthType authType,
        String authToken,
        String authHeaderName,
        Duration connectTimeout,
        Duration requestTimeout,
        Duration promptTimeout) {

    /** Authentication schemes supported by {@link HttpAgentBackend}. */
    public enum AuthType {
        NONE,
        BEARER,
        HEADER;

        public static AuthType fromWire(String raw) {
            if (raw == null || raw.isBlank()) {
                return NONE;
            }
            return switch (raw.trim().toLowerCase(Locale.ROOT)) {
                case "bearer" -> BEARER;
                case "header" -> HEADER;
                case "none", "" -> NONE;
                default -> throw new IllegalArgumentException("unknown authType: " + raw);
            };
        }
    }

    public HttpAgentConfig {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("id must not be blank");
        }
        if (baseUri == null) {
            throw new IllegalArgumentException("baseUri must not be null");
        }
        authType = authType == null ? AuthType.NONE : authType;
        connectTimeout = connectTimeout == null ? Duration.ofSeconds(10L) : connectTimeout;
        requestTimeout = requestTimeout == null ? Duration.ofSeconds(30L) : requestTimeout;
        promptTimeout = promptTimeout == null ? Duration.ofMinutes(10L) : promptTimeout;
        if (authType == AuthType.HEADER && (authHeaderName == null || authHeaderName.isBlank())) {
            throw new IllegalArgumentException("authHeaderName is required when authType=HEADER");
        }
        if (authType != AuthType.NONE && (authToken == null || authToken.isBlank())) {
            throw new IllegalArgumentException("authToken is required when authType=" + authType);
        }
    }
}
