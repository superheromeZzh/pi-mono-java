package com.campusclaw.assistant.channel;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.campusclaw.assistant.task.TaskManager;
import com.campusclaw.assistant.task.TaskRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.concurrent.CompletableFuture;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class WebhookChannelControllerTest {

    private MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final TaskManager taskManager = mock(TaskManager.class);
    private final TaskRepository taskRepository = mock(TaskRepository.class);

    @BeforeEach
    void setUp() {
        WebhookChannelProperties properties = new WebhookChannelProperties();
        properties.setEnabled(true);
        properties.setToken("my-secret");
        properties.setName("webhook");

        WebhookChannelController controller = new WebhookChannelController(
                properties, taskRepository, taskManager);

        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    @Test
    void noToken_shouldReturn401() throws Exception {
        mockMvc.perform(post("/api/assistant/webhook/incoming")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new WebhookChannelController.IncomingMessage("hello", null))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void wrongBearerToken_shouldReturn401() throws Exception {
        mockMvc.perform(post("/api/assistant/webhook/incoming")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer wrong-token")
                        .content(objectMapper.writeValueAsString(
                                new WebhookChannelController.IncomingMessage("hello", null))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void wrongQueryToken_shouldReturn401() throws Exception {
        mockMvc.perform(post("/api/assistant/webhook/incoming?token=wrong-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new WebhookChannelController.IncomingMessage("hello", null))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void correctBearerToken_shouldReturn202() throws Exception {
        when(taskManager.executeTask(anyString()))
                .thenReturn(CompletableFuture.completedFuture("ok"));

        mockMvc.perform(post("/api/assistant/webhook/incoming")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer my-secret")
                        .content(objectMapper.writeValueAsString(
                                new WebhookChannelController.IncomingMessage("hello webhook", null))))
                .andExpect(status().isAccepted());

        ArgumentCaptor<String> taskIdCaptor = ArgumentCaptor.forClass(String.class);
        verify(taskManager).executeTask(taskIdCaptor.capture());
    }

    @Test
    void correctQueryToken_shouldReturn202() throws Exception {
        when(taskManager.executeTask(anyString()))
                .thenReturn(CompletableFuture.completedFuture("ok"));

        mockMvc.perform(post("/api/assistant/webhook/incoming?token=my-secret")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new WebhookChannelController.IncomingMessage("hello via query", null))))
                .andExpect(status().isAccepted());

        verify(taskManager).executeTask(anyString());
    }

    @Test
    void withConversationId_shouldPassThrough() throws Exception {
        when(taskManager.executeTask(anyString()))
                .thenReturn(CompletableFuture.completedFuture("ok"));

        mockMvc.perform(post("/api/assistant/webhook/incoming")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer my-secret")
                        .content(objectMapper.writeValueAsString(
                                new WebhookChannelController.IncomingMessage("hello", "my-conv-42"))))
                .andExpect(status().isAccepted());

        verify(taskManager).executeTask(anyString());
    }
}
