/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.hicampus.mate.matecampusclaw.codingagent.tool.hybrid;

import java.util.Locale;
import java.util.Map;

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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * 混合模式 Grep 工具 - 统一入口，智能路由
 *
 * @version [br_eCampusCore 25.1.0_Next, 2026/05/06]
 * @since [br_eCampusCore 25.1.0_Next]
 */
@Component
@ConditionalOnProperty(name = "tool.execution.hybrid-enabled", havingValue = "true", matchIfMissing = false)
public class HybridGrepTool implements AgentTool {

    private static final Logger log = LoggerFactory.getLogger(HybridGrepTool.class);

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
        return "Search file contents using regular expressions. "
                + "Use _executionMode parameter to force specific mode.";
    }

    @Override
    public JsonNode parameters() {
        ObjectNode props = mapper.createObjectNode();
        props.set(
                "pattern",
                mapper.createObjectNode()
                        .put("type", "string")
                        .put("description", "Regular expression pattern to search for"));
        props.set(
                "path",
                mapper.createObjectNode()
                        .put("type", "string")
                        .put("description", "File or directory to search in (optional)"));
        props.set(
                "glob",
                mapper.createObjectNode()
                        .put("type", "string")
                        .put("description", "Glob pattern to filter files (optional)"));
        ArrayNode enumValues = mapper.createArrayNode();
        enumValues.add("local");
        enumValues.add("sandbox");
        enumValues.add("auto");
        ObjectNode execModeNode = mapper.createObjectNode().put("type", "string");
        execModeNode.set("enum", enumValues);
        execModeNode.put("description", "Override execution mode");
        props.set("_executionMode", execModeNode);

        return mapper.createObjectNode()
                .put("type", "object")
                .<ObjectNode>set("properties", props)
                .set("required", mapper.createArrayNode().add("pattern"));
    }

    @Override
    public AgentToolResult execute(
            String toolCallId, Map<String, Object> params, CancellationToken signal, AgentToolUpdateCallback onUpdate)
            throws Exception {
        ExecutionMode explicitMode = extractMode(params);
        return router.route(name(), params, explicitMode, signal, onUpdate);
    }

    private ExecutionMode extractMode(Map<String, Object> params) {
        Object mode = params.get("_executionMode");
        if (mode != null) {
            try {
                return ExecutionMode.valueOf(mode.toString().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException e) {
                // 忽略无效值
                log.debug("ignoring unknown _executionMode '{}', router will pick default", mode, e);
            }
        }
        return null;
    }
}
