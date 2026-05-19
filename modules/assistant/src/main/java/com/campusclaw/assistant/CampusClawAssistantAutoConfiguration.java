/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.campusclaw.assistant;

import com.campusclaw.assistant.channel.gateway.WebSocketGatewayProperties;
import com.campusclaw.assistant.mapper.ChatMemoryMapper;
import com.campusclaw.assistant.memory.ChatMemoryRepository;
import com.campusclaw.assistant.memory.MyBatisChatMemoryRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;

/**
 * Spring Boot auto-configuration for the assistant module. Registers the default
 * {@link ObjectMapper} (JSR-310 aware, ISO-8601 dates), the MyBatis-backed
 * {@link ChatMemoryRepository}/{@link com.campusclaw.assistant.memory.ChatMemoryStore} beans,
 * and enables component plus mapper scanning under {@code com.campusclaw.assistant.*}.
 *
 * @version [br_eCampusCore 25.1.0_Next, 2026/05/13]
 * @since [br_eCampusCore 25.1.0_Next]
 */
@AutoConfiguration
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
    @ConditionalOnMissingBean(ChatMemoryRepository.class)
    public ChatMemoryRepository chatMemoryRepository(ObjectMapper objectMapper, ChatMemoryMapper mapper) {
        return new MyBatisChatMemoryRepository(objectMapper, mapper);
    }

    @Bean
    @ConditionalOnMissingBean(com.campusclaw.assistant.memory.ChatMemoryStore.class)
    public com.campusclaw.assistant.memory.ChatMemoryStore chatMemoryStore(ChatMemoryRepository repository) {
        return new com.campusclaw.assistant.memory.ChatMemoryStore(repository);
    }
}
