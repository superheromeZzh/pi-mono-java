package com.huawei.hicampus.mate.matecampusclaw.assistant;

import com.huawei.hicampus.mate.matecampusclaw.assistant.channel.gateway.WebSocketGatewayProperties;
import com.huawei.hicampus.mate.matecampusclaw.assistant.mapper.ChatMemoryMapper;
import com.huawei.hicampus.mate.matecampusclaw.assistant.memory.ChatMemoryRepository;
import com.huawei.hicampus.mate.matecampusclaw.assistant.memory.MyBatisChatMemoryRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;

@AutoConfiguration
@ComponentScan
@MapperScan("com.huawei.hicampus.mate.matecampusclaw.assistant.mapper")
@EnableConfigurationProperties({
    AssistantProperties.class,
    WebSocketGatewayProperties.class
})
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
    @ConditionalOnMissingBean(com.huawei.hicampus.mate.matecampusclaw.assistant.memory.ChatMemoryStore.class)
    public com.huawei.hicampus.mate.matecampusclaw.assistant.memory.ChatMemoryStore chatMemoryStore(ChatMemoryRepository repository) {
        return new com.huawei.hicampus.mate.matecampusclaw.assistant.memory.ChatMemoryStore(repository);
    }
}
