package com.huawei.hicampus.mate.matecampusclaw.assistant.config;

import com.huawei.hicampus.mate.matecampusclaw.assistant.mapper.ChatMemoryMapper;
import com.huawei.hicampus.mate.matecampusclaw.assistant.memory.ChatMemoryRepository;
import com.huawei.hicampus.mate.matecampusclaw.assistant.memory.ChatMemoryStore;
import com.huawei.hicampus.mate.matecampusclaw.assistant.memory.MyBatisChatMemoryRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import javax.sql.DataSource;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

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
