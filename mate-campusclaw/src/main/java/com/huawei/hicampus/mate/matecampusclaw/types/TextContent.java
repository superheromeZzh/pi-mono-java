package com.huawei.hicampus.mate.matecampusclaw.ai.types;

import com.fasterxml.jackson.annotation.JsonProperty;

import jakarta.annotation.Nullable;

/**
 * A text content block in an assistant message.
 *
 * @param text          the text content
 * @param textSignature optional signature metadata (provider-specific)
 */
public record TextContent(
    @JsonProperty("text") String text,
    @JsonProperty("textSignature") @Nullable String textSignature
) implements ContentBlock {

    public TextContent(String text) {
        this(text, null);
    }
}
