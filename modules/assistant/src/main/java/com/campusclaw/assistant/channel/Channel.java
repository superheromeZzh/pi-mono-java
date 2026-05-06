/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.campusclaw.assistant.channel;

@SuppressWarnings("checkstyle:top_class_comment")
public interface Channel {

    String getName();

    void sendMessage(String message);
}
