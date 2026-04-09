package com.campusclaw.assistant.channel;

import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.concurrent.ConcurrentHashMap;

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
