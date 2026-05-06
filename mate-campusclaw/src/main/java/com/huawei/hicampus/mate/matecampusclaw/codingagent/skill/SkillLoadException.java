/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.hicampus.mate.matecampusclaw.codingagent.skill;

/**
 * Thrown when a skill file cannot be loaded or fails validation.
 *
 * @version [br_eCampusCore 25.1.0_Next, 2026/05/06]
 * @since [br_eCampusCore 25.1.0_Next]
 */
public class SkillLoadException extends RuntimeException {

    @SuppressWarnings("checkstyle:java_doc_format_missing")
    public SkillLoadException(String message) {
        super(message);
    }

    @SuppressWarnings("checkstyle:java_doc_format_missing")
    public SkillLoadException(String message, Throwable cause) {
        super(message, cause);
    }
}
