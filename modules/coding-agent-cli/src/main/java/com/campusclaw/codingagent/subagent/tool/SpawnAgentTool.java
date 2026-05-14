/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.campusclaw.codingagent.subagent.tool;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import com.campusclaw.agent.subagent.SubAgentBackend;
import com.campusclaw.agent.subagent.SubAgentEvent;
import com.campusclaw.agent.subagent.SubAgentException;
import com.campusclaw.agent.subagent.SubAgentRegistry;
import com.campusclaw.agent.subagent.SubAgentSession;
import com.campusclaw.agent.tool.AgentTool;
import com.campusclaw.agent.tool.AgentToolResult;
import com.campusclaw.agent.tool.AgentToolUpdateCallback;
import com.campusclaw.agent.tool.CancellationToken;
import com.campusclaw.ai.types.TextContent;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Tool that lets the main agent open a sub-agent session and delegate a task to it.
 *
 * <p>Routes through {@link SubAgentRegistry} so the actual backend (ACP stdio, HTTP, etc.) stays
 * pluggable.
 *
 * @version [br_eCampusCore 25.1.0_Next, 2026/05/12]
 * @since [br_eCampusCore 25.1.0_Next]
 */
@Component
public class SpawnAgentTool implements AgentTool {

    private static final Logger log = LoggerFactory.getLogger(SpawnAgentTool.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String DEFAULT_PARENT_AGENT_ID = "main";
    private static final long DEFAULT_TIMEOUT_SECONDS = 600L;

    private final SubAgentRegistry registry;

    public SpawnAgentTool(SubAgentRegistry registry) {
        this.registry = registry;
    }

    @Override
    public String name() {
        return "spawn_agent";
    }

    @Override
    public String label() {
        return "Spawn Agent";
    }

    @Override
    public String description() {
        return "Delegate a task to another coding agent (Claude Code, Codex, or a remote HTTP agent) "
                + "over the Agent Client Protocol. The sub-agent runs in an isolated session; its "
                + "streaming output, tool calls, and final answer are forwarded back to this conversation.";
    }

    @Override
    public JsonNode parameters() {
        ObjectNode props = MAPPER.createObjectNode();

        props.set(
                "backend",
                MAPPER.createObjectNode()
                        .put("type", "string")
                        .put("description", "Sub-agent backend id (e.g. claude-code). Must be registered."));

        props.set(
                "task",
                MAPPER.createObjectNode().put("type", "string").put("description", "Prompt to send to the sub-agent."));

        props.set(
                "cwd",
                MAPPER.createObjectNode()
                        .put("type", "string")
                        .put("description", "Working directory for the sub-agent (optional)."));

        props.set(
                "model",
                MAPPER.createObjectNode()
                        .put("type", "string")
                        .put("description", "Model override forwarded to the backend (optional)."));

        props.set(
                "thinking",
                MAPPER.createObjectNode()
                        .put("type", "string")
                        .put("description", "Reasoning effort override forwarded to the backend (optional)."));

        props.set(
                "timeout_seconds",
                MAPPER.createObjectNode()
                        .put("type", "integer")
                        .put("description", "Hard timeout in seconds for the sub-agent turn (default 600)."));

        return MAPPER.createObjectNode()
                .put("type", "object")
                .<ObjectNode>set("properties", props)
                .set("required", MAPPER.createArrayNode().add("backend").add("task"));
    }

    @Override
    public AgentToolResult execute(
            String toolCallId, Map<String, Object> params, CancellationToken signal, AgentToolUpdateCallback onUpdate) {
        String backendId = asString(params.get("backend"));
        String task = asString(params.get("task"));
        if (backendId == null || task == null) {
            return textResult("Error: 'backend' and 'task' are required.");
        }

        SubAgentBackend backend;
        try {
            backend = registry.requireBackend(backendId);
        } catch (SubAgentException ex) {
            return textResult("Error: " + ex.getMessage() + ". Known backends: " + registry.backendIds());
        }

        Duration timeout = resolveTimeout(params.get("timeout_seconds"));
        String cwd = asString(params.get("cwd"));
        if (cwd == null || cwd.isBlank()) {
            cwd = System.getProperty("user.dir");
        }
        SubAgentBackend.OpenRequest openRequest = new SubAgentBackend.OpenRequest(
                DEFAULT_PARENT_AGENT_ID,
                cwd,
                asString(params.get("model")),
                asString(params.get("thinking")),
                Map.of(),
                timeout);

        return runSession(backend, openRequest, task, signal, onUpdate, timeout);
    }

    private AgentToolResult runSession(
            SubAgentBackend backend,
            SubAgentBackend.OpenRequest request,
            String task,
            CancellationToken signal,
            AgentToolUpdateCallback onUpdate,
            Duration timeout) {
        SubAgentSession session;
        try {
            session = backend.open(request);
        } catch (SubAgentException ex) {
            return textResult("Error: failed to open sub-agent session: " + ex.getMessage());
        }
        registry.trackSession(session);
        try {
            return streamPrompt(backend, session, task, signal, onUpdate, timeout);
        } finally {
            registry.forgetSession(session);
            try {
                backend.close(session, "tool-finished");
            } catch (RuntimeException ex) {
                log.debug("close failed for {}: {}", session.keyString(), ex.toString());
            }
        }
    }

    private AgentToolResult streamPrompt(
            SubAgentBackend backend,
            SubAgentSession session,
            String task,
            CancellationToken signal,
            AgentToolUpdateCallback onUpdate,
            Duration timeout) {
        var transcript = new StringBuilder();
        var stopReason = new AtomicReference<SubAgentEvent.StopReason>();
        var errorRef = new AtomicReference<SubAgentEvent.Error>();
        var done = new CountDownLatch(1);

        var cancelledByParent = new java.util.concurrent.atomic.AtomicBoolean(false);
        var subscription = backend.prompt(session, task, signal)
                .subscribe(
                        event -> handleEvent(event, transcript, stopReason, errorRef, onUpdate),
                        err -> {
                            errorRef.compareAndSet(null, new SubAgentEvent.Error("STREAM", err.getMessage(), false));
                            done.countDown();
                        },
                        done::countDown);

        if (signal != null) {
            signal.onCancel(() -> {
                cancelledByParent.set(true);
                try {
                    backend.cancel(session, "parent-cancelled");
                } catch (RuntimeException ignored) {
                    // best-effort
                }
                done.countDown();
            });
        }

        try {
            if (!done.await(timeout.toSeconds() + 5L, TimeUnit.SECONDS)) {
                backend.cancel(session, "tool-timeout");
                return textResult("Error: sub-agent turn timed out after " + timeout.toSeconds() + "s.");
            }
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            backend.cancel(session, "interrupted");
            return textResult("Error: interrupted while waiting for sub-agent.");
        } finally {
            subscription.dispose();
        }

        if (cancelledByParent.get()) {
            return textResult("Sub-agent cancelled by parent.");
        }
        if (errorRef.get() != null) {
            return textResult("Error: " + errorRef.get().message());
        }
        return buildResult(transcript.toString(), stopReason.get());
    }

    private static void handleEvent(
            SubAgentEvent event,
            StringBuilder transcript,
            AtomicReference<SubAgentEvent.StopReason> stopReason,
            AtomicReference<SubAgentEvent.Error> errorRef,
            AgentToolUpdateCallback onUpdate) {
        if (event instanceof SubAgentEvent.TextDelta delta && delta.stream() == SubAgentEvent.Stream.OUTPUT) {
            transcript.append(delta.text());
            onUpdate.onUpdate(new AgentToolResult(List.of(new TextContent(transcript.toString())), null));
        } else if (event instanceof SubAgentEvent.Done d) {
            stopReason.set(d.stopReason());
        } else if (event instanceof SubAgentEvent.Error err) {
            errorRef.compareAndSet(null, err);
        }
    }

    private static AgentToolResult buildResult(String transcript, SubAgentEvent.StopReason stopReason) {
        String body = transcript.isBlank() ? "(sub-agent returned no output)" : transcript;
        String suffix = stopReason == null ? "" : "\n\n[stop=" + stopReason + "]";
        return new AgentToolResult(List.of(new TextContent(body + suffix)), null);
    }

    private static Duration resolveTimeout(Object raw) {
        if (raw instanceof Number n) {
            long seconds = n.longValue();
            if (seconds > 0L) {
                return Duration.ofSeconds(seconds);
            }
        }
        if (raw instanceof String s && !s.isBlank()) {
            try {
                long seconds = Long.parseLong(s.trim());
                if (seconds > 0L) {
                    return Duration.ofSeconds(seconds);
                }
            } catch (NumberFormatException ignored) {
                // fall through to default
            }
        }
        return Duration.ofSeconds(DEFAULT_TIMEOUT_SECONDS);
    }

    private static String asString(Object value) {
        if (value == null) {
            return null;
        }
        String s = value.toString().trim();
        return s.isEmpty() ? null : s;
    }

    private static AgentToolResult textResult(String text) {
        return new AgentToolResult(List.of(new TextContent(text)), null);
    }
}
