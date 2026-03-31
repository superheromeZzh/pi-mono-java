package com.campusclaw.assistant.task;

import com.campusclaw.ai.model.ModelRegistry;
import com.campusclaw.ai.types.Model;
import jakarta.annotation.PostConstruct;
import org.jobrunr.jobs.annotations.Job;
import org.jobrunr.scheduling.JobScheduler;
import org.jobrunr.scheduling.BackgroundJob;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Component
public class RecurringTaskHandler {

    private static final Logger log = LoggerFactory.getLogger(RecurringTaskHandler.class);

    private final TaskManager taskManager;
    private final TaskRepository taskRepository;
    private final JobScheduler jobScheduler;
    private final ModelRegistry modelRegistry;

    public RecurringTaskHandler(
        TaskManager taskManager,
        TaskRepository taskRepository,
        JobScheduler jobScheduler,
        ModelRegistry modelRegistry
    ) {
        this.taskManager = taskManager;
        this.taskRepository = taskRepository;
        this.jobScheduler = jobScheduler;
        this.modelRegistry = modelRegistry;
    }

    @PostConstruct
    public void scheduleRecurringTasks() {
        List<RecurringTask> recurringTasks = taskRepository.findRecurringTasks();
        for (RecurringTask recurringTask : recurringTasks) {
            try {
                jobScheduler.scheduleRecurrently(
                    "recurring-" + recurringTask.id(),
                    recurringTask.cronExpression(),
                    () -> enqueueRecurringTask(recurringTask)
                );
            } catch (Exception e) {
                log.warn("Deleting recurring task with invalid cron '{}' ({}): {}",
                    recurringTask.name(), recurringTask.id(), recurringTask.cronExpression());
                taskRepository.deleteRecurringTask(recurringTask.id());
            }
        }
    }

    @Job(name = "Recurring task %0")
    public void executeRecurringTask(String recurringTaskId) {
        Optional<RecurringTask> opt = taskRepository.findRecurringTaskById(recurringTaskId);
        if (opt.isEmpty()) {
            log.warn("Recurring task not found: {}", recurringTaskId);
            return;
        }
        RecurringTask recurringTask = opt.get();

        String taskId = UUID.randomUUID().toString();
        String conversationId = "recurring-" + recurringTask.id() + "-" + Instant.now().toEpochMilli();
        Task task = new Task(
            taskId, conversationId, recurringTask.prompt(),
            TaskStatus.TODO, null, null,
            Instant.now(), Instant.now()
        );
        taskRepository.save(task);

        Model model = resolveModel(recurringTask.modelId());
        Instant start = Instant.now();

        taskManager.executeTask(taskId, model).whenComplete((result, ex) -> {
            long durationMs = java.time.Duration.between(start, Instant.now()).toMillis();
            String status;
            String resultText;
            if (ex != null) {
                status = "FAILED";
                resultText = ex.getCause() != null ? ex.getCause().getMessage() : ex.getMessage();
            } else {
                status = "COMPLETED";
                resultText = result;
            }

            ExecutionResult execResult = new ExecutionResult(Instant.now(), status,
                resultText != null ? resultText : "", durationMs);

            List<ExecutionResult> results = new ArrayList<>(
                recurringTask.executionResults() != null ? recurringTask.executionResults() : List.of());
            results.add(execResult);
            if (results.size() > 50) {
                results = results.subList(results.size() - 50, results.size());
            }

            RecurringTask updated = new RecurringTask(
                recurringTask.id(), recurringTask.name(), recurringTask.description(),
                recurringTask.cronExpression(), recurringTask.prompt(), recurringTask.modelId(),
                status, Instant.now(), results
            );
            taskRepository.updateRecurringTask(updated);
            taskRepository.deleteTask(taskId);
            taskRepository.deleteRecurringChatMemory(recurringTaskId);
        });
    }

    public void enqueueRecurringTask(RecurringTask recurringTask) {
        jobScheduler.enqueue(() -> executeRecurringTask(recurringTask.id()));
    }

    /**
     * Schedule a one-time task to execute after a delay.
     */
    public void scheduleDelayedTask(String prompt, java.time.Duration delay, String modelId) {
        Instant executeAt = Instant.now().plus(delay);
        log.info("Scheduling delayed task: '{}' at {}", prompt, executeAt);
        try {
            jobScheduler.schedule(executeAt, () -> executeDelayedTask(prompt, modelId));
            log.info("Delayed task scheduled successfully");
        } catch (Exception e) {
            log.error("Failed to schedule delayed task", e);
        }
    }

    @Job(name = "Delayed task")
    public void executeDelayedTask(String prompt, String modelId) {
        log.info("Executing delayed task: '{}', modelId={}", prompt, modelId);
        try {
            String taskId = UUID.randomUUID().toString();
            String conversationId = "delayed-" + Instant.now().toEpochMilli();
            Task task = new Task(
                taskId, conversationId, prompt,
                TaskStatus.TODO, null, null,
                Instant.now(), Instant.now()
            );
            taskRepository.save(task);
            log.info("Task saved to DB: {}", taskId);

            Model model = resolveModel(modelId);
            taskManager.executeTask(taskId, model);
            log.info("Delayed task execution completed: {}", taskId);
        } catch (Exception e) {
            log.error("Delayed task execution failed: '{}'", prompt, e);
            throw e;
        }
    }

    /**
     * Delete a recurring task and cancel its scheduled job.
     */
    public void deleteRecurringTask(String id) {
        BackgroundJob.deleteRecurringJob("recurring-" + id);
        taskRepository.deleteRecurringTaskExecutions(id);
        taskRepository.deleteRecurringChatMemory(id);
        taskRepository.deleteRecurringTask(id);
    }

    /**
     * Validate a cron expression without scheduling anything.
     */
    public void validateCron(String cronExpression) {
        org.jobrunr.scheduling.cron.CronExpression.create(cronExpression);
    }

    private Model resolveModel(String modelId) {
        if (modelId != null) {
            return modelRegistry.getAllModels().stream()
                .filter(m -> m.id().equals(modelId))
                .findFirst()
                .orElse(null);
        }
        return null;
    }
}
