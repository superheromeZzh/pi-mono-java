package com.campusclaw.assistant;

import com.campusclaw.assistant.mapper.ChatMemoryMapper;
import com.campusclaw.assistant.memory.ChatMemoryRepository;
import com.campusclaw.assistant.memory.MyBatisChatMemoryRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.campusclaw.assistant.channel.gateway.WebSocketGatewayProperties;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

@Configuration
@ComponentScan
@MapperScan("com.campusclaw.assistant.mapper")
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
    @ConditionalOnBean(ChatMemoryMapper.class)
    @ConditionalOnMissingBean(ChatMemoryRepository.class)
    public ChatMemoryRepository chatMemoryRepository(ObjectMapper objectMapper, ChatMemoryMapper mapper) {
        return new MyBatisChatMemoryRepository(objectMapper, mapper);
    }
}
