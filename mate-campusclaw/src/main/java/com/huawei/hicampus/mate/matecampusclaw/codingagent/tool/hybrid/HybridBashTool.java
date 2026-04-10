package com.huawei.hicampus.mate.matecampusclaw.codingagent.tool.hybrid;

import com.huawei.hicampus.mate.matecampusclaw.agent.tool.AgentTool;
import com.huawei.hicampus.mate.matecampusclaw.agent.tool.AgentToolResult;
import com.huawei.hicampus.mate.matecampusclaw.agent.tool.AgentToolUpdateCallback;
import com.huawei.hicampus.mate.matecampusclaw.agent.tool.CancellationToken;
import com.huawei.hicampus.mate.matecampusclaw.codingagent.tool.execution.ExecutionMode;
import com.huawei.hicampus.mate.matecampusclaw.codingagent.tool.execution.ExecutionRouter;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * 混合模式 Bash 工具 - 统一入口，智能路由
 */
@Component
@ConditionalOnProperty(name = "tool.execution.hybrid-enabled", havingValue = "true", matchIfMissing = false)
public class HybridBashTool implements AgentTool {

    private final ExecutionRouter router;
    private final ObjectMapper mapper = new ObjectMapper();

    @Autowired
    public HybridBashTool(ExecutionRouter router) {
        this.router = router;
    }

    @Override
    public String name() {
        return "bash";
    }

    @Override
    public String label() {
        return "Bash";
    }

    @Override
    public String description() {
        return "Execute bash commands. Dangerous commands automatically use sandbox. " +
               "Use _executionMode parameter to force specific mode.";
    }

    @Override
    public JsonNode parameters() {
        ObjectNode props = mapper.createObjectNode();
        props.set("command", mapper.createObjectNode()
            .put("type", "string")
            .put("description", "The bash command to execute"));
        props.set("timeout", mapper.createObjectNode()
            .put("type", "integer")
            .put("description", "Timeout in seconds (optional)"));
        ArrayNode enumValues = mapper.createArrayNode();
        enumValues.add("local");
        enumValues.add("sandbox");
        enumValues.add("auto");
        ObjectNode execModeNode = mapper.createObjectNode()
            .put("type", "string");
        execModeNode.set("enum", enumValues);
        execModeNode.put("description", "Override execution mode: local (fast), sandbox (safe), auto (smart). " +
                              "Dangerous commands like 'rm -rf /' always use sandbox.");
        props.set("_executionMode", execModeNode);

        return mapper.createObjectNode()
            .put("type", "object")
            .<ObjectNode>set("properties", props)
            .set("required", mapper.createArrayNode().add("command"));
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
