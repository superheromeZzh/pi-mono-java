package com.campusclaw.assistant.channel;

public interface Channel {

    String getName();

    void sendMessage(String message);
}
