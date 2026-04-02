package com.huawei.hicampus.mate.matecampusclaw.codingagent.compaction;

import java.util.List;
import java.util.Set;

import com.huawei.hicampus.mate.matecampusclaw.ai.types.Message;

public record CompactionResult(
    String summary,
    List<Message> retainedMessages,
    Set<String> filesRead,
    Set<String> filesModified
) {}
