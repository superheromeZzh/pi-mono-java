/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.campusclaw.agent.subagent;

/**
 * Handle to an open sub-agent session.
 *
 * <p>{@code runtimeSessionId} is the backend-local identifier (for example the ACP
 * {@code sessionId} returned by {@code session/new}); it may be {@code null} for backends that do
 * not expose one.
 *
 * @param key canonical sub-agent session key
 * @param runtimeSessionId backend-local identifier (nullable)
 * @param backend the backend that owns this session
 *
 * @version [br_eCampusCore 25.1.0_Next, 2026/05/12]
 * @since [br_eCampusCore 25.1.0_Next]
 */
public record SubAgentSession(SubAgentSessionKey key, String runtimeSessionId, SubAgentBackend backend) {

    public SubAgentSession {
        if (key == null) {
            throw new IllegalArgumentException("key must not be null");
        }
        if (backend == null) {
            throw new IllegalArgumentException("backend must not be null");
        }
    }

    public String keyString() {
        return key.asString();
    }
}
