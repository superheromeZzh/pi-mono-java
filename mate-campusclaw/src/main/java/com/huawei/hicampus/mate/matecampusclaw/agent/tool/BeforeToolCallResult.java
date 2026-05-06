/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.hicampus.mate.matecampusclaw.agent.tool;

/**
 * Result returned from the before-tool-call hook.
 *
 * @version [br_eCampusCore 25.1.0_Next, 2026/05/06]
 * @since [br_eCampusCore 25.1.0_Next]
 */
public record BeforeToolCallResult(boolean block, String reason) {

    @SuppressWarnings("checkstyle:java_doc_format_missing")
    public static BeforeToolCallResult allow() {
        return new BeforeToolCallResult(false, null);
    }

    @SuppressWarnings("checkstyle:java_doc_format_missing")
    public static BeforeToolCallResult block(String reason) {
        return new BeforeToolCallResult(true, reason);
    }
}
