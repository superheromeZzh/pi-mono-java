package com.huawei.hicampus.mate.matecampusclaw.codingagent.tool.execution;

import com.huawei.hicampus.mate.matecampusclaw.agent.tool.AgentToolResult;
import com.huawei.hicampus.mate.matecampusclaw.agent.tool.CancellationToken;
import com.huawei.hicampus.mate.matecampusclaw.agent.tool.AgentToolUpdateCallback;

import java.util.Map;

/**
 * 工具执行策略接口
 */
public interface ToolExecutionStrategy {

    /**
     * 执行工具调用
     */
    AgentToolResult execute(
        String toolName,
        Map<String, Object> params,
        CancellationToken signal,
        AgentToolUpdateCallback onUpdate
    ) throws Exception;

    /**
     * 获取策略名称
     */
    String getName();

    /**
     * 检查是否支持该工具
     */
    boolean supports(String toolName);
}
