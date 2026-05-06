/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.hicampus.mate.matecampusclaw.codingagent.tool.edit;

/**
 * Structured details returned alongside the Edit tool result.
 *
 * @param diff             unified diff of the changes made
 * @param firstChangedLine 1-indexed line number of the first change, or null
 */
public record EditToolDetails(
        String diff,
        Integer firstChangedLine
) {
}
