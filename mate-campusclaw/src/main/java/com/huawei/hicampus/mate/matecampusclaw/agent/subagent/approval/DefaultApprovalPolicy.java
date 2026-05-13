/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.hicampus.mate.matecampusclaw.agent.subagent.approval;

import org.springframework.stereotype.Component;

/**
 * Default approval policy. Read-only operations auto-allow; everything else escalates to the
 * parent agent. {@code UNKNOWN} risk also escalates, on the principle that an unrecognised tool
 * name should never silently auto-allow.
 *
 * @version [br_eCampusCore 25.1.0_Next, 2026/05/12]
 * @since [br_eCampusCore 25.1.0_Next]
 */
@Component
public class DefaultApprovalPolicy implements ApprovalPolicy {

    @Override
    public ApprovalDecision decide(ApprovalClassifier.Risk risk, String toolName) {
        if (risk == null) {
            return ApprovalDecision.ASK_PARENT;
        }
        return switch (risk) {
            case READ_ONLY -> ApprovalDecision.AUTO_ALLOW;
            case FILE_WRITE, EXEC, NETWORK, UNKNOWN -> ApprovalDecision.ASK_PARENT;
        };
    }
}
