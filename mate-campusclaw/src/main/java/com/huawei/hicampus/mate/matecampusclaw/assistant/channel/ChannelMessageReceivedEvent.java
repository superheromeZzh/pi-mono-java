/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.hicampus.mate.matecampusclaw.assistant.channel;

import java.time.Instant;

@SuppressWarnings("checkstyle:top_class_comment")
public record ChannelMessageReceivedEvent(String channelName, String message, Instant timestamp) {
    public ChannelMessageReceivedEvent(String channelName, String message) {
        this(channelName, message, Instant.now());
    }
}
