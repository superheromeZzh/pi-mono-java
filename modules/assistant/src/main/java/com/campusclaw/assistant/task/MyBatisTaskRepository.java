package com.campusclaw.assistant.task;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.campusclaw.assistant.mapper.TaskMapper;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

public class MyBatisTaskRepository implements TaskRepository {

    private static final TypeReference<List<ExecutionResult>> EXECUTION_RESULTS_TYPE = new TypeReference<>() {};

    private final TaskMapper taskMapper;
    private final ObjectMapper objectMapper;

    public MyBatisTaskRepository(TaskMapper taskMapper, ObjectMapper objectMapper) {
        this.taskMapper = taskMapper;
        this.objectMapper = objectMapper;
    }

    @Override
    public void save(Task task) {
        TaskEntity entity = new TaskEntity(task);
        if (taskMapper.findById(task.id()) != null) {
            taskMapper.update(entity);
        } else {
            taskMapper.insert(entity);
        }
    }

    @Override
    public Optional<Task> findById(String id) {
        TaskEntity entity = taskMapper.findById(id);
        return Optional.ofNullable(entity).map(TaskEntity::toDomain);
    }

    @Override
    public List<Task> findByStatus(TaskStatus status) {
        return taskMapper.findByStatus(status.name()).stream()
                .map(TaskEntity::toDomain)
                .toList();
    }

    @Override
    public List<Task> findAll() {
        return taskMapper.findAll().stream()
                .map(TaskEntity::toDomain)
                .toList();
    }

    @Override
    public List<RecurringTask> findRecurringTasks() {
        return taskMapper.findAllRecurring().stream()
                .map(this::toDomain)
                .toList();
    }

    @Override
    public void saveRecurringTask(RecurringTask task) {
        taskMapper.insertRecurring(new RecurringTaskEntity(task));
    }

    @Override
    public void deleteRecurringTask(String id) {
        taskMapper.deleteRecurring(id);
    }

    @Override
    public void deleteTask(String id) {
        taskMapper.deleteTask(id);
    }

    @Override
    public Optional<RecurringTask> findRecurringTaskById(String id) {
        RecurringTaskEntity entity = taskMapper.findRecurringById(id);
        return Optional.ofNullable(entity).map(this::toDomain);
    }

    @Override
    public void updateRecurringTask(RecurringTask task) {
        String json = serializeExecutionResults(task.executionResults());
        taskMapper.updateRecurring(new RecurringTaskEntity(task, json));
    }

    @Override
    public void deleteRecurringTaskExecutions(String recurringId) {
        taskMapper.deleteRecurringTaskExecutions(recurringId);
    }

    @Override
    public void deleteRecurringChatMemory(String recurringId) {
        taskMapper.deleteRecurringChatMemory(recurringId);
    }

    private RecurringTask toDomain(RecurringTaskEntity entity) {
        List<ExecutionResult> results = deserializeExecutionResults(entity.executionResults());
        return entity.toDomain(results);
    }

    private List<ExecutionResult> deserializeExecutionResults(String json) {
        if (json == null || json.isBlank()) {
            return Collections.emptyList();
        }
        try {
            return objectMapper.readValue(json, EXECUTION_RESULTS_TYPE);
        } catch (JsonProcessingException e) {
            return Collections.emptyList();
        }
    }

    private String serializeExecutionResults(List<ExecutionResult> results) {
        if (results == null || results.isEmpty()) {
            return "[]";
        }
        try {
            return objectMapper.writeValueAsString(results);
        } catch (JsonProcessingException e) {
            return "[]";
        }
    }
}
