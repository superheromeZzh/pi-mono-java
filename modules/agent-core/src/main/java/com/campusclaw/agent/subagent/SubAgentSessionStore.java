/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.campusclaw.agent.subagent;

import java.util.List;
import java.util.Optional;

/**
 * Persistence backend for {@link SubAgentSessionRecord}s. The default file-backed implementation
 * writes one JSON document per session under {@code ~/.campusclaw/agent/subagents/<backend>/}.
 *
 * <p>First version only persists metadata for audit/inspection; sessions are not auto-resumed on
 * startup.
 *
 * @version [br_eCampusCore 25.1.0_Next, 2026/05/12]
 * @since [br_eCampusCore 25.1.0_Next]
 */
public interface SubAgentSessionStore {

    void save(SubAgentSessionRecord record);

    Optional<SubAgentSessionRecord> load(String sessionKey);

    List<SubAgentSessionRecord> listOpen();

    /**
     * No-op store used when persistence is intentionally disabled.
     */
    SubAgentSessionStore NOOP = new SubAgentSessionStore() {

        @Override
        public void save(SubAgentSessionRecord record) {}

        @Override
        public Optional<SubAgentSessionRecord> load(String sessionKey) {
            return Optional.empty();
        }

        @Override
        public List<SubAgentSessionRecord> listOpen() {
            return List.of();
        }
    };
}
