/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.campusclaw.agent.subagent.approval;

/**
 * Resolution returned by a {@link ParentPermissionResolver}.
 *
 * @param outcome decision verdict
 * @param optionId option id chosen from {@link ParentPermissionRequest#options()}; required for
 *     {@link Outcome#SELECTED}, ignored otherwise
 *
 * @version [br_eCampusCore 25.1.0_Next, 2026/05/12]
 * @since [br_eCampusCore 25.1.0_Next]
 */
public record ParentPermissionDecision(Outcome outcome, String optionId) {

    /**
     * High-level outcome of a permission round-trip.
     */
    public enum Outcome {
        SELECTED,
        CANCELLED
    }

    public static ParentPermissionDecision selected(String optionId) {
        return new ParentPermissionDecision(Outcome.SELECTED, optionId);
    }

    public static ParentPermissionDecision cancelled() {
        return new ParentPermissionDecision(Outcome.CANCELLED, null);
    }
}
