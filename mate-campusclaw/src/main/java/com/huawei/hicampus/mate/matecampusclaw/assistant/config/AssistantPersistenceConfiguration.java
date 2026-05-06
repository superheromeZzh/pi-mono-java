/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.hicampus.mate.matecampusclaw.assistant.config;

import javax.sql.DataSource;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.huawei.hicampus.mate.matecampusclaw.assistant.mapper.ChatMemoryMapper;
import com.huawei.hicampus.mate.matecampusclaw.assistant.memory.ChatMemoryRepository;
import com.huawei.hicampus.mate.matecampusclaw.assistant.memory.ChatMemoryStore;
import com.huawei.hicampus.mate.matecampusclaw.assistant.memory.MyBatisChatMemoryRepository;

import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Wires the assistant module's MyBatis persistence beans against the host application's DataSource.
 *
 * @version [br_eCampusCore 25.1.0_Next, 2026/05/06]
 * @since [br_eCampusCore 25.1.0_Next]
 */
@Configuration
@ConditionalOnBean(DataSource.class)
public class AssistantPersistenceConfiguration {

    @Bean
    @ConditionalOnMissingBean(ChatMemoryRepository.class)
    public ChatMemoryRepository chatMemoryRepository(ObjectMapper objectMapper, ChatMemoryMapper mapper) {
        return new MyBatisChatMemoryRepository(objectMapper, mapper);
    }

    @Bean
    @ConditionalOnMissingBean(ChatMemoryStore.class)
    public ChatMemoryStore chatMemoryStore(ChatMemoryRepository repository) {
        return new ChatMemoryStore(repository);
    }
}
