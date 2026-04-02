package com.campusclaw.codingagent.mode.server;

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
import com.campusclaw.codingagent.prompt.SystemPromptBuilder;
import com.campusclaw.codingagent.session.AgentSession;
import com.campusclaw.codingagent.session.SessionConfig;
import com.campusclaw.codingagent.skill.SkillExpander;
import com.campusclaw.codingagent.skill.SkillLoader;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Manages multiple {@link AgentSession} instances keyed by conversation ID.
 * Sessions are created on demand and evicted after an idle timeout.
 */
public class SessionPool {

    private static final Logger log = LoggerFactory.getLogger(SessionPool.class);
    private static final long IDLE_TIMEOUT_MINUTES = 30;

    private final CampusClawAiService aiService;
    private final ModelRegistry modelRegistry;
    private final SystemPromptBuilder promptBuilder;
    private final List<AgentTool> tools;
    private final SessionConfig baseConfig;

    private final Map<String, Entry> sessions = new ConcurrentHashMap<>();
    private final ScheduledExecutorService cleaner;

    record Entry(AgentSession session, long lastAccess) {}

    public SessionPool(
            CampusClawAiService aiService,
            ModelRegistry modelRegistry,
            SystemPromptBuilder promptBuilder,
            List<AgentTool> tools,
            SessionConfig baseConfig
    ) {
        this.aiService = aiService;
        this.modelRegistry = modelRegistry;
        this.promptBuilder = promptBuilder;
        this.tools = tools;
        this.baseConfig = baseConfig;

        this.cleaner = Executors.newSingleThreadScheduledExecutor(r -> {
            var t = new Thread(r, "session-pool-cleaner");
            t.setDaemon(true);
            return t;
        });
        cleaner.scheduleAtFixedRate(this::evictIdle, IDLE_TIMEOUT_MINUTES, 5, TimeUnit.MINUTES);
    }

    /**
     * Returns an existing session for the given conversation ID, or creates a new one.
     * If conversationId is null, a new session is created with a generated ID.
     *
     * @return a two-element result: [conversationId, session]
     */
    public SessionRef getOrCreate(String conversationId) {
        if (conversationId != null && !conversationId.isBlank()) {
            var entry = sessions.get(conversationId);
            if (entry != null) {
                sessions.put(conversationId, new Entry(entry.session(), now()));
                return new SessionRef(conversationId, entry.session());
            }
        }
        // Create new session
        String id = (conversationId != null && !conversationId.isBlank())
                ? conversationId
                : UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        AgentSession session = createSession();
        sessions.put(id, new Entry(session, now()));
        log.info("Created new conversation: {}", id);
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
            log.info("Removed conversation: {}", conversationId);
            return true;
        }
        return false;
    }

    /** Returns the number of active sessions. */
    public int size() {
        return sessions.size();
    }

    /** Shuts down the cleaner thread. */
    public void shutdown() {
        cleaner.shutdownNow();
    }

    record SessionRef(String conversationId, AgentSession session) {}

    private AgentSession createSession() {
        AgentSession session = new AgentSession(
                aiService, modelRegistry, promptBuilder,
                new SkillLoader(), new SkillExpander(), tools
        );
        session.initialize(baseConfig);
        return session;
    }

    private void evictIdle() {
        long cutoff = now() - TimeUnit.MINUTES.toMillis(IDLE_TIMEOUT_MINUTES);
        sessions.entrySet().removeIf(e -> {
            if (e.getValue().lastAccess() < cutoff && !e.getValue().session().isStreaming()) {
                log.info("Evicted idle conversation: {}", e.getKey());
                return true;
            }
            return false;
        });
    }

    private static long now() {
        return System.currentTimeMillis();
    }
}
