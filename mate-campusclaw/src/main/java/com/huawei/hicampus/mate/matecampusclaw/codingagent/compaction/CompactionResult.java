/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.hicampus.mate.matecampusclaw.codingagent.compaction;

import java.util.List;
import java.util.Set;

import com.huawei.hicampus.mate.matecampusclaw.ai.types.Message;

/**
 * Outcome of a single compaction pass: the generated summary text, the tail of messages kept
 * verbatim, plus the file paths observed as read or modified during the compacted span (used
 * downstream to re-attach context where relevant).
 *
 * @version [br_eCampusCore 25.1.0_Next, 2026/05/13]
 * @since [br_eCampusCore 25.1.0_Next]
 */
public record CompactionResult(
        String summary, List<Message> retainedMessages, Set<String> filesRead, Set<String> filesModified) {}
