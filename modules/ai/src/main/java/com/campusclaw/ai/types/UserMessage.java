/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.campusclaw.ai.types;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * A message from the user.
 *
 * @param content   the content blocks of the message
 * @param timestamp Unix timestamp in milliseconds
 *
 * @version [br_eCampusCore 25.1.0_Next, 2026/05/06]
 * @since [br_eCampusCore 25.1.0_Next]
 */
public record UserMessage(
        @JsonProperty("content") List<ContentBlock> content, @JsonProperty("timestamp") long timestamp)
        implements Message {

    /**
     * Convenience constructor that wraps a plain text string into a single {@link TextContent} block.
     *
     * @param text raw user text to wrap
     * @param timestamp Unix timestamp in milliseconds when the message was authored
     */
    public UserMessage(String text, long timestamp) {
        this(List.of(new TextContent(text, null)), timestamp);
    }
}
