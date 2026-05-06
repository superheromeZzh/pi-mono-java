/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.campusclaw.codingagent.compaction;

@SuppressWarnings("checkstyle:top_class_comment")
public record CompactionConfig(boolean enabled, int reserveTokens, int keepRecentTokens) {
    @SuppressWarnings("checkstyle:java_doc_format_missing")
    public static CompactionConfig defaults() {
        return new CompactionConfig(true, 16384, 20000);
    }
}
