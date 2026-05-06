/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.hicampus.mate.matecampusclaw.assistant.channel;

@SuppressWarnings("checkstyle:top_class_comment")
public interface Channel {

    @SuppressWarnings("checkstyle:java_doc_format_missing")
    String getName();

    @SuppressWarnings("checkstyle:java_doc_format_missing")
    void sendMessage(String message);
}
