/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.campusclaw.assistant.channel;

import java.util.Collection;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Service;

@SuppressWarnings("checkstyle:top_class_comment")
@Service
public class ChannelRegistry {

    private final ConcurrentHashMap<String, Channel> channels = new ConcurrentHashMap<>();
    private volatile Channel latestChannel;

    @SuppressWarnings("checkstyle:java_doc_format_missing")
    public void register(Channel channel) {
        channels.put(channel.getName(), channel);
        latestChannel = channel;
    }

    @SuppressWarnings("checkstyle:java_doc_format_missing")
    public Channel get(String name) {
        return channels.get(name);
    }

    public Channel getLatest() {
        return latestChannel;
    }

    public Collection<Channel> getAll() {
        return channels.values();
    }
}
