/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.campusclaw.agent.tool;

import java.util.List;

import com.campusclaw.ai.types.ContentBlock;

/**
 * Optional overrides returned from the after-tool-call hook.
 *
 * @version [br_eCampusCore 25.1.0_Next, 2026/05/06]
 * @since [br_eCampusCore 25.1.0_Next]
 */
public record AfterToolCallResult(List<ContentBlock> content, Object details, Boolean isError) {

    @SuppressWarnings("checkstyle:java_doc_format_missing")
    public static AfterToolCallResult noOverride() {
        return new AfterToolCallResult(null, null, null);
    }
}
