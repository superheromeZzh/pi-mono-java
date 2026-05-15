/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.hicampus.mate.matecampusclaw.agent.subagent.a2a;

import java.net.URI;
import java.time.Duration;

/**
 * Static configuration for an {@link A2aAgentBackend}.
 *
 * <p>Targets the Huawei mate-service flavour of the A2A protocol: requests are issued as
 * {@code POST {baseUri}/{agentName}} with a JSON-RPC 2.0 envelope, authenticated via the
 * {@code X-HW-ID} and {@code X-HW-APPKEY} headers.
 *
 * @param id backend id reported through {@code SubAgentBackend.id()}
 * @param baseUri base URI of the mate-service A2A endpoint, e.g.
 *     {@code https://host:port/mate-service/v1/a2a/request} (agent name is appended at request
 *     time)
 * @param agentName remote agent identifier appended to {@code baseUri} (e.g. {@code KnowledgeQAAgent})
 * @param hwId value sent in the {@code X-HW-ID} header
 * @param hwAppKey value sent in the {@code X-HW-APPKEY} header
 * @param defaultModel optional value for {@code params.metadata.model}; per-session model overrides
 *     from {@code OpenRequest.model()} take precedence when present
 * @param connectTimeout TCP/TLS connect timeout
 * @param requestTimeout per-call request timeout
 *
 * @version [br_eCampusCore 25.1.0_Next, 2026/05/15]
 * @since [br_eCampusCore 25.1.0_Next]
 */
public record A2aAgentConfig(
        String id,
        URI baseUri,
        String agentName,
        String hwId,
        String hwAppKey,
        String defaultModel,
        Duration connectTimeout,
        Duration requestTimeout) {

    public A2aAgentConfig {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("id must not be blank");
        }
        if (baseUri == null) {
            throw new IllegalArgumentException("baseUri must not be null");
        }
        if (agentName == null || agentName.isBlank()) {
            throw new IllegalArgumentException("agentName must not be blank");
        }
        if (hwId == null || hwId.isBlank()) {
            throw new IllegalArgumentException("hwId must not be blank");
        }
        if (hwAppKey == null || hwAppKey.isBlank()) {
            throw new IllegalArgumentException("hwAppKey must not be blank");
        }
        connectTimeout = connectTimeout == null ? Duration.ofSeconds(10L) : connectTimeout;
        requestTimeout = requestTimeout == null ? Duration.ofSeconds(60L) : requestTimeout;
    }
}
