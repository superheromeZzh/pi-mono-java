/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.campusclaw.codingagent.skill;

/**
 * Thrown when a skill installation, removal, or linking operation fails.
 *
 * @version [br_eCampusCore 25.1.0_Next, 2026/05/06]
 * @since [br_eCampusCore 25.1.0_Next]
 */
public class SkillInstallException extends Exception {

    @SuppressWarnings("checkstyle:java_doc_format_missing")
    public SkillInstallException(String message) {
        super(message);
    }

    @SuppressWarnings("checkstyle:java_doc_format_missing")
    public SkillInstallException(String message, Throwable cause) {
        super(message, cause);
    }
}
