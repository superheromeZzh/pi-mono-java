package com.huawei.hicampus.mate.matecampusclaw.codingagent.tool.bash;

import com.huawei.hicampus.mate.matecampusclaw.codingagent.util.TruncationUtils;

/**
 * Structured details returned alongside the Bash tool result.
 *
 * @param truncation     truncation metadata, if output was truncated
 * @param fullOutputPath path to the full untruncated output file, if truncation occurred
 */
public record BashToolDetails(
        TruncationUtils.TruncationResult truncation,
        String fullOutputPath
) {
}
