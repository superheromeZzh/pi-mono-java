package com.campusclaw.codingagent.skill;

/**
 * Thrown when a skill file cannot be loaded or fails validation.
 */
public class SkillLoadException extends RuntimeException {

    public SkillLoadException(String message) {
        super(message);
    }

    public SkillLoadException(String message, Throwable cause) {
        super(message, cause);
    }
}
