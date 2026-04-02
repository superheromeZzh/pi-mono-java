package com.huawei.hicampus.mate.matecampusclaw.codingagent.skill;

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
