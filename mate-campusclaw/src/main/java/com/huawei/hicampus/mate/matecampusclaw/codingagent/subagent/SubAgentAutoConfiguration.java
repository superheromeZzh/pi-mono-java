/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.hicampus.mate.matecampusclaw.codingagent.subagent;

import java.net.URI;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import com.huawei.hicampus.mate.matecampusclaw.agent.subagent.SubAgentBackend;
import com.huawei.hicampus.mate.matecampusclaw.agent.subagent.SubAgentRegistry;
import com.huawei.hicampus.mate.matecampusclaw.agent.subagent.acp.backend.ProcessAcpBackend;
import com.huawei.hicampus.mate.matecampusclaw.agent.subagent.approval.ApprovalClassifier;
import com.huawei.hicampus.mate.matecampusclaw.agent.subagent.approval.ApprovalPolicy;
import com.huawei.hicampus.mate.matecampusclaw.agent.subagent.approval.ParentPermissionResolver;
import com.huawei.hicampus.mate.matecampusclaw.agent.subagent.http.HttpAgentBackend;
import com.huawei.hicampus.mate.matecampusclaw.agent.subagent.http.HttpAgentConfig;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;

import jakarta.annotation.PostConstruct;

/**
 * Builds and registers sub-agent backends from {@link SubAgentProperties} at application startup.
 *
 * <p>Each entry under {@code subagent.backends.*} produces either a {@link ProcessAcpBackend} or
 * an {@link HttpAgentBackend} that gets registered against the singleton {@link SubAgentRegistry}.
 * Entries with {@code disabled: true} are skipped.
 *
 * @version [br_eCampusCore 25.1.0_Next, 2026/05/12]
 * @since [br_eCampusCore 25.1.0_Next]
 */
@Configuration
@EnableConfigurationProperties(SubAgentProperties.class)
@Lazy(false)
public class SubAgentAutoConfiguration {

    private static final Logger log = LoggerFactory.getLogger(SubAgentAutoConfiguration.class);
    private static final String TYPE_ACP = "acp";
    private static final String TYPE_HTTP = "http";

    private final SubAgentProperties properties;
    private final SubAgentRegistry registry;
    private final ObjectMapper mapper;
    private final ApprovalClassifier classifier;
    private final ApprovalPolicy policy;
    private final ParentPermissionResolver parentResolver;

    public SubAgentAutoConfiguration(
            SubAgentProperties properties,
            SubAgentRegistry registry,
            ObjectMapper mapper,
            ApprovalClassifier classifier,
            ApprovalPolicy policy,
            ParentPermissionResolver parentResolver) {
        this.properties = properties;
        this.registry = registry;
        this.mapper = mapper;
        this.classifier = classifier;
        this.policy = policy;
        this.parentResolver = parentResolver;
    }

    @PostConstruct
    public void registerBackends() {
        if (!properties.isEnabled()) {
            log.info("sub-agent backends disabled by configuration");
            return;
        }
        Map<String, SubAgentProperties.BackendSpec> backends = properties.getBackends();
        if (backends.isEmpty()) {
            log.debug("no sub-agent backends configured");
            return;
        }
        backends.forEach(this::registerBackend);
    }

    private void registerBackend(String id, SubAgentProperties.BackendSpec spec) {
        if (spec.isDisabled()) {
            log.info("sub-agent backend '{}' is disabled in configuration", id);
            return;
        }
        String type = spec.getType() == null ? "" : spec.getType().trim().toLowerCase(Locale.ROOT);
        try {
            SubAgentBackend backend =
                    switch (type) {
                        case TYPE_ACP -> buildAcp(id, spec);
                        case TYPE_HTTP -> buildHttp(id, spec);
                        default ->
                            throw new IllegalArgumentException("subagent.backends." + id
                                    + ".type must be 'acp' or 'http' (got '" + spec.getType() + "')");
                    };
            registry.register(backend);
            log.info("registered sub-agent backend '{}' ({})", backend.id(), type);
        } catch (IllegalArgumentException ex) {
            log.warn("skipping sub-agent backend '{}': {}", id, ex.getMessage());
        }
    }

    private SubAgentBackend buildAcp(String id, SubAgentProperties.BackendSpec spec) {
        if (spec.getCommand() == null || spec.getCommand().isBlank()) {
            throw new IllegalArgumentException("acp backend requires 'command'");
        }
        var config = new ProcessAcpBackend.Config(
                spec.getCommand(),
                List.copyOf(spec.getArgs()),
                Map.copyOf(spec.getEnv()),
                spec.getClientName(),
                spec.getClientVersion(),
                spec.getPromptTimeout());
        return new ProcessAcpBackend(id, config, mapper, classifier, policy, parentResolver);
    }

    private SubAgentBackend buildHttp(String id, SubAgentProperties.BackendSpec spec) {
        if (spec.getUrl() == null || spec.getUrl().isBlank()) {
            throw new IllegalArgumentException("http backend requires 'url'");
        }
        HttpAgentConfig.AuthType authType = HttpAgentConfig.AuthType.fromWire(spec.getAuthType());
        var config = new HttpAgentConfig(
                id,
                URI.create(spec.getUrl()),
                authType,
                spec.getAuthToken(),
                spec.getAuthHeaderName(),
                spec.getConnectTimeout(),
                spec.getRequestTimeout(),
                spec.getPromptTimeout() == null ? Duration.ofMinutes(10L) : spec.getPromptTimeout());
        return new HttpAgentBackend(config, mapper);
    }
}
