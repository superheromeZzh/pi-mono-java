/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.campusclaw.codingagent.tool.hybrid;

import java.util.Locale;
import java.util.Map;

import com.campusclaw.agent.tool.AgentTool;
import com.campusclaw.agent.tool.AgentToolResult;
import com.campusclaw.agent.tool.AgentToolUpdateCallback;
import com.campusclaw.agent.tool.CancellationToken;
import com.campusclaw.codingagent.tool.execution.ExecutionMode;
import com.campusclaw.codingagent.tool.execution.ExecutionRouter;
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
 * 混合模式 Edit 工具 - 统一入口，智能路由
 *
 * @version [br_eCampusCore 25.1.0_Next, 2026/05/06]
 * @since [br_eCampusCore 25.1.0_Next]
 */
@Component
@ConditionalOnProperty(name = "tool.execution.hybrid-enabled", havingValue = "true", matchIfMissing = false)
public class HybridEditTool implements AgentTool {

    private static final Logger log = LoggerFactory.getLogger(HybridEditTool.class);

    private final ExecutionRouter router;
    private final ObjectMapper mapper = new ObjectMapper();

    @Autowired
    public HybridEditTool(ExecutionRouter router) {
        this.router = router;
    }

    @Override
    public String name() {
        return "edit";
    }

    @Override
    public String label() {
        return "Edit";
    }

    @Override
    public String description() {
        return "Edit file contents using text replacement. Protected paths use sandbox. "
                + "Use _executionMode parameter to force specific mode.";
    }

    @Override
    public JsonNode parameters() {
        ObjectNode props = mapper.createObjectNode();
        props.set("path", mapper.createObjectNode().put("type", "string").put("description", "File path to edit"));
        props.set(
                "oldText",
                mapper.createObjectNode()
                        .put("type", "string")
                        .put("description", "Text to find and replace (single replacement mode)"));
        props.set(
                "newText",
                mapper.createObjectNode()
                        .put("type", "string")
                        .put("description", "Replacement text (single replacement mode)"));
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
                .set("required", mapper.createArrayNode().add("path"));
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
