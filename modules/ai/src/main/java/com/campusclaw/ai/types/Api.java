package com.campusclaw.ai.types;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * API protocol used to communicate with an LLM provider.
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

    @JsonValue
    public String value() {
        return value;
    }

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
