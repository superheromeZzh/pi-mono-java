package com.huawei.hicampus.campusclaw.codingagent.tool.read;

import com.huawei.hicampus.campusclaw.codingagent.util.TruncationUtils;

/**
 * Structured details returned alongside the Read tool result.
 *
 * @param truncation truncation metadata, if output was truncated
 */
public record ReadToolDetails(
        TruncationUtils.TruncationResult truncation
) {
}
