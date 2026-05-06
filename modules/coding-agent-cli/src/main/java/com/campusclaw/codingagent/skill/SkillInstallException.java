/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.campusclaw.codingagent.skill;

/**
 * Thrown when a skill installation, removal, or linking operation fails.
 */
public class SkillInstallException extends Exception {

    public SkillInstallException(String message) {
        super(message);
    }

    public SkillInstallException(String message, Throwable cause) {
        super(message, cause);
    }
}
