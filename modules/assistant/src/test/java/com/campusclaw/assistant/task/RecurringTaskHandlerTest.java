package com.campusclaw.assistant.task;

import com.campusclaw.ai.model.ModelRegistry;
import com.campusclaw.ai.types.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.jobrunr.jobs.lambdas.JobLambda;
import org.jobrunr.scheduling.JobScheduler;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.Mockito.lenient;

@ExtendWith(MockitoExtension.class)
class RecurringTaskHandlerTest {

    @Mock private TaskManager taskManager;
    @Mock private TaskRepository taskRepository;
    @Mock private JobScheduler jobScheduler;
    private ModelRegistry modelRegistry;
    private RecurringTaskHandler handler;

    private static final Model GLM_5 = new Model(
        "glm-5", "GLM-5",
        Api.OPENAI_COMPLETIONS, Provider.ZAI,
        "https://api.z.ai/api/coding/paas/v4", true,
        List.of(InputModality.TEXT),
        new ModelCost(1.0, 3.2, 0.2, 0),
        204800, 131072, null, "zai", null
    );

    private static final Model CLAUDE_SONNET = new Model(
        "claude-sonnet-4-20250514", "Claude Sonnet 4",
        Api.ANTHROPIC_MESSAGES, Provider.ANTHROPIC,
        "https://api.anthropic.com", true,
        List.of(InputModality.TEXT, InputModality.IMAGE),
        new ModelCost(3.0, 15.0, 0.3, 3.75),
        200000, 16000, null, null, null
    );

    @BeforeEach
    void setUp() {
        modelRegistry = new ModelRegistry();
        modelRegistry.register(GLM_5);
        modelRegistry.register(CLAUDE_SONNET);
        handler = new RecurringTaskHandler(taskManager, taskRepository, jobScheduler, modelRegistry);
        lenient().when(taskManager.executeTask(anyString(), any())).thenReturn(CompletableFuture.completedFuture("done"));
    }

    private RecurringTask recurringTask(String id, String modelId) {
        return new RecurringTask(id, "test-task", null, "0 9 * * *", "run report", modelId);
    }

    private void stubFindRecurringTaskById(String id, String modelId) {
        when(taskRepository.findRecurringTaskById(id))
            .thenReturn(Optional.of(recurringTask(id, modelId)));
    }

    @Nested
    class ExecuteRecurringTask {

        @Test
        void restoresModelFromModelId() {
            String taskId = "rec-1";
            stubFindRecurringTaskById(taskId, "glm-5");

            handler.executeRecurringTask(taskId);

            ArgumentCaptor<Model> modelCaptor = ArgumentCaptor.forClass(Model.class);
            verify(taskManager).executeTask(anyString(), modelCaptor.capture());
            assertNotNull(modelCaptor.getValue());
            assertEquals("glm-5", modelCaptor.getValue().id());
            assertEquals(Provider.ZAI, modelCaptor.getValue().provider());
        }

        @Test
        void passesNullModelWhenModelIdIsNull() {
            String taskId = "rec-2";
            stubFindRecurringTaskById(taskId, null);

            handler.executeRecurringTask(taskId);

            verify(taskManager).executeTask(anyString(), isNull());
        }

        @Test
        void passesNullModelWhenModelIdNotFound() {
            String taskId = "rec-3";
            stubFindRecurringTaskById(taskId, "nonexistent-model");

            handler.executeRecurringTask(taskId);

            verify(taskManager).executeTask(anyString(), isNull());
        }

        @Test
        void savesTaskBeforeExecution() {
            String taskId = "rec-4";
            stubFindRecurringTaskById(taskId, "glm-5");

            handler.executeRecurringTask(taskId);

            ArgumentCaptor<Task> taskCaptor = ArgumentCaptor.forClass(Task.class);
            verify(taskRepository).save(taskCaptor.capture());
            assertEquals("run report", taskCaptor.getValue().prompt());
            assertEquals(TaskStatus.TODO, taskCaptor.getValue().status());
        }

        @Test
        void noOpsWhenRecurringTaskNotFound() {
            when(taskRepository.findRecurringTaskById("rec-nonexistent"))
                .thenReturn(Optional.empty());

            handler.executeRecurringTask("rec-nonexistent");

            verify(taskManager, never()).executeTask(anyString(), any());
            verify(taskRepository, never()).save(any());
        }

        @Test
        void updatesRecurringTaskAfterExecution() {
            String taskId = "rec-5";
            stubFindRecurringTaskById(taskId, "glm-5");

            handler.executeRecurringTask(taskId);

            ArgumentCaptor<RecurringTask> captor = ArgumentCaptor.forClass(RecurringTask.class);
            verify(taskRepository).updateRecurringTask(captor.capture());
            RecurringTask updated = captor.getValue();
            assertEquals("COMPLETED", updated.lastStatus());
            assertNotNull(updated.lastExecutionAt());
            assertNotNull(updated.executionResults());
            assertEquals(1, updated.executionResults().size());
            assertEquals("COMPLETED", updated.executionResults().get(0).status());
            assertEquals("done", updated.executionResults().get(0).result());
        }

        @Test
        void cleansUpTempTaskAfterExecution() {
            String taskId = "rec-6";
            stubFindRecurringTaskById(taskId, "glm-5");

            handler.executeRecurringTask(taskId);

            ArgumentCaptor<Task> taskCaptor = ArgumentCaptor.forClass(Task.class);
            verify(taskRepository).save(taskCaptor.capture());
            String tempTaskId = taskCaptor.getValue().id();
            verify(taskRepository).deleteTask(tempTaskId);
            verify(taskRepository).deleteRecurringChatMemory(taskId);
        }

        @Test
        void handlesFailedExecution() {
            String taskId = "rec-7";
            stubFindRecurringTaskById(taskId, "glm-5");
            when(taskManager.executeTask(anyString(), any()))
                .thenReturn(CompletableFuture.failedFuture(new RuntimeException("boom")));

            handler.executeRecurringTask(taskId);

            ArgumentCaptor<RecurringTask> captor = ArgumentCaptor.forClass(RecurringTask.class);
            verify(taskRepository).updateRecurringTask(captor.capture());
            assertEquals("FAILED", captor.getValue().lastStatus());
            assertEquals("boom", captor.getValue().executionResults().get(0).result());
        }

        @Test
        void accumulatesExecutionResults() {
            String taskId = "rec-8";
            List<ExecutionResult> existingResults = List.of(
                new ExecutionResult(Instant.now().minusSeconds(60), "COMPLETED", "old result", 1000L)
            );
            when(taskRepository.findRecurringTaskById(taskId))
                .thenReturn(Optional.of(new RecurringTask(taskId, "test-task", null,
                    "0 9 * * *", "run report", "glm-5", null, null, existingResults)));

            handler.executeRecurringTask(taskId);

            ArgumentCaptor<RecurringTask> captor = ArgumentCaptor.forClass(RecurringTask.class);
            verify(taskRepository).updateRecurringTask(captor.capture());
            assertEquals(2, captor.getValue().executionResults().size());
            assertEquals("old result", captor.getValue().executionResults().get(0).result());
            assertEquals("done", captor.getValue().executionResults().get(1).result());
        }
    }

    @Nested
    class ExecuteDelayedTask {

        @Test
        void restoresModelFromModelId() {
            handler.executeDelayedTask("check logs", "glm-5");

            ArgumentCaptor<Model> modelCaptor = ArgumentCaptor.forClass(Model.class);
            verify(taskManager).executeTask(anyString(), modelCaptor.capture());
            assertNotNull(modelCaptor.getValue());
            assertEquals("glm-5", modelCaptor.getValue().id());
        }

        @Test
        void passesNullModelWhenModelIdIsNull() {
            handler.executeDelayedTask("check logs", null);

            verify(taskManager).executeTask(anyString(), isNull());
        }

        @Test
        void savesTaskWithCorrectPrompt() {
            handler.executeDelayedTask("summarize today", "glm-5");

            ArgumentCaptor<Task> taskCaptor = ArgumentCaptor.forClass(Task.class);
            verify(taskRepository).save(taskCaptor.capture());
            assertEquals("summarize today", taskCaptor.getValue().prompt());
            assertTrue(taskCaptor.getValue().conversationId().startsWith("delayed-"));
        }
    }

    @Nested
    class ScheduleDelayedTask {

        @Test
        void delegatesToJobScheduler() {
            handler.scheduleDelayedTask("check logs", Duration.ofMinutes(10), "glm-5");

            verify(jobScheduler).schedule(any(java.time.Instant.class), any(JobLambda.class));
        }
    }
}
