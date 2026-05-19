/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.campusclaw.assistant;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Spring Boot configuration properties for the assistant module, bound to the {@code pi.assistant.*}
 * prefix. Currently exposes nested channel settings for future expansion.
 *
 * @version [br_eCampusCore 25.1.0_Next, 2026/05/13]
 * @since [br_eCampusCore 25.1.0_Next]
 */
@ConfigurationProperties(prefix = "pi.assistant")
public class AssistantProperties {

    private Channel channel = new Channel();

    public Channel getChannel() {
        return channel;
    }

    public void setChannel(Channel channel) {
        this.channel = channel;
    }

    @SuppressWarnings("checkstyle:top_class_comment")
    public static class Channel {
        // Placeholder for future channel configuration extensions
    }
}
