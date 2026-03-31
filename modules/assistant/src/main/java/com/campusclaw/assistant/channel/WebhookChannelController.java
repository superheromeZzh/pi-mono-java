package com.campusclaw.assistant.channel;

import com.campusclaw.assistant.task.Task;
import com.campusclaw.assistant.task.TaskManager;
import com.campusclaw.assistant.task.TaskRepository;
import com.campusclaw.assistant.task.TaskStatus;
import jakarta.annotation.Nullable;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.UUID;

@RestController
@RequestMapping("/api/assistant/webhook")
@ConditionalOnProperty(prefix = "pi.assistant.channel.webhook", name = "enabled", havingValue = "true")
public class WebhookChannelController {

    private final WebhookChannelProperties properties;
    private final TaskRepository taskRepository;
    private final TaskManager taskManager;

    public WebhookChannelController(WebhookChannelProperties properties,
                                    TaskRepository taskRepository,
                                    TaskManager taskManager) {
        this.properties = properties;
        this.taskRepository = taskRepository;
        this.taskManager = taskManager;
    }

    public record IncomingMessage(
        String message,
        @Nullable String conversationId
    ) {}

    @PostMapping("/incoming")
    public ResponseEntity<Void> incoming(
            @RequestBody IncomingMessage incoming,
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            @RequestParam(value = "token", required = false) String queryToken) {
        if (!validateToken(authHeader, queryToken)) {
            return ResponseEntity.status(401).build();
        }

        String id = UUID.randomUUID().toString();
        String conversationId = incoming.conversationId() != null
                ? incoming.conversationId()
                : "webhook-" + id;
        Instant now = Instant.now();
        Task task = new Task(
                id, conversationId, incoming.message(),
                TaskStatus.TODO, null, properties.getName(),
                now, now
        );
        taskRepository.save(task);
        taskManager.executeTask(id);

        return ResponseEntity.accepted().build();
    }

    private boolean validateToken(String authHeader, String queryToken) {
        String expected = properties.getToken();
        if (expected == null || expected.isEmpty()) {
            return true;
        }

        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            return expected.equals(authHeader.substring(7));
        }

        if (queryToken != null) {
            return expected.equals(queryToken);
        }

        return false;
    }
}
