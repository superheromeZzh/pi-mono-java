package com.huawei.hicampus.mate.matecampusclaw.ai.types;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * Transport protocol for streaming LLM responses.
 */
public enum Transport {

    SSE("sse"),
    WEBSOCKET("websocket"),
    AUTO("auto");

    private final String value;

    Transport(String value) {
        this.value = value;
    }

    @JsonValue
    public String value() {
        return value;
    }

    @JsonCreator
    public static Transport fromValue(String value) {
        for (var t : values()) {
            if (t.value.equals(value)) {
                return t;
            }
        }
        throw new IllegalArgumentException("Unknown Transport: " + value);
    }
}
