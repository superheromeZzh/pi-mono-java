package com.campusclaw.assistant.task;

import java.util.List;
import java.util.Optional;

public interface TaskRepository {

    void save(Task task);

    Optional<Task> findById(String id);

    List<Task> findByStatus(TaskStatus status);

    List<Task> findAll();

    List<RecurringTask> findRecurringTasks();

    void saveRecurringTask(RecurringTask task);

    void deleteRecurringTask(String id);

    void deleteTask(String id);

    Optional<RecurringTask> findRecurringTaskById(String id);

    void updateRecurringTask(RecurringTask task);

    void deleteRecurringTaskExecutions(String recurringId);

    void deleteRecurringChatMemory(String recurringId);
}
