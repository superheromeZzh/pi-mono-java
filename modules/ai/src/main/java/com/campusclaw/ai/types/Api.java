/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.campusclaw.ai.types;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * API protocol used to communicate with an LLM provider.
 *
 * @version [br_eCampusCore 25.1.0_Next, 2026/05/06]
 * @since [br_eCampusCore 25.1.0_Next]
 */
public enum Api {
    ANTHROPIC_MESSAGES("anthropic-messages"),
    OPENAI_RESPONSES("openai-responses"),
    OPENAI_COMPLETIONS("openai-completions"),
    BEDROCK_CONVERSE_STREAM("bedrock-converse-stream"),
    GOOGLE_GENERATIVE_AI("google-generative-ai"),
    GOOGLE_VERTEX("google-vertex"),
    MISTRAL_CONVERSATIONS("mistral-conversations"),
    AZURE_OPENAI_RESPONSES("azure-openai-responses"),
    OPENAI_CODEX_RESPONSES("openai-codex-responses"),
    GOOGLE_GEMINI_CLI("google-gemini-cli");

    private final String value;

    Api(String value) {
        this.value = value;
    }

    @SuppressWarnings("checkstyle:java_doc_format_missing")
    @JsonValue
    public String value() {
        return value;
    }

    @SuppressWarnings("checkstyle:java_doc_format_missing")
    @JsonCreator
    public static Api fromValue(String value) {
        for (var a : values()) {
            if (a.value.equals(value)) {
                return a;
            }
        }
        throw new IllegalArgumentException("Unknown Api: " + value);
    }
}
