package com.mariozechner.pi.codingagent.compaction;

import com.mariozechner.pi.ai.types.Message;
import java.util.List;
import java.util.Set;

public record CompactionResult(
    String summary,
    List<Message> retainedMessages,
    Set<String> filesRead,
    Set<String> filesModified
) {}
