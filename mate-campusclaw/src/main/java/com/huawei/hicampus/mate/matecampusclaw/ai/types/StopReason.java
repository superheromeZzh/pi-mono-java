/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.hicampus.mate.matecampusclaw.ai.types;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * Reason why the LLM stopped generating output.
 *
 * @version [br_eCampusCore 25.1.0_Next, 2026/05/06]
 * @since [br_eCampusCore 25.1.0_Next]
 */
public enum StopReason {
    STOP("stop"),
    LENGTH("length"),
    TOOL_USE("toolUse"),
    ERROR("error"),
    ABORTED("aborted");

    private final String value;

    StopReason(String value) {
        this.value = value;
    }

    @SuppressWarnings("checkstyle:java_doc_format_missing")
    @JsonValue
    public String value() {
        return value;
    }

    @SuppressWarnings("checkstyle:java_doc_format_missing")
    @JsonCreator
    public static StopReason fromValue(String value) {
        for (var reason : values()) {
            if (reason.value.equals(value)) {
                return reason;
            }
        }
        throw new IllegalArgumentException("Unknown StopReason: " + value);
    }
}
