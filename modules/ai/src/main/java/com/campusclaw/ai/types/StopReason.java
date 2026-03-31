package com.campusclaw.ai.types;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * Reason why the LLM stopped generating output.
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

    @JsonValue
    public String value() {
        return value;
    }

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
