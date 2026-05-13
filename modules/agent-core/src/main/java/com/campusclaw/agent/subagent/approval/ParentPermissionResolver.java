/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.campusclaw.agent.subagent.approval;

import java.time.Duration;

/**
 * Strategy invoked when {@link DefaultApprovalPolicy} returns {@link ApprovalDecision#ASK_PARENT}.
 *
 * <p>Implementations may prompt the parent agent or a human via TUI/RPC/HTTP. The call blocks the
 * backend reader thread that received the request, so it must respect {@code timeout} and never
 * wait indefinitely.
 *
 * @version [br_eCampusCore 25.1.0_Next, 2026/05/12]
 * @since [br_eCampusCore 25.1.0_Next]
 */
public interface ParentPermissionResolver {

    ParentPermissionDecision resolve(ParentPermissionRequest request, Duration timeout);
}
