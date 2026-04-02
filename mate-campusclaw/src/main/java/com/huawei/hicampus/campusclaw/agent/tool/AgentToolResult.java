package com.huawei.hicampus.campusclaw.agent.tool;

import java.util.List;

import com.huawei.hicampus.campusclaw.ai.types.ContentBlock;

/**
 * Final or partial result emitted by an {@link AgentTool}.
 *
 * @param content user-visible content returned by the tool
 * @param details optional implementation-specific structured details
 */
public record AgentToolResult(
    List<ContentBlock> content,
    Object details
) {
}
