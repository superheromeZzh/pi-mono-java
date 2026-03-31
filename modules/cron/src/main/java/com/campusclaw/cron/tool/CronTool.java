package com.campusclaw.cron.tool;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

import com.campusclaw.agent.tool.AgentTool;
import com.campusclaw.agent.tool.AgentToolResult;
import com.campusclaw.agent.tool.AgentToolUpdateCallback;
import com.campusclaw.agent.tool.CancellationToken;
import com.campusclaw.ai.types.ContentBlock;
import com.campusclaw.ai.types.TextContent;
import com.campusclaw.cron.CronService;
import com.campusclaw.cron.model.CronJob;
import com.campusclaw.cron.model.CronPayload;
import com.campusclaw.cron.model.CronSchedule;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import org.springframework.stereotype.Component;

/**
 * Agent tool for managing cron jobs via LLM conversation.
 */
@Component
public class CronTool implements AgentTool {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final DateTimeFormatter TIME_FMT =
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneId.systemDefault());

    private final CronService cronService;

    public CronTool(CronService cronService) {
        this.cronService = cronService;
    }

    @Override
    public String name() {
        return "cron";
    }

    @Override
    public String label() {
        return "Cron";
    }

    @Override
    public String description() {
        return "Manage persistent scheduled tasks (cron jobs) that run in isolated background agents. "
                + "Jobs survive across sessions and execute even when no conversation is open (with /cron install). "
                + "Output does NOT appear in the current chat — use the 'loop' tool instead if the user wants "
                + "to see repeated output in the current conversation.";
    }

    @Override
    public JsonNode parameters() {
        var props = MAPPER.createObjectNode();

        props.set("action", MAPPER.createObjectNode()
            .put("type", "string")
            .put("description", "Action: create, list, delete, trigger, status, runs")
            .set("enum", MAPPER.createArrayNode()
                .add("create").add("list").add("delete")
                .add("trigger").add("status").add("runs")));

        props.set("name", MAPPER.createObjectNode()
            .put("type", "string")
            .put("description", "Job name (for create)"));

        props.set("description", MAPPER.createObjectNode()
            .put("type", "string")
            .put("description", "Job description (for create, optional)"));

        props.set("schedule_type", MAPPER.createObjectNode()
            .put("type", "string")
            .put("description", "Schedule type: at, every, or cron (for create)")
            .set("enum", MAPPER.createArrayNode()
                .add("at").add("every").add("cron")));

        props.set("schedule_value", MAPPER.createObjectNode()
            .put("type", "string")
            .put("description", "Schedule value: ISO timestamp for 'at', milliseconds for 'every', cron expression for 'cron'"));

        props.set("timezone", MAPPER.createObjectNode()
            .put("type", "string")
            .put("description", "Timezone for cron expressions (optional, e.g. Asia/Shanghai)"));

        props.set("prompt", MAPPER.createObjectNode()
            .put("type", "string")
            .put("description", "The prompt to send to the agent (for create)"));

        props.set("model", MAPPER.createObjectNode()
            .put("type", "string")
            .put("description", "Model ID to use (for create, optional)"));

        props.set("system_prompt", MAPPER.createObjectNode()
            .put("type", "string")
            .put("description", "Custom system prompt (for create, optional)"));

        props.set("tools", MAPPER.createObjectNode()
            .put("type", "array")
            .put("description", "Allowed tool names (for create, optional, all tools if omitted)")
            .set("items", MAPPER.createObjectNode().put("type", "string")));

        props.set("job_id", MAPPER.createObjectNode()
            .put("type", "string")
            .put("description", "Job ID (for delete, trigger, status, runs)"));

        props.set("limit", MAPPER.createObjectNode()
            .put("type", "integer")
            .put("description", "Max number of run records to return (for runs, default 10)"));

        return MAPPER.createObjectNode()
            .put("type", "object")
            .<ObjectNode>set("properties", props)
            .set("required", MAPPER.createArrayNode().add("action"));
    }

    @Override
    public AgentToolResult execute(String toolCallId, Map<String, Object> params,
                                    CancellationToken signal, AgentToolUpdateCallback onUpdate) {
        String action = (String) params.get("action");
        if (action == null) {
            return textResult("Error: action is required");
        }
        return switch (action) {
            case "create" -> handleCreate(params);
            case "list" -> handleList();
            case "delete" -> handleDelete(params);
            case "trigger" -> handleTrigger(params);
            case "status" -> handleStatus(params);
            case "runs" -> handleRuns(params);
            default -> textResult("Error: unknown action '" + action + "'. Valid: create, list, delete, trigger, status, runs");
        };
    }

    private AgentToolResult handleCreate(Map<String, Object> params) {
        String name = (String) params.get("name");
        if (name == null || name.isBlank()) {
            return textResult("Error: name is required for create");
        }

        String scheduleType = (String) params.get("schedule_type");
        String scheduleValue = (String) params.get("schedule_value");
        if (scheduleType == null || scheduleValue == null) {
            return textResult("Error: schedule_type and schedule_value are required for create");
        }

        String prompt = (String) params.get("prompt");
        if (prompt == null || prompt.isBlank()) {
            return textResult("Error: prompt is required for create");
        }

        CronSchedule schedule;
        try {
            schedule = parseSchedule(scheduleType, scheduleValue, (String) params.get("timezone"));
        } catch (IllegalArgumentException e) {
            return textResult("Error: " + e.getMessage());
        }

        @SuppressWarnings("unchecked")
        var tools = (List<String>) params.get("tools");
        var payload = new CronPayload.AgentPrompt(
            prompt,
            (String) params.get("system_prompt"),
            (String) params.get("model"),
            tools
        );

        String description = (String) params.get("description");
        var job = cronService.createJob(name, description, schedule, payload);

        var sb = new StringBuilder();
        sb.append("Created cron job:\n");
        sb.append("  ID: ").append(job.id()).append("\n");
        sb.append("  Name: ").append(job.name()).append("\n");
        sb.append("  Schedule: ").append(formatSchedule(job.schedule())).append("\n");
        sb.append("  Enabled: ").append(job.enabled()).append("\n");
        return textResult(sb.toString());
    }

    private AgentToolResult handleList() {
        var jobs = cronService.listJobs();
        if (jobs.isEmpty()) {
            return textResult("No cron jobs configured.");
        }

        var sb = new StringBuilder();
        sb.append("Cron jobs (").append(jobs.size()).append("):\n\n");
        for (var job : jobs) {
            sb.append("  ").append(job.enabled() ? "[ON] " : "[OFF]");
            sb.append(" ").append(job.name());
            sb.append(" (").append(job.id()).append(")\n");
            sb.append("    Schedule: ").append(formatSchedule(job.schedule())).append("\n");
            if (job.state().lastRunAtMs() > 0) {
                sb.append("    Last run: ").append(TIME_FMT.format(Instant.ofEpochMilli(job.state().lastRunAtMs())));
                sb.append(" — ").append(job.state().lastRunStatus()).append("\n");
            }
            if (job.state().consecutiveErrors() > 0) {
                sb.append("    Consecutive errors: ").append(job.state().consecutiveErrors()).append("\n");
            }
            sb.append("\n");
        }
        return textResult(sb.toString());
    }

    private AgentToolResult handleDelete(Map<String, Object> params) {
        String jobId = (String) params.get("job_id");
        if (jobId == null) {
            return textResult("Error: job_id is required for delete");
        }
        boolean deleted = cronService.deleteJob(jobId);
        return textResult(deleted ? "Deleted job " + jobId : "Job not found: " + jobId);
    }

    private AgentToolResult handleTrigger(Map<String, Object> params) {
        String jobId = (String) params.get("job_id");
        if (jobId == null) {
            return textResult("Error: job_id is required for trigger");
        }
        try {
            var record = cronService.triggerJob(jobId);
            return textResult("Triggered job " + jobId + " → run " + record.runId()
                + " (" + record.status() + ")");
        } catch (IllegalArgumentException e) {
            return textResult("Error: " + e.getMessage());
        }
    }

    private AgentToolResult handleStatus(Map<String, Object> params) {
        String jobId = (String) params.get("job_id");
        if (jobId == null) {
            return textResult("Error: job_id is required for status");
        }
        var jobOpt = cronService.getJob(jobId);
        if (jobOpt.isEmpty()) {
            return textResult("Job not found: " + jobId);
        }
        var job = jobOpt.get();
        var sb = new StringBuilder();
        sb.append("Job: ").append(job.name()).append(" (").append(job.id()).append(")\n");
        sb.append("  Enabled: ").append(job.enabled()).append("\n");
        sb.append("  Schedule: ").append(formatSchedule(job.schedule())).append("\n");
        sb.append("  Total runs: ").append(job.state().totalRuns()).append("\n");
        if (job.state().runningAtMs() > 0) {
            sb.append("  Currently running since: ")
                .append(TIME_FMT.format(Instant.ofEpochMilli(job.state().runningAtMs()))).append("\n");
        }
        if (job.state().lastRunAtMs() > 0) {
            sb.append("  Last run: ").append(TIME_FMT.format(Instant.ofEpochMilli(job.state().lastRunAtMs())));
            sb.append(" — ").append(job.state().lastRunStatus()).append("\n");
        }
        if (job.state().nextRunAtMs() > 0) {
            sb.append("  Next run: ")
                .append(TIME_FMT.format(Instant.ofEpochMilli(job.state().nextRunAtMs()))).append("\n");
        }
        sb.append("  Consecutive errors: ").append(job.state().consecutiveErrors()).append("\n");
        return textResult(sb.toString());
    }

    private AgentToolResult handleRuns(Map<String, Object> params) {
        String jobId = (String) params.get("job_id");
        if (jobId == null) {
            return textResult("Error: job_id is required for runs");
        }
        int limit = 10;
        if (params.get("limit") instanceof Number n) {
            limit = n.intValue();
        }
        var runs = cronService.getRecentRuns(jobId, limit);
        if (runs.isEmpty()) {
            return textResult("No run records for job " + jobId);
        }

        var sb = new StringBuilder();
        sb.append("Recent runs for job ").append(jobId).append(":\n\n");
        for (var run : runs) {
            sb.append("  ").append(run.runId()).append(" ");
            sb.append(TIME_FMT.format(Instant.ofEpochMilli(run.startedAtMs())));
            sb.append(" → ").append(run.status());
            if (run.error() != null) {
                sb.append(" (").append(run.error()).append(")");
            }
            sb.append("\n");
            if (run.output() != null && !run.output().isBlank()) {
                sb.append("    Output: ").append(run.output()).append("\n");
            }
        }
        return textResult(sb.toString());
    }

    private CronSchedule parseSchedule(String type, String value, String timezone) {
        return switch (type) {
            case "at" -> {
                try {
                    long ts = Long.parseLong(value);
                    yield new CronSchedule.At(ts);
                } catch (NumberFormatException e) {
                    // Try parsing as ISO instant
                    try {
                        yield new CronSchedule.At(Instant.parse(value).toEpochMilli());
                    } catch (Exception e2) {
                        throw new IllegalArgumentException(
                            "Invalid 'at' value: expected epoch millis or ISO timestamp, got: " + value);
                    }
                }
            }
            case "every" -> {
                try {
                    long ms = Long.parseLong(value);
                    if (ms <= 0) throw new IllegalArgumentException("Interval must be positive");
                    yield new CronSchedule.Every(ms);
                } catch (NumberFormatException e) {
                    throw new IllegalArgumentException(
                        "Invalid 'every' value: expected milliseconds, got: " + value);
                }
            }
            case "cron" -> {
                try {
                    org.springframework.scheduling.support.CronExpression.parse(value);
                } catch (IllegalArgumentException e) {
                    throw new IllegalArgumentException("Invalid cron expression: " + value + " — " + e.getMessage());
                }
                yield new CronSchedule.CronExpr(value, timezone);
            }
            default -> throw new IllegalArgumentException("Unknown schedule type: " + type);
        };
    }

    private String formatSchedule(CronSchedule schedule) {
        return switch (schedule) {
            case CronSchedule.At at ->
                "once at " + TIME_FMT.format(Instant.ofEpochMilli(at.timestampMs()));
            case CronSchedule.Every every -> {
                long ms = every.intervalMs();
                if (ms >= 3_600_000) yield "every " + (ms / 3_600_000) + "h";
                if (ms >= 60_000) yield "every " + (ms / 60_000) + "m";
                if (ms >= 1_000) yield "every " + (ms / 1_000) + "s";
                yield "every " + ms + "ms";
            }
            case CronSchedule.CronExpr cron -> {
                String tz = cron.timezone() != null ? " (" + cron.timezone() + ")" : "";
                yield "cron: " + cron.expression() + tz;
            }
        };
    }

    private AgentToolResult textResult(String text) {
        return new AgentToolResult(List.of(new TextContent(text)), null);
    }
}
