package com.campusclaw.codingagent.command.builtin;

import com.campusclaw.assistant.task.ExecutionResult;
import com.campusclaw.assistant.task.RecurringTask;
import com.campusclaw.assistant.task.RecurringTaskHandler;
import com.campusclaw.assistant.task.Task;
import com.campusclaw.assistant.task.TaskManager;
import com.campusclaw.assistant.task.TaskRepository;
import com.campusclaw.ai.types.Model;
import com.campusclaw.codingagent.command.SlashCommand;
import com.campusclaw.codingagent.command.SlashCommandContext;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

public class TaskCommand implements SlashCommand {

    private final TaskManager taskManager;
    private final TaskRepository taskRepository;
    private final RecurringTaskHandler recurringTaskHandler;

    public TaskCommand(TaskManager taskManager, TaskRepository taskRepository,
                       RecurringTaskHandler recurringTaskHandler) {
        this.taskManager = taskManager;
        this.taskRepository = taskRepository;
        this.recurringTaskHandler = recurringTaskHandler;
    }

    @Override
    public String name() {
        return "task";
    }

    @Override
    public String description() {
        return "Manage tasks: /task create|schedule|list|recurring";
    }

    @Override
    public void execute(SlashCommandContext context, String arguments) {
        String args = arguments.trim();
        if (args.isEmpty()) {
            printUsage(context);
            return;
        }

        String[] parts = args.split("\\s+", 3);
        String subcommand = parts[0].toLowerCase();

        switch (subcommand) {
            case "create" -> handleCreate(context, args.substring("create".length()).trim());
            case "schedule" -> handleSchedule(context, parts);
            case "delay" -> handleDelay(context, args);
            case "list" -> handleList(context);
            case "recurring" -> handleRecurring(context);
            case "delete" -> handleDelete(context, args.substring("delete".length()).trim());
            case "delete-recurring" -> handleDeleteRecurring(context, args.substring("delete-recurring".length()).trim());
            default -> {
                context.output().println("Unknown subcommand: " + subcommand);
                printUsage(context);
            }
        }
    }

    private void handleCreate(SlashCommandContext context, String prompt) {
        if (prompt.isEmpty()) {
            context.output().println("Usage: /task create <prompt>");
            return;
        }

        String taskId = UUID.randomUUID().toString();
        String conversationId = "task-" + taskId;
        Task task = new Task(
            taskId, conversationId, prompt,
            com.campusclaw.assistant.task.TaskStatus.TODO, null, null,
            Instant.now(), Instant.now()
        );
        taskRepository.save(task);

        Model currentModel = context.session().getAgent().getState().getModel();

        context.output().println("Task created: " + taskId);
        context.output().println("Status: executing...");

        taskManager.executeTask(taskId, currentModel);
    }

    private void handleSchedule(SlashCommandContext context, String[] parts) {
        if (parts.length < 3) {
            context.output().println("Usage: /task schedule <name> <cron> <prompt>");
            return;
        }

        String name = parts[1];
        String cronAndPrompt = parts[2];

        // Cron expressions are 5 or 6 fields; extract them by counting spaces
        String cron;
        String prompt;
        String[] cronFields = cronAndPrompt.split("\\s+");
        if (cronFields.length < 6) {
            // Not enough fields for a valid cron + prompt
            context.output().println("Usage: /task schedule <name> <cron> <prompt>");
            context.output().println("  Cron must have 5 (minute) or 6 (second) fields.");
            return;
        }
        // Try 6-field (second resolution) first, then 5-field (minute resolution)
        if (cronFields.length >= 7) {
            // 6 fields cron + prompt
            cron = String.join(" ", Arrays.copyOfRange(cronFields, 0, 6));
            prompt = String.join(" ", Arrays.copyOfRange(cronFields, 6, cronFields.length));
        } else {
            // Exactly 6 tokens — ambiguous. Assume 5-field cron + prompt of 1 word,
            // or 6-field cron with no prompt. Check if 6th field looks like a cron field.
            // Default: 5-field cron + prompt
            cron = String.join(" ", Arrays.copyOfRange(cronFields, 0, 5));
            prompt = cronFields[5];
        }

        try {
            recurringTaskHandler.validateCron(cron);
        } catch (Exception e) {
            context.output().println("Invalid cron expression: " + cron);
            return;
        }

        String id = UUID.randomUUID().toString();
        Model currentModel = context.session().getAgent().getState().getModel();
        String modelId = currentModel != null ? currentModel.id() : null;
        RecurringTask recurringTask = new RecurringTask(id, name, null, cron, prompt, modelId);
        taskRepository.saveRecurringTask(recurringTask);
        recurringTaskHandler.scheduleRecurringTasks();

        context.output().println("Recurring task scheduled: " + id);
        context.output().println("  Name: " + name);
        context.output().println("  Cron: " + cron);
    }

    private void handleList(SlashCommandContext context) {
        List<Task> tasks = taskRepository.findAll();
        if (tasks.isEmpty()) {
            context.output().println("No tasks found.");
            return;
        }

        context.output().println("Tasks:");
        for (Task task : tasks) {
            context.output().println("  [" + task.status() + "] " + task.id()
                + " - " + truncate(task.prompt(), 60));
        }
    }

    private void handleRecurring(SlashCommandContext context) {
        List<RecurringTask> tasks = taskRepository.findRecurringTasks();
        if (tasks.isEmpty()) {
            context.output().println("No recurring tasks found.");
            return;
        }

        context.output().println("Recurring tasks:");
        for (RecurringTask task : tasks) {
            context.output().println("  " + task.id() + " - " + task.name()
                + " (" + task.cronExpression() + ")");
            context.output().println("    Prompt: " + truncate(task.prompt(), 60));
            if (task.lastStatus() != null) {
                context.output().println("    Last status: " + task.lastStatus());
                if (task.lastExecutionAt() != null) {
                    String time = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
                        .withZone(ZoneId.systemDefault()).format(task.lastExecutionAt());
                    context.output().println("    Last executed: " + time);
                }
            }
            if (task.executionResults() != null && !task.executionResults().isEmpty()) {
                context.output().println("    Recent executions (" + task.executionResults().size() + " total):");
                int start = Math.max(0, task.executionResults().size() - 3);
                for (int i = start; i < task.executionResults().size(); i++) {
                    ExecutionResult r = task.executionResults().get(i);
                    String time = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
                        .withZone(ZoneId.systemDefault()).format(r.executedAt());
                    context.output().println("      [" + r.status() + "] " + time
                        + " (" + r.durationMs() + "ms) " + truncate(r.result(), 60));
                }
            }
        }
    }

    private void handleDelete(SlashCommandContext context, String id) {
        if (id.isEmpty()) {
            context.output().println("Usage: /task delete <id>");
            return;
        }
        if (taskRepository.findById(id).isEmpty()) {
            context.output().println("Task not found: " + id);
            return;
        }
        taskRepository.deleteTask(id);
        context.output().println("Task deleted: " + id);
    }

    private void handleDeleteRecurring(SlashCommandContext context, String id) {
        if (id.isEmpty()) {
            context.output().println("Usage: /task delete-recurring <id>");
            return;
        }
        if (taskRepository.findRecurringTaskById(id).isEmpty()) {
            context.output().println("Recurring task not found: " + id);
            return;
        }
        recurringTaskHandler.deleteRecurringTask(id);
        context.output().println("Recurring task deleted: " + id);
    }

    private void handleDelay(SlashCommandContext context, String args) {
        // Format: /task delay <minutes> <prompt>
        String rest = args.substring("delay".length()).trim();
        int spaceIndex = rest.indexOf(' ');
        if (spaceIndex == -1) {
            context.output().println("Usage: /task delay <minutes> <prompt>");
            return;
        }
        String minutesStr = rest.substring(0, spaceIndex);
        String prompt = rest.substring(spaceIndex + 1).trim();

        try {
            long minutes = Long.parseLong(minutesStr);
            Duration delay = Duration.ofMinutes(minutes);
            Model currentModel = context.session().getAgent().getState().getModel();
            String modelId = currentModel != null ? currentModel.id() : null;
            recurringTaskHandler.scheduleDelayedTask(prompt, delay, modelId);

            context.output().println("Delayed task scheduled:");
            context.output().println("  Delay: " + minutes + " minute(s)");
            context.output().println("  Prompt: " + prompt);
            context.output().println("  Will execute at: " + Instant.now().plus(delay));
        } catch (NumberFormatException e) {
            context.output().println("Invalid number of minutes: " + minutesStr);
        }
    }

    private void printUsage(SlashCommandContext context) {
        context.output().println("Usage:");
        context.output().println("  /task create <prompt>          Create and execute a one-off task");
        context.output().println("  /task schedule <name> <cron> <prompt>  Schedule a recurring task");
        context.output().println("  /task delay <minutes> <prompt> Schedule a one-time delayed task");
        context.output().println("  /task list                    List all tasks");
        context.output().println("  /task recurring               List recurring tasks");
        context.output().println("  /task delete <id>             Delete a task");
        context.output().println("  /task delete-recurring <id>   Delete a recurring task");
    }

    private static String truncate(String s, int maxLen) {
        if (s.length() <= maxLen) return s;
        return s.substring(0, maxLen) + "...";
    }
}
