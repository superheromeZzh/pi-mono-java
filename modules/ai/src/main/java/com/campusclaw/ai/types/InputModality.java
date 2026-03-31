package com.campusclaw.ai.types;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * Input modality supported by a model.
 */
public enum InputModality {

    TEXT("text"),
    IMAGE("image");

    private final String value;

    InputModality(String value) {
        this.value = value;
    }

    @JsonValue
    public String value() {
        return value;
    }

    @JsonCreator
    public static InputModality fromValue(String value) {
        for (var m : values()) {
            if (m.value.equals(value)) {
                return m;
            }
        }
        throw new IllegalArgumentException("Unknown InputModality: " + value);
    }
}
