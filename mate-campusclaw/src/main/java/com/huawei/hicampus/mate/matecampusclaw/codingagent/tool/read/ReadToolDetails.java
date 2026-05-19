/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.hicampus.mate.matecampusclaw.codingagent.tool.read;

import com.huawei.hicampus.mate.matecampusclaw.codingagent.util.TruncationUtils;

/**
 * Structured details returned alongside the Read tool result.
 *
 * @param truncation truncation metadata, if output was truncated
 *
 * @version [br_eCampusCore 25.1.0_Next, 2026/05/06]
 * @since [br_eCampusCore 25.1.0_Next]
 */
public record ReadToolDetails(TruncationUtils.TruncationResult truncation) {}
