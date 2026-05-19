/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.hicampus.mate.matecampusclaw.agent.subagent.approval;

import java.time.Duration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Default {@link ParentPermissionResolver}: log the request, return {@code CANCELLED}.
 *
 * <p>Until an interactive parent-agent round-trip exists, this is the safest behaviour — denying
 * defaults beat silently auto-allowing risky operations. Interactive resolvers (TUI prompt, RPC
 * round-trip) can replace this bean by declaring a higher-precedence {@code @Component}.
 *
 * @version [br_eCampusCore 25.1.0_Next, 2026/05/12]
 * @since [br_eCampusCore 25.1.0_Next]
 */
@Component
public class TimeoutDeniedResolver implements ParentPermissionResolver {

    private static final Logger log = LoggerFactory.getLogger(TimeoutDeniedResolver.class);

    @Override
    public ParentPermissionDecision resolve(ParentPermissionRequest request, Duration timeout) {
        log.info(
                "denying sub-agent permission (no interactive resolver): session={} backend={} tool={} risk={}",
                request.sessionKey(),
                request.backendId(),
                request.toolName(),
                request.risk());
        return ParentPermissionDecision.cancelled();
    }
}
