package com.campusclaw.codingagent.loop;

import java.util.List;
import java.util.Map;

import com.campusclaw.agent.tool.AgentTool;
import com.campusclaw.agent.tool.AgentToolResult;
import com.campusclaw.agent.tool.AgentToolUpdateCallback;
import com.campusclaw.agent.tool.CancellationToken;
import com.campusclaw.ai.types.TextContent;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import org.springframework.stereotype.Component;

/**
 * Agent tool for in-session recurring prompts. Unlike CronTool (which creates
 * isolated background agents), LoopTool injects prompts into the current
 * conversation's submit queue so responses appear directly in the chat.
 *
 * Use this for tasks the user wants to see repeated in the current session.
 * Use CronTool for persistent background tasks that survive across sessions.
 */
@Component
public class LoopTool implements AgentTool {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final LoopManager loopManager;

    public LoopTool(LoopManager loopManager) {
        this.loopManager = loopManager;
    }

    @Override
    public String name() {
        return "loop";
    }

    @Override
    public String label() {
        return "Loop";
    }

    @Override
    public String description() {
        return "Manage in-session recurring prompts. Loops run within the current conversation — "
                + "responses appear directly in chat. Session-scoped (lost when you exit). "
                + "Use this instead of cron when the user wants to see repeated output in the current chat.";
    }

    @Override
    public JsonNode parameters() {
        var props = MAPPER.createObjectNode();

        props.set("action", MAPPER.createObjectNode()
                .put("type", "string")
                .put("description", "Action: start, stop, stop_all, list")
                .set("enum", MAPPER.createArrayNode()
                        .add("start").add("stop").add("stop_all").add("list")));

        props.set("prompt", MAPPER.createObjectNode()
                .put("type", "string")
                .put("description", "The prompt to repeat (for start)"));

        props.set("interval_ms", MAPPER.createObjectNode()
                .put("type", "integer")
                .put("description", "Interval in milliseconds (for start, default 600000 = 10 minutes)"));

        props.set("id", MAPPER.createObjectNode()
                .put("type", "string")
                .put("description", "Loop ID (for stop)"));

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
            return text("Error: action is required");
        }

        if (!loopManager.isInitialized()) {
            return text("Error: Loop is only available in interactive mode");
        }

        return switch (action) {
            case "start" -> handleStart(params);
            case "stop" -> handleStop(params);
            case "stop_all" -> handleStopAll();
            case "list" -> handleList();
            default -> text("Error: unknown action '" + action + "'. Valid: start, stop, stop_all, list");
        };
    }

    private AgentToolResult handleStart(Map<String, Object> params) {
        String prompt = (String) params.get("prompt");
        if (prompt == null || prompt.isBlank()) {
            return text("Error: prompt is required for start");
        }

        long intervalMs = 600_000; // default 10 minutes
        if (params.get("interval_ms") instanceof Number n) {
            intervalMs = n.longValue();
        }
        if (intervalMs < 1000) {
            return text("Error: interval must be at least 1000ms (1 second)");
        }

        String id = loopManager.start(prompt, intervalMs);
        return text("Started loop #" + id + " (every " + formatInterval(intervalMs) + "): " + prompt
                + "\n\nThe prompt will be submitted to the current conversation at each interval. "
                + "Use action 'stop' with id '" + id + "' to cancel.");
    }

    private AgentToolResult handleStop(Map<String, Object> params) {
        String id = (String) params.get("id");
        if (id == null || id.isBlank()) {
            return text("Error: id is required for stop");
        }
        boolean stopped = loopManager.stop(id);
        return text(stopped ? "Stopped loop #" + id : "Loop not found: #" + id);
    }

    private AgentToolResult handleStopAll() {
        int count = loopManager.stopAll();
        return text("Stopped " + count + " loop(s)");
    }

    private AgentToolResult handleList() {
        var loops = loopManager.list();
        if (loops.isEmpty()) {
            return text("No active loops.");
        }
        var sb = new StringBuilder();
        sb.append("Active loops (").append(loops.size()).append("):\n");
        for (var entry : loops) {
            sb.append("  #").append(entry.id())
                    .append("  every ").append(formatInterval(entry.intervalMs()))
                    .append("  ").append(entry.prompt())
                    .append("\n");
        }
        return text(sb.toString());
    }

    private static String formatInterval(long ms) {
        if (ms >= 3_600_000 && ms % 3_600_000 == 0) return (ms / 3_600_000) + "h";
        if (ms >= 60_000 && ms % 60_000 == 0) return (ms / 60_000) + "m";
        if (ms >= 1000 && ms % 1000 == 0) return (ms / 1000) + "s";
        return ms + "ms";
    }

    private AgentToolResult text(String msg) {
        return new AgentToolResult(List.of(new TextContent(msg)), null);
    }
}
