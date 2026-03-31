package com.campusclaw.ai.types;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * An image content block carrying base64-encoded image data.
 *
 * @param data     base64-encoded image data
 * @param mimeType the MIME type of the image (e.g. "image/png")
 */
public record ImageContent(
    @JsonProperty("data") String data,
    @JsonProperty("mimeType") String mimeType
) implements ContentBlock {
}
