/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.hicampus.mate.matecampusclaw.assistant.channel;

public interface Channel {

    String getName();

    void sendMessage(String message);
}
