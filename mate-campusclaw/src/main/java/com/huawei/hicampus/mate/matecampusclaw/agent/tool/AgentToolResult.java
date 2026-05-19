/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.hicampus.mate.matecampusclaw.agent.tool;

import java.util.List;

import com.huawei.hicampus.mate.matecampusclaw.ai.types.ContentBlock;

/**
 * Final or partial result emitted by an {@link AgentTool}.
 *
 * @param content user-visible content returned by the tool
 * @param details optional implementation-specific structured details
 *
 * @version [br_eCampusCore 25.1.0_Next, 2026/05/06]
 * @since [br_eCampusCore 25.1.0_Next]
 */
public record AgentToolResult(List<ContentBlock> content, Object details) {}
