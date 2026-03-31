package com.campusclaw.codingagent.tool.hybrid;

import com.campusclaw.agent.tool.AgentTool;
import com.campusclaw.agent.tool.AgentToolResult;
import com.campusclaw.agent.tool.AgentToolUpdateCallback;
import com.campusclaw.agent.tool.CancellationToken;
import com.campusclaw.codingagent.tool.execution.ExecutionMode;
import com.campusclaw.codingagent.tool.execution.ExecutionRouter;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * 混合模式 Read 工具 - 统一入口，智能路由
 */
@Component
public class HybridReadTool implements AgentTool {

    private final ExecutionRouter router;
    private final ObjectMapper mapper = new ObjectMapper();

    @Autowired
    public HybridReadTool(ExecutionRouter router) {
        this.router = router;
    }

    @Override
    public String name() {
        return "read";
    }

    @Override
    public String label() {
        return "Read";
    }

    @Override
    public String description() {
        return "Read file contents. Automatically chooses local or sandbox execution based on security policy. " +
               "Use _executionMode parameter to force specific mode (local/sandbox/auto).";
    }

    @Override
    public JsonNode parameters() {
        ObjectNode props = mapper.createObjectNode();
        props.set("path", mapper.createObjectNode()
            .put("type", "string")
            .put("description", "File path to read (relative or absolute)"));
        props.set("offset", mapper.createObjectNode()
            .put("type", "integer")
            .put("description", "Starting line number (1-indexed, optional)"));
        props.set("limit", mapper.createObjectNode()
            .put("type", "integer")
            .put("description", "Maximum number of lines to read (optional)"));
        props.set("_executionMode", mapper.createObjectNode()
            .put("type", "string")
            .put("enum", List.of("local", "sandbox", "auto"))
            .put("description", "Override execution mode: local (fast), sandbox (safe), auto (smart). Default: auto"));

        return mapper.createObjectNode()
            .put("type", "object")
            .<ObjectNode>set("properties", props)
            .set("required", mapper.createArrayNode().add("path"));
    }

    @Override
    public AgentToolResult execute(String toolCallId, Map<String, Object> params,
                                    CancellationToken signal,
                                    AgentToolUpdateCallback onUpdate) throws Exception {
        ExecutionMode explicitMode = extractMode(params);
        return router.route(name(), params, explicitMode, signal, onUpdate);
    }

    private ExecutionMode extractMode(Map<String, Object> params) {
        Object mode = params.get("_executionMode");
        if (mode != null) {
            try {
                return ExecutionMode.valueOf(mode.toString().toUpperCase());
            } catch (IllegalArgumentException e) {
                // 忽略无效值，使用默认
            }
        }
        return null;
    }
}
