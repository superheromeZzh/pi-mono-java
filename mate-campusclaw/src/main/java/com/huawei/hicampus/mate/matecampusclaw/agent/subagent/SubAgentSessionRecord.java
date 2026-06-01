/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.hicampus.mate.matecampusclaw.agent.subagent;

import java.time.Instant;

/**
 * Persisted metadata snapshot for a {@link SubAgentSession}.
 *
 * <p>Captured at open and updated on close. First-class wire shape so the JSON written under
 * {@code ~/.campusclaw/agent/subagents/} stays stable across releases.
 *
 * @param sessionKey canonical {@link SubAgentSessionKey#asString()}
 * @param backendId backend id
 * @param runtimeSessionId backend-local session id (nullable)
 * @param label optional human-readable label
 * @param createdAt open timestamp
 * @param closedAt close timestamp (null while open)
 *
 * @version [br_eCampusCore 25.1.0_Next, 2026/05/12]
 * @since [br_eCampusCore 25.1.0_Next]
 */
public record SubAgentSessionRecord(
        String sessionKey,
        String backendId,
        String runtimeSessionId,
        String label,
        Instant createdAt,
        Instant closedAt) {

    public static SubAgentSessionRecord opened(SubAgentSession session, String label) {
        return new SubAgentSessionRecord(
                session.keyString(), session.key().backendId(), session.runtimeSessionId(), label, Instant.now(), null);
    }

    public SubAgentSessionRecord closing() {
        return new SubAgentSessionRecord(sessionKey, backendId, runtimeSessionId, label, createdAt, Instant.now());
    }
}
