/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.campusclaw.assistant.channel;

import java.util.Collection;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Service;

/**
 * Thread-safe in-memory registry of active {@link Channel} instances keyed by name.
 * Also tracks the most recently registered channel so callers without a specific target
 * can fall back to the latest connection.
 *
 * @version [br_eCampusCore 25.1.0_Next, 2026/05/13]
 * @since [br_eCampusCore 25.1.0_Next]
 */
@Service
public class ChannelRegistry {

    private final ConcurrentHashMap<String, Channel> channels = new ConcurrentHashMap<>();
    private volatile Channel latestChannel;

    public void register(Channel channel) {
        channels.put(channel.getName(), channel);
        latestChannel = channel;
    }

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
