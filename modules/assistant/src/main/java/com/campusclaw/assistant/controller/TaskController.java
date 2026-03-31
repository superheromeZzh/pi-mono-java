package com.campusclaw.assistant.controller;

import com.campusclaw.assistant.task.RecurringTask;
import com.campusclaw.assistant.task.RecurringTaskHandler;
import com.campusclaw.assistant.task.Task;
import com.campusclaw.assistant.task.TaskManager;
import com.campusclaw.assistant.task.TaskRepository;
import com.campusclaw.assistant.task.TaskStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/assistant/tasks")
public class TaskController {

    private final TaskRepository taskRepository;
    private final TaskManager taskManager;

    private final RecurringTaskHandler recurringTaskHandler;

    public TaskController(TaskRepository taskRepository, TaskManager taskManager,
                          RecurringTaskHandler recurringTaskHandler) {
        this.taskRepository = taskRepository;
        this.taskManager = taskManager;
        this.recurringTaskHandler = recurringTaskHandler;
    }

    @GetMapping
    public List<Task> list(@RequestParam(required = false) TaskStatus status) {
        if (status != null) {
            return taskRepository.findByStatus(status);
        }
        return taskRepository.findAll();
    }

    @GetMapping("/{id}")
    public ResponseEntity<Task> get(@PathVariable String id) {
        return taskRepository.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping
    public Task create(@RequestBody CreateTaskRequest request) {
        String id = UUID.randomUUID().toString();
        String conversationId = request.conversationId() != null
                ? request.conversationId()
                : "task-" + id;
        Instant now = Instant.now();
        Task task = new Task(
                id, conversationId, request.prompt(),
                TaskStatus.TODO, null, request.channelName(),
                now, now
        );
        taskRepository.save(task);
        return task;
    }

    @PostMapping("/{id}/execute")
    public ResponseEntity<Void> execute(@PathVariable String id) {
        if (taskRepository.findById(id).isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        taskManager.executeTask(id);
        return ResponseEntity.accepted().build();
    }

    @GetMapping("/recurring")
    public List<RecurringTask> listRecurring() {
        return taskRepository.findRecurringTasks();
    }

    @PostMapping("/recurring")
    public RecurringTask createRecurring(@RequestBody CreateRecurringTaskRequest request) {
        RecurringTask recurringTask = new RecurringTask(
                UUID.randomUUID().toString(),
                request.name(),
                request.description(),
                request.cronExpression(),
                request.prompt(),
                request.modelId()
        );
        taskRepository.saveRecurringTask(recurringTask);
        recurringTaskHandler.scheduleRecurringTasks();
        return recurringTask;
    }

    @DeleteMapping("/recurring/{id}")
    public ResponseEntity<Void> deleteRecurring(@PathVariable String id) {
        taskRepository.deleteRecurringTaskExecutions(id);
        taskRepository.deleteRecurringChatMemory(id);
        taskRepository.deleteRecurringTask(id);
        return ResponseEntity.ok().build();
    }
}
