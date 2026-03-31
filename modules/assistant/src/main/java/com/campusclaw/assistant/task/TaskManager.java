package com.campusclaw.assistant.task;

import com.campusclaw.agent.Agent;
import com.campusclaw.agent.event.AgentEndEvent;
import com.campusclaw.agent.event.AgentEventListener;
import com.campusclaw.ai.model.ModelRegistry;
import com.campusclaw.ai.types.AssistantMessage;
import com.campusclaw.ai.types.ContentBlock;
import com.campusclaw.ai.types.Message;
import com.campusclaw.ai.types.Model;
import com.campusclaw.ai.types.Provider;
import com.campusclaw.ai.types.TextContent;
import com.campusclaw.assistant.channel.ChannelRegistry;
import com.campusclaw.assistant.memory.ChatMemoryStore;
import com.campusclaw.assistant.session.AgentSessionConfig;
import com.campusclaw.assistant.session.AgentSessionFactory;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicReference;

@Service
public class TaskManager {

    public record TaskContext(String taskId, String taskName) {}

    private final TaskRepository taskRepository;
    private final ChatMemoryStore memoryStore;
    private final AgentSessionFactory sessionFactory;
    private final ChannelRegistry channelRegistry;
    private final ModelRegistry modelRegistry;

    private final CopyOnWriteArrayList<AgentEventListener> taskEventListeners = new CopyOnWriteArrayList<>();
    private volatile Agent currentTaskAgent;
    private volatile TaskContext currentTaskContext;

    public TaskManager(
        TaskRepository taskRepository,
        ChatMemoryStore memoryStore,
        AgentSessionFactory sessionFactory,
        ChannelRegistry channelRegistry,
        ModelRegistry modelRegistry
    ) {
        this.taskRepository = Objects.requireNonNull(taskRepository, "taskRepository");
        this.memoryStore = Objects.requireNonNull(memoryStore, "memoryStore");
        this.sessionFactory = Objects.requireNonNull(sessionFactory, "sessionFactory");
        this.channelRegistry = Objects.requireNonNull(channelRegistry, "channelRegistry");
        this.modelRegistry = Objects.requireNonNull(modelRegistry, "modelRegistry");
    }

    /**
     * Subscribe to events from task agents. Returns an unsubscribe Runnable.
     */
    public Runnable subscribe(AgentEventListener listener) {
        taskEventListeners.add(listener);
        // Also subscribe to the current task agent if one is running
        Agent agent = currentTaskAgent;
        if (agent != null) {
            agent.subscribe(listener);
        }
        return () -> {
            taskEventListeners.remove(listener);
        };
    }

    /**
     * Abort the currently running task agent, if any.
     */
    public void abort() {
        Agent agent = currentTaskAgent;
        if (agent != null) {
            agent.abort();
        }
    }

    /**
     * Returns the context (id and name) of the currently executing task, or null if idle.
     */
    public TaskContext getCurrentTaskContext() {
        return currentTaskContext;
    }

    public CompletableFuture<String> executeTask(String taskId) {
        return executeTask(taskId, null);
    }

    public CompletableFuture<String> executeTask(String taskId, Model modelOverride) {
        Task task = taskRepository.findById(taskId)
            .orElseThrow(() -> new IllegalArgumentException("Task not found: " + taskId));

        Task inProgressTask = task.transitionTo(TaskStatus.IN_PROGRESS);
        taskRepository.save(inProgressTask);

        List<Message> history = memoryStore.load(inProgressTask.conversationId());

        Model model = modelOverride;
        if (model == null) {
            model = modelRegistry.getModel(Provider.ANTHROPIC, "claude-sonnet-4-20250514").orElse(null);
        }
        String baseUrlOverride = System.getenv("ANTHROPIC_BASE_URL");
        if (model != null && baseUrlOverride != null && !baseUrlOverride.isBlank()) {
            model = new Model(
                model.id(), model.name(), model.api(), model.provider(),
                baseUrlOverride, model.reasoning(), model.inputModalities(),
                model.cost(), model.contextWindow(), model.maxTokens(),
                model.headers(), model.thinkingFormat(), model.apiKey()
            );
        }
        AgentSessionConfig config = new AgentSessionConfig(
            model, inProgressTask.conversationId(), List.of()
        );
        Agent agent = sessionFactory.create(config);
        agent.replaceMessages(history);

        // Forward external listeners to this agent
        currentTaskAgent = agent;
        currentTaskContext = new TaskContext(taskId, inProgressTask.prompt());
        for (var listener : taskEventListeners) {
            agent.subscribe(listener);
        }

        AtomicReference<String> resultRef = new AtomicReference<>("");

        agent.subscribe((AgentEventListener) event -> {
            if (event instanceof AgentEndEvent endEvent) {
                String result = extractLastAssistantText(endEvent.messages());
                resultRef.set(result);

                Task completed = inProgressTask.withResult(result).transitionTo(TaskStatus.COMPLETED);
                taskRepository.save(completed);

                memoryStore.append(inProgressTask.conversationId(), endEvent.messages());

                if (inProgressTask.channelName() != null) {
                    var channel = channelRegistry.get(inProgressTask.channelName());
                    if (channel != null) {
                        channel.sendMessage(result);
                    }
                }
            }
        });

        CompletableFuture<Void> future;
        try {
            future = agent.prompt(inProgressTask.prompt());
        } catch (Exception e) {
            currentTaskAgent = null;
            currentTaskContext = null;
            String errMsg = "Error: " + e.getMessage();
            Task failed = inProgressTask.withResult(errMsg).transitionTo(TaskStatus.FAILED);
            taskRepository.save(failed);
            return CompletableFuture.completedFuture(errMsg);
        }

        return future.handle((v, ex) -> {
            currentTaskAgent = null;
            currentTaskContext = null;
            if (ex != null) {
                Throwable cause = ex.getCause() != null ? ex.getCause() : ex;
                String errMsg = "Error: " + cause.getMessage();
                Task failed = inProgressTask.withResult(errMsg).transitionTo(TaskStatus.FAILED);
                taskRepository.save(failed);
                return errMsg;
            }
            return resultRef.get();
        });
    }

    private String extractLastAssistantText(List<Message> messages) {
        for (int i = messages.size() - 1; i >= 0; i--) {
            Message message = messages.get(i);
            if (message instanceof AssistantMessage assistantMessage) {
                StringBuilder sb = new StringBuilder();
                for (ContentBlock block : assistantMessage.content()) {
                    if (block instanceof TextContent textContent) {
                        sb.append(textContent.text());
                    }
                }
                if (!sb.isEmpty()) {
                    return sb.toString();
                }
            }
        }
        return "";
    }
}
