/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.campusclaw.agent.subagent.approval;

import java.util.List;
import java.util.Map;

/**
 * Permission request handed to a {@link ParentPermissionResolver}.
 *
 * @param sessionKey originating sub-agent session key (for telemetry/audit)
 * @param backendId backend that surfaced the request
 * @param toolName name of the tool the sub-agent wants to invoke
 * @param risk classifier verdict on the request
 * @param params best-effort serialised params (may be empty)
 * @param options option ids advertised by the backend, in original order; the resolver picks one
 *
 * @version [br_eCampusCore 25.1.0_Next, 2026/05/12]
 * @since [br_eCampusCore 25.1.0_Next]
 */
public record ParentPermissionRequest(
        String sessionKey,
        String backendId,
        String toolName,
        ApprovalClassifier.Risk risk,
        Map<String, Object> params,
        List<Option> options) {

    public ParentPermissionRequest {
        params = params == null ? Map.of() : Map.copyOf(params);
        options = options == null ? List.of() : List.copyOf(options);
    }

    /**
     * Option exposed by the backend (e.g. {"optionId":"allow_once","kind":"allow_once","name":"Allow once"}).
     */
    public record Option(String optionId, String kind, String name) {}
}
