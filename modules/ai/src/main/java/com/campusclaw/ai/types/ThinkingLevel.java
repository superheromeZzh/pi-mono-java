/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.campusclaw.ai.types;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * Level of extended thinking / reasoning to request from the model.
 *
 * @version [br_eCampusCore 25.1.0_Next, 2026/05/06]
 * @since [br_eCampusCore 25.1.0_Next]
 */
public enum ThinkingLevel {
    OFF("off"),
    MINIMAL("minimal"),
    LOW("low"),
    MEDIUM("medium"),
    HIGH("high"),
    XHIGH("xhigh");

    private final String value;

    ThinkingLevel(String value) {
        this.value = value;
    }

    @SuppressWarnings("checkstyle:java_doc_format_missing")
    @JsonValue
    public String value() {
        return value;
    }

    @SuppressWarnings("checkstyle:java_doc_format_missing")
    @JsonCreator
    public static ThinkingLevel fromValue(String value) {
        for (var level : values()) {
            if (level.value.equals(value)) {
                return level;
            }
        }
        throw new IllegalArgumentException("Unknown ThinkingLevel: " + value);
    }
}
