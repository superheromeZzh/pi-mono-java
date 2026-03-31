package com.campusclaw.assistant.channel;

import java.time.Instant;

public record ChannelMessageReceivedEvent(
    String channelName,
    String message,
    Instant timestamp
) {
    public ChannelMessageReceivedEvent(String channelName, String message) {
        this(channelName, message, Instant.now());
    }
}
