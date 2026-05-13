/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.campusclaw.agent.subagent;

import java.util.Locale;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Canonical session key for a sub-agent session.
 *
 * <p>Format: {@code agent:<parentAgentId>:<backendId>:<uuid>}. Mirrors the OpenClaw convention so
 * future cross-tool interop stays predictable.
 *
 * @param parentAgentId identifier of the parent agent that spawned the session
 * @param backendId backend id (lower-case, e.g. {@code claude-code})
 * @param uuid session uuid (lower-case canonical form)
 *
 * @version [br_eCampusCore 25.1.0_Next, 2026/05/12]
 * @since [br_eCampusCore 25.1.0_Next]
 */
public record SubAgentSessionKey(String parentAgentId, String backendId, String uuid) {

    private static final Pattern KEY_PATTERN = Pattern.compile("^agent:([^:]+):([^:]+):([0-9a-fA-F-]{36})$");

    public SubAgentSessionKey {
        if (parentAgentId == null || parentAgentId.isBlank()) {
            throw new IllegalArgumentException("parentAgentId must not be blank");
        }
        if (backendId == null || backendId.isBlank()) {
            throw new IllegalArgumentException("backendId must not be blank");
        }
        if (uuid == null || uuid.isBlank()) {
            throw new IllegalArgumentException("uuid must not be blank");
        }
    }

    public static SubAgentSessionKey newKey(String parentAgentId, String backendId) {
        return new SubAgentSessionKey(
                parentAgentId.toLowerCase(Locale.ROOT),
                backendId.toLowerCase(Locale.ROOT),
                UUID.randomUUID().toString());
    }

    public static SubAgentSessionKey parse(String raw) {
        if (raw == null) {
            throw new IllegalArgumentException("session key must not be null");
        }
        var matcher = KEY_PATTERN.matcher(raw);
        if (!matcher.matches()) {
            throw new IllegalArgumentException("invalid session key: " + raw);
        }
        return new SubAgentSessionKey(matcher.group(1), matcher.group(2), matcher.group(3));
    }

    public String asString() {
        return "agent:" + parentAgentId + ":" + backendId + ":" + uuid;
    }

    @Override
    public String toString() {
        return asString();
    }
}
