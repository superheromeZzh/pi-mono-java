/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.campusclaw.codingagent.mode.server;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import com.campusclaw.agent.tool.AgentTool;
import com.campusclaw.ai.CampusClawAiService;
import com.campusclaw.ai.model.ModelRegistry;
import com.campusclaw.ai.types.Message;
import com.campusclaw.codingagent.config.AppPaths;
import com.campusclaw.codingagent.prompt.SystemPromptBuilder;
import com.campusclaw.codingagent.session.AgentSession;
import com.campusclaw.codingagent.session.SessionConfig;
import com.campusclaw.codingagent.session.SessionManager;
import com.campusclaw.codingagent.skill.SandboxSkillParser;
import com.campusclaw.codingagent.skill.SkillExpander;
import com.campusclaw.codingagent.skill.SkillLoader;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Manages multiple {@link AgentSession} instances keyed by conversation ID.
 * Sessions are created on demand and evicted after an idle timeout.
 *
 * <p>When persistence is enabled, each session is backed by a {@link SessionManager}
 * that writes JSONL to {@code ~/.campusclaw/agent/sessions/--<encoded-cwd>--/<id>.jsonl}.
 * The JSONL filename equals the conversation ID, so reconnects with the same
 * {@code conversation_id} after eviction or process restart resume from disk.
 *
 * @version [br_eCampusCore 25.1.0_Next, 2026/05/06]
 * @since [br_eCampusCore 25.1.0_Next]
 */
public class SessionPool {

    private static final Logger log = LoggerFactory.getLogger(SessionPool.class);
    private static final long IDLE_TIMEOUT_MINUTES = 30L;

    private final CampusClawAiService aiService;
    private final ModelRegistry modelRegistry;
    private final SystemPromptBuilder promptBuilder;
    private final List<AgentTool> tools;
    private final SessionConfig baseConfig;
    private final SandboxSkillParser sandboxParser;
    private final boolean useSandbox;
    private final boolean persistenceEnabled;
    private final String serverCwd;

    private final Map<String, Entry> sessions = new ConcurrentHashMap<>();
    private final ScheduledExecutorService cleaner;

    record Entry(AgentSession session, long lastAccess) {}

    @SuppressWarnings("checkstyle:java_doc_format_missing")
    public SessionPool(
            CampusClawAiService aiService,
            ModelRegistry modelRegistry,
            SystemPromptBuilder promptBuilder,
            List<AgentTool> tools,
            SessionConfig baseConfig) {
        this(aiService, modelRegistry, promptBuilder, tools, baseConfig, null, false, true);
    }

    @SuppressWarnings("checkstyle:java_doc_format_missing")
    public SessionPool(
            CampusClawAiService aiService,
            ModelRegistry modelRegistry,
            SystemPromptBuilder promptBuilder,
            List<AgentTool> tools,
            SessionConfig baseConfig,
            SandboxSkillParser sandboxParser,
            boolean useSandbox) {
        this(aiService, modelRegistry, promptBuilder, tools, baseConfig, sandboxParser, useSandbox, true);
    }

    @SuppressWarnings("checkstyle:java_doc_format_missing")
    public SessionPool(
            CampusClawAiService aiService,
            ModelRegistry modelRegistry,
            SystemPromptBuilder promptBuilder,
            List<AgentTool> tools,
            SessionConfig baseConfig,
            SandboxSkillParser sandboxParser,
            boolean useSandbox,
            boolean persistenceEnabled) {
        this.aiService = aiService;
        this.modelRegistry = modelRegistry;
        this.promptBuilder = promptBuilder;
        this.tools = tools;
        this.baseConfig = baseConfig;
        this.sandboxParser = sandboxParser;
        this.useSandbox = useSandbox;
        this.persistenceEnabled = persistenceEnabled;
        this.serverCwd = System.getProperty("user.dir");

        this.cleaner = Executors.newSingleThreadScheduledExecutor(r -> {
            var t = new Thread(r, "session-pool-cleaner");
            t.setDaemon(true);
            return t;
        });
        cleaner.scheduleAtFixedRate(this::evictIdle, IDLE_TIMEOUT_MINUTES, 5, TimeUnit.MINUTES);
    }

    /**
     * Returns an existing session for the given conversation ID, or creates a new one.
     *
     * <p>When persistence is enabled and the conversation ID maps to an existing
     * JSONL file on disk, the session is restored from disk before being returned.
     * If conversationId is null, a new session is created with a generated ID.
     *
     * @return the resolved session reference
     */
    public SessionRef getOrCreate(String conversationId) {
        if (conversationId != null && !conversationId.isBlank()) {
            var entry = sessions.get(conversationId);
            if (entry != null) {
                sessions.put(conversationId, new Entry(entry.session(), now()));
                return new SessionRef(conversationId, entry.session());
            }
        }

        String id = (conversationId != null && !conversationId.isBlank())
                ? conversationId
                : UUID.randomUUID().toString().replace("-", "").substring(0, 12);

        AgentSession session = createSessionWithPersistence(id);
        sessions.put(id, new Entry(session, now()));
        return new SessionRef(id, session);
    }

    /**
     * Removes a conversation and its session.
     *
     * @return true if the conversation existed and was removed
     */
    public boolean remove(String conversationId) {
        var removed = sessions.remove(conversationId);
        if (removed != null) {
            closeQuietly(removed.session());
            log.info("Removed conversation: {}", conversationId);
            return true;
        }
        return false;
    }

    /**
     * Re-keys a pool entry under a new conversation ID. Used by the WS
     * {@code new_session} command, which rotates the conversation ID so the
     * fresh history goes to its own JSONL file.
     */
    public void rekey(String oldId, String newId) {
        var entry = sessions.remove(oldId);
        if (entry != null) {
            sessions.put(newId, new Entry(entry.session(), now()));
        }
    }

    /** Returns the number of active sessions. */
    public int size() {
        return sessions.size();
    }

    /** Shuts down the cleaner thread and closes any open SessionManager writers. */
    public void shutdown() {
        cleaner.shutdownNow();
        sessions.values().forEach(e -> closeQuietly(e.session()));
        sessions.clear();
    }

    record SessionRef(String conversationId, AgentSession session) {}

    private AgentSession createSessionWithPersistence(String conversationId) {
        AgentSession session = new AgentSession(
                aiService,
                modelRegistry,
                promptBuilder,
                new SkillLoader(sandboxParser, useSandbox),
                new SkillExpander(sandboxParser, useSandbox),
                tools);

        if (!persistenceEnabled) {
            session.initialize(baseConfig);
            log.info("Created new conversation (in-memory only): {}", conversationId);
            return session;
        }

        SessionManager sm = new SessionManager();
        Path file = sessionFilePath(conversationId);
        List<Message> restored = List.of();
        if (Files.exists(file)) {
            restored = sm.loadSession(file);
            log.info("Resumed conversation {} from disk ({} messages)", conversationId, restored.size());
        } else {
            sm.createSession(serverCwd, conversationId);
            log.info("Created new conversation: {}", conversationId);
        }

        session.setSessionManager(sm);
        session.initialize(baseConfig);

        if (!restored.isEmpty()) {
            session.getAgent().clearMessages();
            for (Message m : restored) {
                session.getAgent().getState().appendMessage(m);
            }
        }

        return session;
    }

    /** Returns the JSONL path that {@link SessionManager} would use for this id. */
    private Path sessionFilePath(String sessionId) {
        String safePath = "--" + serverCwd.replaceFirst("^[/\\\\]", "").replaceAll("[/\\\\:]", "-") + "--";
        return AppPaths.SESSIONS_DIR.resolve(safePath).resolve(sessionId + ".jsonl");
    }

    private void evictIdle() {
        long cutoff = now() - TimeUnit.MINUTES.toMillis(IDLE_TIMEOUT_MINUTES);
        sessions.entrySet().removeIf(e -> {
            if (e.getValue().lastAccess() < cutoff && !e.getValue().session().isStreaming()) {
                closeQuietly(e.getValue().session());
                log.info("Evicted idle conversation: {}", e.getKey());
                return true;
            }
            return false;
        });
    }

    private static void closeQuietly(AgentSession session) {
        SessionManager sm = session.getSessionManager();
        if (sm != null) {
            sm.close();
        }
    }

    private static long now() {
        return System.currentTimeMillis();
    }
}
