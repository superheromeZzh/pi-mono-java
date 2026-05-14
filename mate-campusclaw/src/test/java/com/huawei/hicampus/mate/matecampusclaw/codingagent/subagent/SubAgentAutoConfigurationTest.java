/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.hicampus.mate.matecampusclaw.codingagent.subagent;

import static org.assertj.core.api.Assertions.assertThat;

import com.huawei.hicampus.mate.matecampusclaw.agent.subagent.SubAgentRegistry;
import com.huawei.hicampus.mate.matecampusclaw.agent.subagent.acp.backend.ProcessAcpBackend;
import com.huawei.hicampus.mate.matecampusclaw.agent.subagent.approval.ApprovalClassifier;
import com.huawei.hicampus.mate.matecampusclaw.agent.subagent.approval.DefaultApprovalPolicy;
import com.huawei.hicampus.mate.matecampusclaw.agent.subagent.approval.TimeoutDeniedResolver;
import com.huawei.hicampus.mate.matecampusclaw.agent.subagent.http.HttpAgentBackend;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

class SubAgentAutoConfigurationTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(JacksonAutoConfiguration.class))
            .withUserConfiguration(Support.class, SubAgentAutoConfiguration.class);

    @Test
    void registersAcpAndHttpBackendsFromProperties() {
        runner.withPropertyValues(
                        "subagent.enabled=true",
                        "subagent.backends.claude-code.type=acp",
                        "subagent.backends.claude-code.command=claude",
                        "subagent.backends.claude-code.args[0]=--acp",
                        "subagent.backends.remote.type=http",
                        "subagent.backends.remote.url=https://agent.example.com",
                        "subagent.backends.remote.auth-type=bearer",
                        "subagent.backends.remote.auth-token=secret")
                .run(context -> {
                    SubAgentRegistry registry = context.getBean(SubAgentRegistry.class);
                    assertThat(registry.backendIds()).contains("claude-code", "remote");
                    assertThat(registry.requireBackend("claude-code")).isInstanceOf(ProcessAcpBackend.class);
                    assertThat(registry.requireBackend("remote")).isInstanceOf(HttpAgentBackend.class);
                });
    }

    @Test
    void disabledEntriesAreSkipped() {
        runner.withPropertyValues(
                        "subagent.enabled=true",
                        "subagent.backends.codex.type=acp",
                        "subagent.backends.codex.command=codex-acp",
                        "subagent.backends.codex.disabled=true")
                .run(context -> {
                    SubAgentRegistry registry = context.getBean(SubAgentRegistry.class);
                    assertThat(registry.backendIds()).doesNotContain("codex");
                });
    }

    @Test
    void disablingFeatureSkipsAllBackends() {
        runner.withPropertyValues(
                        "subagent.enabled=false",
                        "subagent.backends.claude-code.type=acp",
                        "subagent.backends.claude-code.command=claude")
                .run(context -> {
                    SubAgentRegistry registry = context.getBean(SubAgentRegistry.class);
                    assertThat(registry.backendIds()).isEmpty();
                });
    }

    @Test
    void invalidBackendIsSkippedWithoutBreakingStartup() {
        runner.withPropertyValues(
                        "subagent.enabled=true",
                        "subagent.backends.bad.type=acp",
                        // missing 'command' on acp backend
                        "subagent.backends.good.type=http",
                        "subagent.backends.good.url=https://ok.example.com",
                        "subagent.backends.good.auth-type=none")
                .run(context -> {
                    SubAgentRegistry registry = context.getBean(SubAgentRegistry.class);
                    assertThat(registry.backendIds()).contains("good").doesNotContain("bad");
                });
    }

    @Configuration(proxyBeanMethods = false)
    static class Support {

        @Bean
        SubAgentRegistry subAgentRegistry() {
            return new SubAgentRegistry(new org.springframework.beans.factory.support.StaticListableBeanFactory()
                    .getBeanProvider(com.huawei.hicampus.mate.matecampusclaw.agent.subagent.SubAgentBackend.class));
        }

        @Bean
        ApprovalClassifier approvalClassifier() {
            return new ApprovalClassifier();
        }

        @Bean
        DefaultApprovalPolicy defaultApprovalPolicy() {
            return new DefaultApprovalPolicy("allow");
        }

        @Bean
        TimeoutDeniedResolver parentPermissionResolver() {
            return new TimeoutDeniedResolver();
        }

        @Bean
        ObjectMapper objectMapper() {
            return new ObjectMapper();
        }
    }
}
