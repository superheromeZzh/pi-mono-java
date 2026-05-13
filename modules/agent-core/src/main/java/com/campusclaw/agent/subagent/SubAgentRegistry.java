/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.campusclaw.agent.subagent;

import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

/**
 * Process-wide registry of {@link SubAgentBackend} instances and live {@link SubAgentSession}s.
 *
 * <p>Backends are discovered via Spring (every {@code @Component} that implements
 * {@code SubAgentBackend} is auto-registered). Sessions are tracked so the parent agent's abort
 * can cascade to every open sub-agent.
 *
 * @version [br_eCampusCore 25.1.0_Next, 2026/05/12]
 * @since [br_eCampusCore 25.1.0_Next]
 */
@Component
public class SubAgentRegistry {

    private static final Logger log = LoggerFactory.getLogger(SubAgentRegistry.class);

    private final Map<String, SubAgentBackend> backendsById = new ConcurrentHashMap<>();
    private final Map<String, SubAgentSession> sessionsByKey = new ConcurrentHashMap<>();
    private final Map<String, SubAgentSessionRecord> recordsByKey = new ConcurrentHashMap<>();
    private final SubAgentSessionStore sessionStore;

    public SubAgentRegistry(ObjectProvider<SubAgentBackend> backends) {
        this(backends, SubAgentSessionStore.NOOP);
    }

    @org.springframework.beans.factory.annotation.Autowired
    public SubAgentRegistry(ObjectProvider<SubAgentBackend> backends, SubAgentSessionStore sessionStore) {
        this.sessionStore = sessionStore == null ? SubAgentSessionStore.NOOP : sessionStore;
        backends.orderedStream().forEach(this::register);
    }

    public final void register(SubAgentBackend backend) {
        String id = normalize(backend.id());
        if (id.isEmpty()) {
            throw new IllegalArgumentException("backend id must not be blank");
        }
        SubAgentBackend previous = backendsById.put(id, backend);
        if (previous != null && previous != backend) {
            log.warn(
                    "sub-agent backend '{}' was replaced by {}",
                    id,
                    backend.getClass().getName());
        }
    }

    public Optional<SubAgentBackend> backend(String id) {
        return Optional.ofNullable(backendsById.get(normalize(id)));
    }

    public SubAgentBackend requireBackend(String id) {
        return backend(id)
                .orElseThrow(() ->
                        new SubAgentException("BACKEND_MISSING", "sub-agent backend '" + id + "' is not registered"));
    }

    public Collection<String> backendIds() {
        return List.copyOf(backendsById.keySet());
    }

    public void trackSession(SubAgentSession session) {
        trackSession(session, null);
    }

    public void trackSession(SubAgentSession session, String label) {
        sessionsByKey.put(session.keyString(), session);
        var record = SubAgentSessionRecord.opened(session, label);
        recordsByKey.put(session.keyString(), record);
        try {
            sessionStore.save(record);
        } catch (RuntimeException ex) {
            log.debug("session store save failed for {}: {}", session.keyString(), ex.toString());
        }
    }

    public void forgetSession(SubAgentSession session) {
        sessionsByKey.remove(session.keyString());
        var record = recordsByKey.remove(session.keyString());
        if (record != null) {
            try {
                sessionStore.save(record.closing());
            } catch (RuntimeException ex) {
                log.debug("session store close-save failed for {}: {}", session.keyString(), ex.toString());
            }
        }
    }

    public Optional<SubAgentSession> session(String key) {
        return Optional.ofNullable(sessionsByKey.get(key));
    }

    public Collection<SubAgentSession> sessions() {
        return List.copyOf(sessionsByKey.values());
    }

    /**
     * Cancel every open session. Intended for parent-agent abort cascading.
     *
     * @param reason human-readable reason propagated to each backend's cancel call
     */
    public void cancelAll(String reason) {
        for (SubAgentSession session : List.copyOf(sessionsByKey.values())) {
            try {
                session.backend().cancel(session, reason);
            } catch (RuntimeException ex) {
                log.warn("cancel failed for {}: {}", session.keyString(), ex.toString());
            }
        }
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }
}
