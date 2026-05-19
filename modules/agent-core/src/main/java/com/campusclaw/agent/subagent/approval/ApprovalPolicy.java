/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.campusclaw.agent.subagent.approval;

/**
 * Pluggable approval strategy applied to sub-agent permission requests.
 *
 * @version [br_eCampusCore 25.1.0_Next, 2026/05/12]
 * @since [br_eCampusCore 25.1.0_Next]
 */
public interface ApprovalPolicy {

    ApprovalDecision decide(ApprovalClassifier.Risk risk, String toolName);
}
