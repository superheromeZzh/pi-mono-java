/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.campusclaw.codingagent.tool.execution;

import java.util.Map;

import com.campusclaw.agent.tool.AgentToolResult;
import com.campusclaw.agent.tool.AgentToolUpdateCallback;
import com.campusclaw.agent.tool.CancellationToken;

/**
 * 工具执行策略接口
 *
 * @version [br_eCampusCore 25.1.0_Next, 2026/05/06]
 * @since [br_eCampusCore 25.1.0_Next]
 */
public interface ToolExecutionStrategy {

    /**
     * 执行工具调用.
     *
     * @param toolName 工具名
     * @param params 工具参数
     * @param signal 取消信号
     * @param onUpdate 进度回调
     * @return 工具执行结果
     * @throws Exception 工具执行抛出的任意异常
     */
    AgentToolResult execute(
            String toolName, Map<String, Object> params, CancellationToken signal, AgentToolUpdateCallback onUpdate)
            throws Exception;

    /**
     * 获取策略名称
     *
     * @return the result
     */
    String getName();

    /**
     * 检查是否支持该工具
     *
     * @param toolName the toolName
     * @return the result
     */
    boolean supports(String toolName);
}
