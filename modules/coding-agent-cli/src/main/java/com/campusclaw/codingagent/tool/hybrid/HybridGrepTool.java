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
 * 混合模式 Grep 工具 - 统一入口，智能路由
 */
@Component
public class HybridGrepTool implements AgentTool {

    private final ExecutionRouter router;
    private final ObjectMapper mapper = new ObjectMapper();

    @Autowired
    public HybridGrepTool(ExecutionRouter router) {
        this.router = router;
    }

    @Override
    public String name() {
        return "grep";
    }

    @Override
    public String label() {
        return "Grep";
    }

    @Override
    public String description() {
        return "Search file contents using regular expressions. " +
               "Use _executionMode parameter to force specific mode.";
    }

    @Override
    public JsonNode parameters() {
        ObjectNode props = mapper.createObjectNode();
        props.set("pattern", mapper.createObjectNode()
            .put("type", "string")
            .put("description", "Regular expression pattern to search for"));
        props.set("path", mapper.createObjectNode()
            .put("type", "string")
            .put("description", "File or directory to search in (optional)"));
        props.set("glob", mapper.createObjectNode()
            .put("type", "string")
            .put("description", "Glob pattern to filter files (optional)"));
        props.set("_executionMode", mapper.createObjectNode()
            .put("type", "string")
            .put("enum", List.of("local", "sandbox", "auto"))
            .put("description", "Override execution mode"));

        return mapper.createObjectNode()
            .put("type", "object")
            .<ObjectNode>set("properties", props)
            .set("required", mapper.createArrayNode().add("pattern"));
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
                // 忽略无效值
            }
        }
        return null;
    }
}
