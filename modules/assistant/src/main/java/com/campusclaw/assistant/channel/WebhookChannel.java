package com.campusclaw.assistant.channel;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

@Component
@ConditionalOnProperty(prefix = "pi.assistant.channel.webhook", name = "enabled", havingValue = "true")
public class WebhookChannel implements Channel {

    private static final Logger log = LoggerFactory.getLogger(WebhookChannel.class);

    private final WebhookChannelProperties properties;
    private final ChannelRegistry channelRegistry;
    private final RestTemplate restTemplate;

    public WebhookChannel(WebhookChannelProperties properties, ChannelRegistry channelRegistry) {
        this.properties = properties;
        this.channelRegistry = channelRegistry;
        this.restTemplate = new RestTemplate();
    }

    @PostConstruct
    public void register() {
        channelRegistry.register(this);
        log.info("Webhook channel registered with name: {}", properties.getName());
    }

    @Override
    public String getName() {
        return properties.getName();
    }

    @Override
    public void sendMessage(String message) {
        String url = properties.getGatewayUrl();
        String token = properties.getToken();

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(token);

        Map<String, String> body = Map.of("message", message);
        HttpEntity<Map<String, String>> request = new HttpEntity<>(body, headers);

        log.info("Sending message to webhook gateway: {}", url);
        try {
            restTemplate.postForEntity(url, request, String.class);
        } catch (Exception e) {
            log.error("Failed to send message to webhook gateway: {}", url, e);
        }
    }
}
