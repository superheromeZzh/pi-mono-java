/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.campusclaw.assistant.channel;

import java.time.Instant;

@SuppressWarnings("checkstyle:top_class_comment")
public record ChannelMessageReceivedEvent(String channelName, String message, Instant timestamp) {
    @SuppressWarnings("checkstyle:java_doc_format_missing")
    public ChannelMessageReceivedEvent(String channelName, String message) {
        this(channelName, message, Instant.now());
    }
}
