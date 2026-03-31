package com.campusclaw.ai.types;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * Cache retention policy for prompt caching.
 */
public enum CacheRetention {

    NONE("none"),
    SHORT("short"),
    LONG("long");

    private final String value;

    CacheRetention(String value) {
        this.value = value;
    }

    @JsonValue
    public String value() {
        return value;
    }

    @JsonCreator
    public static CacheRetention fromValue(String value) {
        for (var cr : values()) {
            if (cr.value.equals(value)) {
                return cr;
            }
        }
        throw new IllegalArgumentException("Unknown CacheRetention: " + value);
    }
}
