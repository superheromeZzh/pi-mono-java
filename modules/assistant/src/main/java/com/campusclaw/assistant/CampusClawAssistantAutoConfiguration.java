/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.campusclaw.assistant;

import javax.sql.DataSource;

import com.campusclaw.assistant.channel.gateway.WebSocketGatewayProperties;
import com.campusclaw.assistant.mapper.ChatMemoryMapper;
import com.campusclaw.assistant.memory.ChatMemoryRepository;
import com.campusclaw.assistant.memory.ChatMemoryStore;
import com.campusclaw.assistant.memory.MyBatisChatMemoryRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

/**
 * Configuration entry-point for the assistant module. Picked up by component scan from
 * {@code com.campusclaw} (the host Spring Boot application's {@code scanBasePackages}),
 * so no {@code META-INF/spring/.imports} registration is needed.
 *
 * <p>Guards:
 * <ul>
 *   <li>{@code @ConditionalOnClass} — silently inert when MyBatis or JDBC isn't on the
 *       integrator's classpath (covers the corporate-mate integration scenario where
 *       optional persistence deps may not be propagated).</li>
 *   <li>{@code @ConditionalOnProperty} — opt-in via {@code pi.assistant.enabled=true}.
 *       Downstream integrators can override to {@code false}.</li>
 *   <li>MyBatis persistence beans additionally gate on {@code @ConditionalOnBean(DataSource.class)}
 *       so they no-op when no datasource is wired (host falls back to in-memory behavior
 *       via {@code InteractiveMode.resolveChatMemoryStore} catch path).</li>
 * </ul>
 *
 * @version [br_eCampusCore 25.1.0_Next, 2026/05/28]
 * @since [br_eCampusCore 25.1.0_Next]
 */
@Configuration
@ConditionalOnClass({MapperScan.class, DataSource.class})
@ConditionalOnProperty(prefix = "pi.assistant", name = "enabled", havingValue = "true", matchIfMissing = false)
@ComponentScan
@MapperScan("com.campusclaw.assistant.mapper")
@EnableConfigurationProperties({AssistantProperties.class, WebSocketGatewayProperties.class})
public class CampusClawAssistantAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(ObjectMapper.class)
    public ObjectMapper assistantObjectMapper() {
        return new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    }

    @Bean
    @ConditionalOnBean(DataSource.class)
    @ConditionalOnMissingBean(ChatMemoryRepository.class)
    public ChatMemoryRepository chatMemoryRepository(ObjectMapper objectMapper, ChatMemoryMapper mapper) {
        return new MyBatisChatMemoryRepository(objectMapper, mapper);
    }

    @Bean
    @ConditionalOnBean(DataSource.class)
    @ConditionalOnMissingBean(ChatMemoryStore.class)
    public ChatMemoryStore chatMemoryStore(ChatMemoryRepository repository) {
        return new ChatMemoryStore(repository);
    }
}
