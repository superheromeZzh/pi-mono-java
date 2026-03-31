package com.campusclaw.assistant.task;

import org.jobrunr.jobs.annotations.Job;
import org.springframework.stereotype.Component;

@Component
public class TaskHandler {

    private final TaskManager taskManager;

    public TaskHandler(TaskManager taskManager) {
        this.taskManager = taskManager;
    }

    @Job(name = "Execute task %0", retries = 3)
    public void executeTask(String taskId) {
        taskManager.executeTask(taskId);
    }
}
