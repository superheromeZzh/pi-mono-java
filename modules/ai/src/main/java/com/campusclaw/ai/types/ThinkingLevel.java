package com.campusclaw.ai.types;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * Level of extended thinking / reasoning to request from the model.
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

    @JsonValue
    public String value() {
        return value;
    }

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
