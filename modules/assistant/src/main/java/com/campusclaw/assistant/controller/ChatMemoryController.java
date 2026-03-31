package com.campusclaw.assistant.controller;

import com.campusclaw.ai.types.Message;
import com.campusclaw.assistant.memory.ChatMemoryStore;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/assistant/memory")
public class ChatMemoryController {

    private final ChatMemoryStore memoryStore;

    public ChatMemoryController(ChatMemoryStore memoryStore) {
        this.memoryStore = memoryStore;
    }

    @GetMapping("/{conversationId}")
    public List<Message> load(@PathVariable String conversationId) {
        return memoryStore.load(conversationId);
    }

    @PostMapping("/{conversationId}")
    public ResponseEntity<Void> append(
            @PathVariable String conversationId,
            @RequestBody List<Message> messages) {
        memoryStore.append(conversationId, messages);
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/{conversationId}")
    public ResponseEntity<Void> clear(@PathVariable String conversationId) {
        memoryStore.clear(conversationId);
        return ResponseEntity.ok().build();
    }
}
