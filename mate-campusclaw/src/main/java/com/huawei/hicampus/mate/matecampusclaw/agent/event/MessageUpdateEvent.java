/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.hicampus.mate.matecampusclaw.agent.event;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.huawei.hicampus.mate.matecampusclaw.ai.stream.AssistantMessageEvent;
import com.huawei.hicampus.mate.matecampusclaw.ai.types.Message;

/**
 * Emitted for incremental assistant message updates.
 */
public record MessageUpdateEvent(
        @JsonProperty("message") Message message,
        @JsonProperty("assistantMessageEvent") AssistantMessageEvent assistantMessageEvent)
        implements AgentEvent {}
