package com.huawei.hicampus.campusclaw.codingagent.compaction;

import java.util.List;
import java.util.Set;

import com.huawei.hicampus.campusclaw.ai.types.Message;

public record CompactionResult(
    String summary,
    List<Message> retainedMessages,
    Set<String> filesRead,
    Set<String> filesModified
) {}
