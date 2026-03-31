package com.campusclaw.assistant;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.campusclaw.assistant.channel.WebhookChannelProperties;
import com.campusclaw.assistant.mapper.ChatMemoryMapper;
import com.campusclaw.assistant.mapper.TaskMapper;
import com.campusclaw.assistant.memory.ChatMemoryRepository;
import com.campusclaw.assistant.memory.MyBatisChatMemoryRepository;
import com.campusclaw.assistant.task.MyBatisTaskRepository;
import com.campusclaw.assistant.task.TaskRepository;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

@Configuration
@ComponentScan
@EnableConfigurationProperties({AssistantProperties.class, WebhookChannelProperties.class})
@MapperScan("com.campusclaw.assistant.mapper")
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
    public ChatMemoryRepository chatMemoryRepository(
        ObjectMapper objectMapper,
        ChatMemoryMapper chatMemoryMapper
    ) {
        return new MyBatisChatMemoryRepository(objectMapper, chatMemoryMapper);
    }

    @Bean
    @ConditionalOnMissingBean(TaskRepository.class)
    public TaskRepository taskRepository(TaskMapper taskMapper, ObjectMapper objectMapper) {
        return new MyBatisTaskRepository(taskMapper, objectMapper);
    }
}
