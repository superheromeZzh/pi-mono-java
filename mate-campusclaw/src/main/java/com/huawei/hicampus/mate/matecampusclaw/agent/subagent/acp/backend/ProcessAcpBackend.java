/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.hicampus.mate.matecampusclaw.agent.subagent.acp.backend;

import java.io.IOException;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import com.huawei.hicampus.mate.matecampusclaw.agent.subagent.SubAgentBackend;
import com.huawei.hicampus.mate.matecampusclaw.agent.subagent.SubAgentEvent;
import com.huawei.hicampus.mate.matecampusclaw.agent.subagent.SubAgentException;
import com.huawei.hicampus.mate.matecampusclaw.agent.subagent.SubAgentSession;
import com.huawei.hicampus.mate.matecampusclaw.agent.subagent.SubAgentSessionKey;
import com.huawei.hicampus.mate.matecampusclaw.agent.subagent.acp.AcpClient;
import com.huawei.hicampus.mate.matecampusclaw.agent.subagent.acp.AcpProtocol;
import com.huawei.hicampus.mate.matecampusclaw.agent.subagent.approval.ApprovalClassifier;
import com.huawei.hicampus.mate.matecampusclaw.agent.subagent.approval.ApprovalDecision;
import com.huawei.hicampus.mate.matecampusclaw.agent.subagent.approval.ApprovalPolicy;
import com.huawei.hicampus.mate.matecampusclaw.agent.subagent.approval.ParentPermissionDecision;
import com.huawei.hicampus.mate.matecampusclaw.agent.subagent.approval.ParentPermissionRequest;
import com.huawei.hicampus.mate.matecampusclaw.agent.subagent.approval.ParentPermissionResolver;
import com.huawei.hicampus.mate.matecampusclaw.agent.tool.CancellationToken;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

/**
 * Generic {@link SubAgentBackend} that launches an ACP server as a child process and speaks the
 * protocol over its stdio.
 *
 * @version [br_eCampusCore 25.1.0_Next, 2026/05/12]
 * @since [br_eCampusCore 25.1.0_Next]
 */
public class ProcessAcpBackend implements SubAgentBackend {

    private static final Logger log = LoggerFactory.getLogger(ProcessAcpBackend.class);

    private static final Duration DEFAULT_INIT_TIMEOUT = Duration.ofSeconds(30L);

    private static final Duration DEFAULT_ASK_PARENT_TIMEOUT = Duration.ofSeconds(30L);

    private final String id;
    private final Config config;
    private final ObjectMapper mapper;
    private final ApprovalClassifier classifier;
    private final ApprovalPolicy policy;
    private final ParentPermissionResolver parentResolver;
    private final Map<String, RuntimeHandle> handles = new ConcurrentHashMap<>();

    public ProcessAcpBackend(
            String id,
            Config config,
            ObjectMapper mapper,
            ApprovalClassifier classifier,
            ApprovalPolicy policy,
            ParentPermissionResolver parentResolver) {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("id must not be blank");
        }
        this.id = id;
        this.config = config;
        this.mapper = mapper;
        this.classifier = classifier;
        this.policy = policy;
        this.parentResolver = parentResolver;
    }

    @Override
    public String id() {
        return id;
    }

    @Override
    public SubAgentSession open(OpenRequest request) {
        SubAgentSessionKey key = SubAgentSessionKey.newKey(request.parentAgentId(), id);
        Process process = startProcess(request);
        AcpClient client = new AcpClient(mapper, process.getInputStream(), process.getOutputStream());
        client.setPermissionHandler((req, ctx) -> resolvePermission(req, ctx, key));
        client.start("acp-reader-" + id + "-" + key.uuid());
        try {
            client.initialize(config.clientName(), config.clientVersion(), DEFAULT_INIT_TIMEOUT);
            String runtimeSessionId = client.newSession(request.cwd(), DEFAULT_INIT_TIMEOUT);
            handles.put(key.asString(), new RuntimeHandle(process, client));
            return new SubAgentSession(key, runtimeSessionId, this);
        } catch (RuntimeException ex) {
            client.close();
            process.destroyForcibly();
            throw ex;
        }
    }

    @Override
    public Flux<SubAgentEvent> prompt(SubAgentSession session, String text, CancellationToken signal) {
        RuntimeHandle handle = requireHandle(session);
        Sinks.Many<SubAgentEvent> bridge = Sinks.many().multicast().onBackpressureBuffer(1024, false);

        var subscription = handle.client
                .events()
                .subscribe(
                        event -> bridge.tryEmitNext(event),
                        error -> bridge.tryEmitError(error),
                        () -> bridge.tryEmitComplete());

        if (signal != null) {
            signal.onCancel(() -> handle.client.cancel());
        }

        Duration timeout = config.promptTimeout();
        Thread.ofVirtual().name("acp-prompt-" + id + "-" + session.key().uuid()).start(() -> {
            try {
                handle.client.prompt(text, timeout);
            } catch (RuntimeException ex) {
                bridge.tryEmitNext(new SubAgentEvent.Error("ACP_PROMPT", ex.getMessage(), false));
                bridge.tryEmitComplete();
            }
        });

        return bridge.asFlux()
                .takeUntil(event -> event instanceof SubAgentEvent.Done || event instanceof SubAgentEvent.Error)
                .doFinally(signalType -> subscription.dispose());
    }

    @Override
    public void cancel(SubAgentSession session, String reason) {
        RuntimeHandle handle = handles.get(session.keyString());
        if (handle != null) {
            handle.client.cancel();
        }
    }

    @Override
    public void close(SubAgentSession session, String reason) {
        RuntimeHandle handle = handles.remove(session.keyString());
        if (handle == null) {
            return;
        }
        try {
            handle.client.close();
        } catch (RuntimeException ex) {
            log.debug("client close failed: {}", ex.toString());
        }
        Process process = handle.process;
        process.destroy();
        try {
            if (!process.waitFor(5L, java.util.concurrent.TimeUnit.SECONDS)) {
                process.descendants().forEach(ProcessHandle::destroyForcibly);
                process.destroyForcibly();
            }
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            process.destroyForcibly();
        }
    }

    private Process startProcess(OpenRequest request) {
        List<String> argv = new ArrayList<>();
        argv.add(config.command());
        argv.addAll(config.args());
        ProcessBuilder pb = new ProcessBuilder(argv);
        if (request.cwd() != null && !request.cwd().isBlank()) {
            pb.directory(Paths.get(request.cwd()).toFile());
        }
        Map<String, String> env = pb.environment();
        config.env().forEach(env::put);
        request.env().forEach(env::put);
        pb.redirectError(ProcessBuilder.Redirect.INHERIT);
        try {
            return pb.start();
        } catch (IOException ex) {
            throw new SubAgentException("ACP_SPAWN_FAILED", "failed to launch " + config.command(), ex);
        }
    }

    private RuntimeHandle requireHandle(SubAgentSession session) {
        RuntimeHandle handle = handles.get(session.keyString());
        if (handle == null) {
            throw new SubAgentException("ACP_SESSION_GONE", "session " + session.keyString() + " is not open");
        }
        return handle;
    }

    /** Static configuration for a {@link ProcessAcpBackend}. */
    public record Config(
            String command,
            List<String> args,
            Map<String, String> env,
            String clientName,
            String clientVersion,
            Duration promptTimeout) {

        public Config {
            if (command == null || command.isBlank()) {
                throw new IllegalArgumentException("command must not be blank");
            }
            args = args == null ? List.of() : List.copyOf(args);
            env = env == null ? Map.of() : Map.copyOf(env);
            clientName = clientName == null || clientName.isBlank() ? "campusclaw" : clientName;
            clientVersion = clientVersion == null || clientVersion.isBlank() ? "1.0.0" : clientVersion;
            promptTimeout = promptTimeout == null ? Duration.ofMinutes(10L) : promptTimeout;
        }
    }

    private record RuntimeHandle(Process process, AcpClient client) {}

    /**
     * Returns the live ACP client for a session, intended for tests and permission wiring.
     *
     * @param session a session previously returned by {@link #open}
     * @return the underlying {@link AcpClient}
     */
    public AcpClient clientFor(SubAgentSession session) {
        return requireHandle(session).client;
    }

    private AcpProtocol.RequestPermissionResponse.Outcome resolvePermission(
            AcpProtocol.RequestPermissionRequest request, AcpClient.PermissionContext ctx, SubAgentSessionKey key) {
        String toolName = extractToolName(request);
        ApprovalClassifier.Risk risk =
                classifier == null ? ApprovalClassifier.Risk.UNKNOWN : classifier.classify(toolName);
        ApprovalDecision decision = policy == null ? ApprovalDecision.ASK_PARENT : policy.decide(risk, toolName);
        if (decision == ApprovalDecision.AUTO_ALLOW) {
            String allowOption = pickOption(request, "allow");
            if (allowOption != null) {
                return AcpProtocol.RequestPermissionResponse.Outcome.selected(allowOption);
            }
        }
        if (decision == ApprovalDecision.ASK_PARENT) {
            return askParent(request, key, toolName, risk);
        }
        if (decision == ApprovalDecision.DENY) {
            String rejectOption = pickOption(request, "reject");
            if (rejectOption != null) {
                return AcpProtocol.RequestPermissionResponse.Outcome.selected(rejectOption);
            }
        }
        log.info("denying sub-agent permission for tool '{}' risk={} decision={}", toolName, risk, decision);
        return AcpProtocol.RequestPermissionResponse.Outcome.cancelled();
    }

    private AcpProtocol.RequestPermissionResponse.Outcome askParent(
            AcpProtocol.RequestPermissionRequest request,
            SubAgentSessionKey key,
            String toolName,
            ApprovalClassifier.Risk risk) {
        if (parentResolver == null) {
            log.debug("no ParentPermissionResolver configured; cancelling permission request");
            return AcpProtocol.RequestPermissionResponse.Outcome.cancelled();
        }
        var options = parsePermissionOptions(request);
        var params = parseToolParams(request);
        var parentReq = new ParentPermissionRequest(key.asString(), id, toolName, risk, params, options);
        ParentPermissionDecision decision;
        try {
            decision = parentResolver.resolve(parentReq, DEFAULT_ASK_PARENT_TIMEOUT);
        } catch (RuntimeException ex) {
            log.warn("ParentPermissionResolver threw; cancelling: {}", ex.toString());
            return AcpProtocol.RequestPermissionResponse.Outcome.cancelled();
        }
        if (decision == null || decision.outcome() == ParentPermissionDecision.Outcome.CANCELLED) {
            return AcpProtocol.RequestPermissionResponse.Outcome.cancelled();
        }
        if (decision.optionId() != null && !decision.optionId().isBlank()) {
            return AcpProtocol.RequestPermissionResponse.Outcome.selected(decision.optionId());
        }
        log.warn("parent resolver returned SELECTED without optionId; cancelling instead");
        return AcpProtocol.RequestPermissionResponse.Outcome.cancelled();
    }

    private List<ParentPermissionRequest.Option> parsePermissionOptions(AcpProtocol.RequestPermissionRequest request) {
        var options = request.options();
        if (options == null || !options.isArray()) {
            return List.of();
        }
        var parsed = new ArrayList<ParentPermissionRequest.Option>(options.size());
        for (var option : options) {
            parsed.add(new ParentPermissionRequest.Option(
                    option.path("optionId").asText(""),
                    option.path("kind").asText(""),
                    option.path("name").asText("")));
        }
        return parsed;
    }

    private Map<String, Object> parseToolParams(AcpProtocol.RequestPermissionRequest request) {
        var toolCall = request.toolCall();
        if (toolCall == null || !toolCall.has("params")) {
            return Map.of();
        }
        try {
            return mapper.convertValue(
                    toolCall.get("params"), new com.fasterxml.jackson.core.type.TypeReference<>() {});
        } catch (RuntimeException ex) {
            return Map.of();
        }
    }

    private static String extractToolName(AcpProtocol.RequestPermissionRequest request) {
        var toolCall = request.toolCall();
        if (toolCall == null) {
            return "";
        }
        if (toolCall.has("name")) {
            return toolCall.get("name").asText("");
        }
        if (toolCall.has("toolName")) {
            return toolCall.get("toolName").asText("");
        }
        return "";
    }

    private static String pickOption(AcpProtocol.RequestPermissionRequest request, String kindPrefix) {
        var options = request.options();
        if (options == null || !options.isArray()) {
            return null;
        }
        for (var option : options) {
            String kind = option.has("kind") ? option.get("kind").asText("") : "";
            if (kind.startsWith(kindPrefix) && option.has("optionId")) {
                return option.get("optionId").asText(null);
            }
        }
        // Fallback: if asked for "allow" but no kind matches, just return first option.
        if ("allow".equals(kindPrefix) && options.size() > 0 && options.get(0).has("optionId")) {
            return options.get(0).get("optionId").asText(null);
        }
        return null;
    }
}
