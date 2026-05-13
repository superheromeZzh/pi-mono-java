/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.hicampus.mate.matecampusclaw.agent.subagent;

import java.time.Duration;
import java.util.Collections;
import java.util.Map;

import com.huawei.hicampus.mate.matecampusclaw.agent.tool.CancellationToken;

import reactor.core.publisher.Flux;

/**
 * Pluggable backend that knows how to open a sub-agent session, drive it through one or more
 * turns, and tear it down. Implementations include native ACP (stdio + ndJSON) and HTTP/WS
 * transports.
 *
 * @version [br_eCampusCore 25.1.0_Next, 2026/05/12]
 * @since [br_eCampusCore 25.1.0_Next]
 */
public interface SubAgentBackend {

    /**
     * Stable id used by {@link SubAgentRegistry} and by the {@code spawn_agent} tool.
     *
     * @return the backend identifier, unique within the registry
     */
    String id();

    SubAgentSession open(OpenRequest request) throws SubAgentException;

    Flux<SubAgentEvent> prompt(SubAgentSession session, String text, CancellationToken signal);

    /**
     * Reply to a previously emitted {@link SubAgentEvent.PermissionRequest}. Default backends that
     * never emit permission requests can leave this unsupported.
     *
     * @param session the sub-agent session that emitted the permission request
     * @param requestId id of the {@link SubAgentEvent.PermissionRequest} being answered
     * @param outcome the resolved {@link PermissionOutcome} to send back
     * @throws UnsupportedOperationException by default; backends that emit permission requests must override
     */
    default void respondPermission(SubAgentSession session, String requestId, PermissionOutcome outcome) {
        throw new UnsupportedOperationException("backend " + id() + " does not support permission responses");
    }

    void cancel(SubAgentSession session, String reason);

    void close(SubAgentSession session, String reason);

    /**
     * @param parentAgentId parent agent identifier (used to build the session key)
     * @param cwd working directory for the sub-agent (nullable, backend may default)
     * @param model optional model override
     * @param thinking optional reasoning effort override
     * @param env extra environment variables for process-based backends
     * @param timeout overall turn timeout
     */
    record OpenRequest(
            String parentAgentId,
            String cwd,
            String model,
            String thinking,
            Map<String, String> env,
            Duration timeout) {

        public OpenRequest {
            if (parentAgentId == null || parentAgentId.isBlank()) {
                throw new IllegalArgumentException("parentAgentId must not be blank");
            }
            env = env == null ? Map.of() : Collections.unmodifiableMap(Map.copyOf(env));
        }
    }

    /** Outcome a parent agent returns in response to a {@link SubAgentEvent.PermissionRequest}. */
    enum PermissionOutcome {
        ALLOW_ONCE,
        ALLOW_ALWAYS,
        DENY,
        CANCEL
    }
}
