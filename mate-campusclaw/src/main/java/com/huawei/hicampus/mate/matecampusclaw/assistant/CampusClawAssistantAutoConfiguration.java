package com.huawei.hicampus.mate.matecampusclaw.assistant;

import com.huawei.hicampus.mate.matecampusclaw.assistant.channel.gateway.WebSocketGatewayProperties;
import com.huawei.hicampus.mate.matecampusclaw.assistant.config.AssistantPersistenceConfiguration;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Import;

@AutoConfiguration
@Import(AssistantPersistenceConfiguration.class)
@ComponentScan(basePackages = "com.huawei.hicampus.mate.matecampusclaw.assistant.channel")
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
}
